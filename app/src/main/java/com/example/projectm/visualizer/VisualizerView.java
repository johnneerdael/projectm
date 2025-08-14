package com.example.projectm.visualizer;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Choreographer;
import android.view.SurfaceHolder;

public class VisualizerView extends GLSurfaceView {

    private static final String TAG = "VisualizerView";
    private VisualizerRenderer renderer;
    private Choreographer.FrameCallback frameCallback;
    private boolean useChoreographer = false;
    private int targetFPS = 30;

    public VisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Log.d(TAG, "VisualizerView constructor");

        try {
            // Configure EGL
            // Try to use RGB888 for better visual quality, but with a 16-bit depth buffer for better performance
            setEGLConfigChooser(8, 8, 8, 0, 16, 0);
            
            // Create an OpenGL ES 3.0 context for better performance
            setEGLContextClientVersion(3);
            
            // Enable hardware acceleration
            setPreserveEGLContextOnPause(true);
            
            // Set target frame rate for Google TV (API 30+)
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                try {
                    SurfaceHolder holder = getHolder();
                    if (holder != null && holder.getSurface() != null) {
                        // Target 30 FPS for smooth music visualization on TV hardware
                        holder.getSurface().setFrameRate(30.0f, android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
                        Log.i(TAG, "Set target frame rate to 30 FPS for Android TV");
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Could not set frame rate hint: " + e.getMessage());
                }
            }
            
            Log.d(TAG, "Set OpenGL ES 3.0 context with hardware acceleration");
    
            // Note: renderer will be set by MainActivity
            // renderer = new VisualizerRenderer();
    
            // DON'T set render mode yet - we'll do it after the renderer is set
            // to avoid NullPointerException
            Log.d(TAG, "Will set render mode to RENDERMODE_CONTINUOUSLY after renderer is set");
            
            // Setup Choreographer-based frame pacing for smoother rendering
            setupFramePacing();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing VisualizerView", e);
        }
    }
    
    private void setupFramePacing() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                frameCallback = new Choreographer.FrameCallback() {
                    private long lastFrameTime = 0;
                    private final long targetFrameTime = 1000000000L / targetFPS; // nanoseconds per frame

                    @Override
                    public void doFrame(long frameTimeNanos) {
                        if (useChoreographer) {
                            long timeSinceLastFrame = frameTimeNanos - lastFrameTime;

                            if (timeSinceLastFrame >= targetFrameTime || lastFrameTime == 0) {
                                requestRender();
                                lastFrameTime = frameTimeNanos;
                            }
                            Choreographer.getInstance().postFrameCallback(this);
                        }
                    }
                };
                Log.d(TAG, "Choreographer frame pacing initialized for " + targetFPS + " FPS");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing frame pacing", e);
        }
    }

    @Override
    public void setRenderer(Renderer renderer) {
        Log.d(TAG, "setRenderer called with renderer: " + (renderer != null ? renderer.getClass().getSimpleName() : "null"));
        try {
            // First set the renderer using the parent method
            super.setRenderer(renderer);
            
            if (renderer instanceof VisualizerRenderer) {
                this.renderer = (VisualizerRenderer) renderer;
                Log.d(TAG, "VisualizerRenderer set successfully");
                
                // Now it's safe to set the render mode because the GLThread exists
                setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
                Log.i(TAG, "Set RENDERMODE_CONTINUOUSLY for music visualization");
            } else {
                Log.w(TAG, "Renderer is not an instance of VisualizerRenderer");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting renderer", e);
        }
    }

    public VisualizerRenderer getRenderer() {
        return renderer;
    }
    
    @Override
    public void onResume() {
        Log.d(TAG, "onResume called");
        try {
            super.onResume();
            
            // Re-enable Choreographer pacing for performance if available
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                enableChoreographerPacing(true);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in VisualizerView.onResume: " + e.getMessage());
        }
    }
    
    @Override
    public void onPause() {
        Log.d(TAG, "onPause called");
        try {
            useChoreographer = false;
            super.onPause();
        } catch (Exception e) {
            Log.e(TAG, "Error in VisualizerView.onPause: " + e.getMessage());
        }
    }
    
    public void setTargetFPS(int fps) {
        if (fps >= 15 && fps <= 120) {
            this.targetFPS = fps;
            Log.i(TAG, "Target FPS updated to: " + fps);
            
            // Update native layer
            try {
                ProjectMJNI.setTargetFPS(fps);
            } catch (Exception e) {
                Log.w(TAG, "Failed to set native target FPS", e);
            }
            
            // Update surface frame rate hint if available
            if (Build.VERSION.SDK_INT >= 30 && getHolder().getSurface() != null) {
                try {
                    getHolder().getSurface().setFrameRate((float) fps, android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
                    Log.d(TAG, "Surface frame rate updated to: " + fps);
                } catch (Exception e) {
                    Log.w(TAG, "Could not update surface frame rate", e);
                }
            }
        }
    }
    
    public void enableChoreographerPacing(boolean enable) {
        this.useChoreographer = enable;
        
        if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
            Choreographer.getInstance().postFrameCallback(frameCallback);
            Log.i(TAG, "Choreographer frame pacing enabled");
        } else {
            setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
            Log.i(TAG, "Continuous rendering mode enabled");
        }
    }
    
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated called");
        super.surfaceCreated(holder);
    }
    
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "surfaceDestroyed called");
        super.surfaceDestroyed(holder);
    }
    
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "surfaceChanged called: " + width + "x" + height + ", format=" + format);
        super.surfaceChanged(holder, format, width, height);
    }
    
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        Log.d(TAG, "onWindowFocusChanged: " + hasFocus);
        super.onWindowFocusChanged(hasFocus);
        
        // If we gain focus, request a render to ensure we're displaying
        if (hasFocus) {
            Log.d(TAG, "Window gained focus, requesting render");
            requestRender();
        }
    }
    
    @Override
    public void requestRender() {
        try {
            // Check if we have a valid renderer before requesting a render
            if (renderer != null) {
                // Force a refresh of the entire surface
                queueEvent(() -> {
                    // This will run on the GL thread
                    android.opengl.GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                    android.opengl.GLES30.glClear(android.opengl.GLES30.GL_COLOR_BUFFER_BIT | 
                                                  android.opengl.GLES30.GL_DEPTH_BUFFER_BIT);
                });
                
                // Request the actual render
                super.requestRender();
            } else {
                Log.d(TAG, "Skipping requestRender - renderer not set yet");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in requestRender", e);
        }
    }
}
