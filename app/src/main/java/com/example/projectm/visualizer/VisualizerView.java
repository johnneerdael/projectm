package com.example.projectm.visualizer;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;

public class VisualizerView extends GLSurfaceView {

    private static final String TAG = "VisualizerView";
    private VisualizerRenderer renderer;

    public VisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Log.d(TAG, "VisualizerView constructor");

        try {
            // Configure EGL
            // Try to use RGB888 for better visual quality, but with a 16-bit depth buffer for better performance
            setEGLConfigChooser(8, 8, 8, 0, 16, 0);
            
            // Create an OpenGL ES 2.0 context.
            setEGLContextClientVersion(2);
            
            // Enable hardware acceleration
            setPreserveEGLContextOnPause(true);
            
            Log.d(TAG, "Set OpenGL ES 2.0 context with hardware acceleration");
    
            // Note: renderer will be set by MainActivity
            // renderer = new VisualizerRenderer();
    
            // DON'T set render mode yet - we'll do it after the renderer is set
            // to avoid NullPointerException
            Log.d(TAG, "Will set render mode to RENDERMODE_CONTINUOUSLY after renderer is set");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing VisualizerView", e);
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
        } catch (Exception e) {
            Log.e(TAG, "Error in VisualizerView.onResume: " + e.getMessage());
        }
    }
    
    @Override
    public void onPause() {
        Log.d(TAG, "onPause called");
        try {
            super.onPause();
        } catch (Exception e) {
            Log.e(TAG, "Error in VisualizerView.onPause: " + e.getMessage());
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
                super.requestRender();
            } else {
                Log.d(TAG, "Skipping requestRender - renderer not set yet");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in requestRender", e);
        }
    }
}
