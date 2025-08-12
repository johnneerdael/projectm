package com.example.projectm.visualizer;

import android.opengl.GLSurfaceView;
import android.util.Log;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class VisualizerRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = "VisualizerRenderer";

    private boolean mProjectMInitialized = false;
    private String mAssetPath;
    private int mWidth = 0;
    private int mHeight = 0;
    private long lastPresetChange = 0;
    private long PRESET_CHANGE_INTERVAL = 30000; // 30 seconds default
    private boolean autoChangeEnabled = true; // Auto change presets by default

    public VisualizerRenderer(String assetPath) {
        Log.d(TAG, "VisualizerRenderer created with asset path: " + assetPath);
        mAssetPath = assetPath;
        
        // Use default width and height if not set yet
        if (mWidth <= 0 || mHeight <= 0) {
            mWidth = 1280;  // Default width for most Android TV devices
            mHeight = 720;  // Default height for most Android TV devices
            Log.d(TAG, "Using default dimensions: " + mWidth + "x" + mHeight);
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.d(TAG, "onSurfaceCreated called, GL version: " + gl.glGetString(GL10.GL_VERSION));
        Log.d(TAG, "GL Vendor: " + gl.glGetString(GL10.GL_VENDOR));
        Log.d(TAG, "GL Renderer: " + gl.glGetString(GL10.GL_RENDERER));
        Log.d(TAG, "Current dimensions: " + mWidth + "x" + mHeight);
        Log.d(TAG, "Asset path: " + (mAssetPath != null ? mAssetPath : "null"));

        try {
            if (mWidth > 0 && mHeight > 0 && mAssetPath != null) {
                // Check if asset path exists
                java.io.File assetDir = new java.io.File(mAssetPath);
                Log.d(TAG, "Asset directory exists: " + assetDir.exists() + ", isDirectory: " + assetDir.isDirectory());
                if (assetDir.exists() && assetDir.isDirectory()) {
                    Log.d(TAG, "Asset directory contains: " + assetDir.list().length + " files");
                    for (String file : assetDir.list()) {
                        Log.d(TAG, "Asset file: " + file);
                    }
                }

                Log.d(TAG, "Initializing projectM with dimensions: " + mWidth + "x" + mHeight + ", path: " + mAssetPath);
                ProjectMJNI.onSurfaceCreated(mWidth, mHeight, mAssetPath);
                mProjectMInitialized = true;
                lastPresetChange = System.currentTimeMillis();
                Log.i(TAG, "ProjectM initialized successfully");
                
                // Verify initialization by getting current preset name
                String currentPreset = ProjectMJNI.getCurrentPresetName();
                Log.i(TAG, "Initial preset: " + currentPreset);
            } else {
                Log.w(TAG, "Cannot initialize projectM: width=" + mWidth + ", height=" + mHeight + 
                          ", assetPath=" + (mAssetPath != null ? mAssetPath : "null"));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onSurfaceCreated", e);
            mProjectMInitialized = false;
        }
    }

    // Performance tracking
    private long lastFpsCheck = 0;
    private int frameCount = 0;
    private static final int FPS_UPDATE_INTERVAL = 5000; // 5 seconds
    private boolean lowPerformanceMode = false;
    
    @Override
    public void onDrawFrame(GL10 gl) {
        try {
            if (mProjectMInitialized) {
                // Track performance
                frameCount++;
                long now = System.currentTimeMillis();
                
                // Calculate FPS every 5 seconds
                if (now - lastFpsCheck > FPS_UPDATE_INTERVAL) {
                    float fps = (float) frameCount * 1000 / (now - lastFpsCheck);
                    Log.d(TAG, "Rendering at " + fps + " FPS");
                    
                    // If FPS is too low, switch to low performance mode
                    if (fps < 24 && !lowPerformanceMode) {
                        Log.w(TAG, "Low frame rate detected, activating low performance mode");
                        lowPerformanceMode = true;
                    } else if (fps > 28 && lowPerformanceMode) {
                        Log.d(TAG, "Performance improved, deactivating low performance mode");
                        lowPerformanceMode = false;
                    }
                    
                    lastFpsCheck = now;
                    frameCount = 0;
                }
                
                // Render the frame
                ProjectMJNI.onDrawFrame();
                
                // Auto-change presets if enabled
                if (autoChangeEnabled) {
                    if (now - lastPresetChange > PRESET_CHANGE_INTERVAL) {
                        // If in low performance mode, try to switch to a simpler preset
                        if (lowPerformanceMode) {
                            // For now just use random, but ideally would select less complex presets
                            randomPreset();
                        } else {
                            randomPreset();
                        }
                        lastPresetChange = now;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onDrawFrame", e);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Log.d(TAG, "onSurfaceChanged: " + width + "x" + height);
        Log.d(TAG, "Previous dimensions: " + mWidth + "x" + mHeight);
        mWidth = width;
        mHeight = height;
        
        try {
            if (mProjectMInitialized) {
                ProjectMJNI.onSurfaceChanged(width, height);
                Log.d(TAG, "Surface resized successfully");
            } else if (mAssetPath != null) {
                Log.d(TAG, "ProjectM not initialized yet, initializing now with: " + width + "x" + height);
                Log.d(TAG, "Asset path: " + mAssetPath);
                
                // Check if asset path exists
                java.io.File assetDir = new java.io.File(mAssetPath);
                Log.d(TAG, "Asset directory exists: " + assetDir.exists() + ", isDirectory: " + assetDir.isDirectory());
                
                if (assetDir.exists() && assetDir.isDirectory()) {
                    String[] files = assetDir.list();
                    if (files != null) {
                        Log.d(TAG, "Asset directory contains: " + files.length + " files");
                        for (String file : files) {
                            Log.d(TAG, "Asset file: " + file);
                        }
                    } else {
                        Log.w(TAG, "Could not list files in asset directory");
                    }
                }
                
                // Initialize if we have all required parameters
                ProjectMJNI.onSurfaceCreated(width, height, mAssetPath);
                mProjectMInitialized = true;
                lastPresetChange = System.currentTimeMillis();
                Log.i(TAG, "ProjectM initialized on surface change");
                
                // Verify initialization by getting current preset name
                String currentPreset = ProjectMJNI.getCurrentPresetName();
                Log.i(TAG, "Initial preset: " + currentPreset);
            } else {
                Log.w(TAG, "Cannot initialize projectM: assetPath is null");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onSurfaceChanged", e);
            mProjectMInitialized = false;
        }
    }
    
    public void addPCMData(short[] pcmData, int samples) {
        try {
            if (mProjectMInitialized && pcmData != null && samples > 0) {
                ProjectMJNI.addPCM(pcmData, (short)samples);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error adding PCM data", e);
        }
    }

    public void nextPreset() {
        try {
            if (mProjectMInitialized) {
                ProjectMJNI.nextPreset();
                Log.d(TAG, "Switched to next preset");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error switching to next preset", e);
        }
    }

    public void previousPreset() {
        try {
            if (mProjectMInitialized) {
                ProjectMJNI.previousPreset();
                Log.d(TAG, "Switched to previous preset");
                // Reset timer when manually changing presets
                lastPresetChange = System.currentTimeMillis();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error switching to previous preset", e);
        }
    }
    
    public void randomPreset() {
        try {
            if (mProjectMInitialized) {
                ProjectMJNI.selectRandomPreset();
                Log.d(TAG, "Switched to random preset");
                lastPresetChange = System.currentTimeMillis();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error switching to random preset", e);
        }
    }
    
    public String getCurrentPresetName() {
        try {
            if (mProjectMInitialized) {
                return ProjectMJNI.getCurrentPresetName();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting current preset name", e);
        }
        return "Unknown";
    }
    
    public void setAutoChange(boolean enabled) {
        autoChangeEnabled = enabled;
        Log.d(TAG, "Auto preset change " + (enabled ? "enabled" : "disabled"));
    }
    
    public boolean isAutoChangeEnabled() {
        return autoChangeEnabled;
    }
    
    public void setPresetDuration(int seconds) {
        try {
            if (mProjectMInitialized && seconds > 0) {
                ProjectMJNI.setPresetDuration(seconds);
                PRESET_CHANGE_INTERVAL = seconds * 1000L;
                Log.d(TAG, "Preset duration set to " + seconds + " seconds");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting preset duration", e);
        }
    }
    
    public void setSoftCutDuration(int seconds) {
        try {
            if (mProjectMInitialized && seconds >= 0) {
                ProjectMJNI.setSoftCutDuration(seconds);
                Log.d(TAG, "Soft cut duration set to " + seconds + " seconds");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting soft cut duration", e);
        }
    }
    
    public int getPresetCount() {
        try {
            if (mProjectMInitialized) {
                return ProjectMJNI.getPresetCount();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting preset count", e);
        }
        return 0;
    }
}
