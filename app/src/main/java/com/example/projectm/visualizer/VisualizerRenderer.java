package com.example.projectm.visualizer;

import android.opengl.GLES20;
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
    private boolean startupMode = false; // Special mode for faster app startup

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
    private static final int FPS_UPDATE_INTERVAL = 2000; // 2 seconds
    private boolean lowPerformanceMode = false;
    private float currentFps = 0;
    
    // Custom rendering resolution
    private int customRenderWidth = 0;
    private int customRenderHeight = 0;
    
    @Override
    public void onDrawFrame(GL10 gl) {
        try {
            if (mProjectMInitialized) {
                // Clear the entire screen to black to avoid artifacts when changing resolution
                GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
                
                // Always set viewport to stretch content to full screen
                // This ensures the visualization is stretched to fill the entire screen
                try {
                    ProjectMJNI.setViewport(0, 0, mWidth, mHeight);
                } catch (Exception e) {
                    // Fall back to direct GL call if ProjectMJNI method fails
                    GLES20.glViewport(0, 0, mWidth, mHeight);
                }
                
                // Track performance
                frameCount++;
                long now = System.currentTimeMillis();
                
                // Calculate FPS every 2 seconds
                if (now - lastFpsCheck > FPS_UPDATE_INTERVAL) {
                    currentFps = (float) frameCount * 1000 / (now - lastFpsCheck);
                    Log.d(TAG, "Rendering at " + currentFps + " FPS");
                    
                    // If FPS is too low, switch to low performance mode
                    // Adjust thresholds based on resolution
                    float lowThreshold = 24;
                    float highThreshold = 28;
                    
                    // Lower thresholds for higher resolutions
                    if (customRenderWidth >= 1920 || customRenderHeight >= 1080) {
                        // For 1080p and 4K, be more lenient with frame rates
                        lowThreshold = 20;
                        highThreshold = 24;
                    } else if (customRenderWidth >= 1280 || customRenderHeight >= 720) {
                        // For 720p, use standard thresholds
                        lowThreshold = 24;
                        highThreshold = 28;
                    }
                    
                    if (currentFps < lowThreshold && !lowPerformanceMode) {
                        Log.w(TAG, "Low frame rate detected (" + currentFps + " < " + lowThreshold + "), activating low performance mode");
                        lowPerformanceMode = true;
                        
                        // Try to simplify current preset for better performance
                        try {
                            ProjectMJNI.setPerformanceLevel(1); // 1=Low performance mode
                            Log.d(TAG, "Set native performance level to LOW");
                        } catch (Exception e) {
                            Log.e(TAG, "Could not set performance level", e);
                        }
                        
                        // If in 4K or 1080p and performance is very poor, automatically lower resolution
                        if (currentFps < 15) {
                            if (customRenderWidth >= 3840 || customRenderHeight >= 2160) {
                                // Drop from 4K to 1080p
                                Log.w(TAG, "Very poor performance at 4K resolution. Automatically reducing to 1080p.");
                                setRenderResolution(1920, 1080);
                            } else if (currentFps < 10 && (customRenderWidth >= 1920 || customRenderHeight >= 1080)) {
                                // Drop from 1080p to 720p
                                Log.w(TAG, "Very poor performance at 1080p resolution. Automatically reducing to 720p.");
                                setRenderResolution(1280, 720);
                            }
                        }
                    } else if (currentFps > highThreshold && lowPerformanceMode) {
                        Log.d(TAG, "Performance improved (" + currentFps + " > " + highThreshold + "), deactivating low performance mode");
                        lowPerformanceMode = false;
                        
                        // Restore full quality settings
                        try {
                            ProjectMJNI.setPerformanceLevel(2); // 2=Normal performance mode
                            Log.d(TAG, "Set native performance level to NORMAL");
                        } catch (Exception e) {
                            Log.e(TAG, "Could not set performance level", e);
                        }
                    }
                    
                    lastFpsCheck = now;
                    frameCount = 0;
                }
                
                // Render the frame
                ProjectMJNI.onDrawFrame();
                
                // Ensure viewport is still correct after rendering
                try {
                    ProjectMJNI.setViewport(0, 0, mWidth, mHeight);
                } catch (Exception e) {
                    // Fall back to direct GL call if ProjectMJNI method fails
                    GLES20.glViewport(0, 0, mWidth, mHeight);
                }
                
                // Auto-change presets if enabled
                if (autoChangeEnabled) {
                    if (now - lastPresetChange > PRESET_CHANGE_INTERVAL) {
                        // If in low performance mode, try to switch to a simpler preset
                        if (lowPerformanceMode) {
                            // Use hardcut in low performance mode to avoid the heavy transition
                            randomPreset(true); // true = hard cut for better performance
                            Log.d(TAG, "Auto-changing preset with hardcut due to low performance mode");
                        } else {
                            // Normal transition in regular performance mode
                            randomPreset(false);
                            Log.d(TAG, "Auto-changing preset with regular transition");
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
        
        // Store the actual surface dimensions
        mWidth = width;
        mHeight = height;
        
        // Always set the viewport to the full screen size
        // This makes sure the visualization is stretched to fill the entire screen
        try {
            ProjectMJNI.setViewport(0, 0, mWidth, mHeight);
            Log.d(TAG, "Set viewport to full screen: " + mWidth + "x" + mHeight);
        } catch (Exception e) {
            // Fall back to direct GL call if ProjectMJNI method fails
            GLES20.glViewport(0, 0, mWidth, mHeight);
            Log.d(TAG, "Set viewport via GLES20 to full screen: " + mWidth + "x" + mHeight);
        }
        
        // If custom resolution is set, use it instead of actual surface dimensions for rendering
        int renderWidth = (customRenderWidth > 0) ? customRenderWidth : width;
        int renderHeight = (customRenderHeight > 0) ? customRenderHeight : height;
        
        if (customRenderWidth > 0 || customRenderHeight > 0) {
            Log.d(TAG, "Using custom render dimensions: " + renderWidth + "x" + renderHeight);
        }
        
        // Clear the screen to avoid artifacts when changing resolution
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        
        try {
            if (mProjectMInitialized) {
                ProjectMJNI.onSurfaceChanged(renderWidth, renderHeight);
                Log.d(TAG, "Surface resized successfully");
                
                // Make sure the viewport is still set correctly after native call
                try {
                    ProjectMJNI.setViewport(0, 0, mWidth, mHeight);
                } catch (Exception e) {
                    GLES20.glViewport(0, 0, mWidth, mHeight);
                }
            } else if (mAssetPath != null) {
                Log.d(TAG, "ProjectM not initialized yet, initializing now with: " + renderWidth + "x" + renderHeight);
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
                ProjectMJNI.onSurfaceCreated(renderWidth, renderHeight, mAssetPath);
                mProjectMInitialized = true;
                lastPresetChange = System.currentTimeMillis();
                Log.i(TAG, "ProjectM initialized on surface change");
                
                // Verify initialization by getting current preset name
                String currentPreset = ProjectMJNI.getCurrentPresetName();
                Log.i(TAG, "Initial preset: " + currentPreset);
                
                // Make sure the viewport is still set correctly after initialization
                try {
                    ProjectMJNI.setViewport(0, 0, mWidth, mHeight);
                } catch (Exception e) {
                    GLES20.glViewport(0, 0, mWidth, mHeight);
                }
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
        nextPreset(false); // Default to soft transition
    }
    
    public void nextPreset(boolean hardCut) {
        try {
            if (mProjectMInitialized) {
                ProjectMJNI.nextPreset(hardCut);
                Log.d(TAG, "Switched to next preset (hardCut=" + hardCut + ")");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error switching to next preset", e);
        }
    }

    public void previousPreset() {
        previousPreset(false); // Default to soft transition
    }
    
    public void previousPreset(boolean hardCut) {
        try {
            if (mProjectMInitialized) {
                ProjectMJNI.previousPreset(hardCut);
                Log.d(TAG, "Switched to previous preset (hardCut=" + hardCut + ")");
                // Reset timer when manually changing presets
                lastPresetChange = System.currentTimeMillis();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error switching to previous preset", e);
        }
    }
    
    public void randomPreset() {
        randomPreset(false); // Default to soft transition
    }
    
    public void randomPreset(boolean hardCut) {
        try {
            if (mProjectMInitialized) {
                if (startupMode) {
                    // In startup mode, use a simpler preset
                    // We should ideally have a list of "fast loading" presets,
                    // but for now we'll use the standard random selection
                    // TODO: Create a curated list of lightweight presets for startup
                    Log.d(TAG, "Selecting random preset (in startup mode, hardCut=" + hardCut + ")");
                } else {
                    Log.d(TAG, "Selecting random preset (normal mode, hardCut=" + hardCut + ")");
                }
                
                ProjectMJNI.selectRandomPreset(hardCut);
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
    
    /**
     * Set startup mode - uses simpler presets for faster initial loading
     */
    public void setStartupMode(boolean enabled) {
        startupMode = enabled;
        Log.d(TAG, "Startup mode " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Get current FPS value
     */
    public float getCurrentFps() {
        return currentFps;
    }
    
    /**
     * Set custom rendering resolution
     * Note: This doesn't immediately change the size, but will be used on the next surface change
     * or can be applied by forcing a surface recreation
     */
    public void setRenderResolution(int width, int height) {
        if (width <= 0 || height <= 0) {
            Log.w(TAG, "Invalid resolution: " + width + "x" + height);
            return;
        }
        
        Log.d(TAG, "Setting render resolution to " + width + "x" + height + " (display size: " + mWidth + "x" + mHeight + ")");
        
        // Check if the requested resolution is reasonable
        boolean isHighRes = (width >= 2560 || height >= 1440);
        
        // Get device's maximum texture size
        int[] maxTextureSize = new int[1];
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTextureSize, 0);
        int maxTexSize = maxTextureSize[0];
        
        if (maxTexSize > 0 && (width > maxTexSize || height > maxTexSize)) {
            Log.w(TAG, "Resolution " + width + "x" + height + " exceeds device maximum texture size: " + maxTexSize);
            
            // Scale down to maximum supported size
            float scale = Math.min((float)maxTexSize / width, (float)maxTexSize / height);
            width = (int)(width * scale);
            height = (int)(height * scale);
            Log.i(TAG, "Adjusted resolution to " + width + "x" + height);
        }
        
        // Store the previous resolution values in case we need to revert
        int oldWidth = customRenderWidth;
        int oldHeight = customRenderHeight;
        
        // Update the custom resolution values
        customRenderWidth = width;
        customRenderHeight = height;
        Log.d(TAG, "Custom resolution set to " + width + "x" + height);
        
        // If already initialized, update the size now
        if (mProjectMInitialized) {
            try {
                // Clear the screen to avoid artifacts when changing resolution
                GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
                
                // CRITICAL: Always set viewport to full screen size BEFORE changing the render resolution
                // This ensures the visualization is stretched to fit the full screen
                try {
                    ProjectMJNI.setViewport(0, 0, mWidth, mHeight);
                    Log.d(TAG, "Set viewport to full screen: " + mWidth + "x" + mHeight);
                } catch (Exception e) {
                    // Fall back to direct GL call if ProjectMJNI method fails
                    GLES20.glViewport(0, 0, mWidth, mHeight);
                    Log.d(TAG, "Set viewport via GLES20 to full screen: " + mWidth + "x" + mHeight);
                }
                
                // We tell ProjectM to render at the custom resolution
                ProjectMJNI.onSurfaceChanged(width, height);
                Log.i(TAG, "Applied new render resolution " + width + "x" + height);
                
                // CRITICAL: Set viewport again to ensure it wasn't changed by the native call
                try {
                    ProjectMJNI.setViewport(0, 0, mWidth, mHeight);
                    Log.d(TAG, "Re-set viewport after resolution change to: " + mWidth + "x" + mHeight);
                } catch (Exception e) {
                    // Fall back to direct GL call if ProjectMJNI method fails
                    GLES20.glViewport(0, 0, mWidth, mHeight);
                }
                
                // Display a warning if this is a high resolution on a device that might struggle
                if (isHighRes && currentFps < 30) {
                    Log.w(TAG, "High resolution may impact performance. Current FPS: " + currentFps);
                }
                
                // Force a render to update the screen with new resolution and viewport
                GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
                ProjectMJNI.onDrawFrame();
                
            } catch (Exception e) {
                Log.e(TAG, "Error changing resolution", e);
                
                // Revert to previous resolution on error
                customRenderWidth = oldWidth;
                customRenderHeight = oldHeight;
                
                // Fallback to a safer resolution if there was an error
                if (width > 1280 || height > 720) {
                    Log.i(TAG, "Falling back to 720p resolution after error");
                    customRenderWidth = 1280;
                    customRenderHeight = 720;
                    try {
                        // Clear screen and set viewport again
                        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
                        
                        // Always ensure viewport is full screen
                        try {
                            ProjectMJNI.setViewport(0, 0, mWidth, mHeight);
                        } catch (Exception ex) {
                            GLES20.glViewport(0, 0, mWidth, mHeight);
                        }
                        
                        // Update the internal render size
                        ProjectMJNI.onSurfaceChanged(1280, 720);
                        
                        // Set viewport again after the native call
                        try {
                            ProjectMJNI.setViewport(0, 0, mWidth, mHeight);
                        } catch (Exception ex) {
                            GLES20.glViewport(0, 0, mWidth, mHeight);
                        }
                        
                        // Force another render
                        ProjectMJNI.onDrawFrame();
                    } catch (Exception e2) {
                        Log.e(TAG, "Fallback resolution also failed", e2);
                    }
                }
            }
        }
    }
}
