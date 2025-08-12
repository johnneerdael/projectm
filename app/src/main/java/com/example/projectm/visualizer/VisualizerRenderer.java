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
        
        // If custom resolution is set, use it instead of actual surface dimensions
        int renderWidth = (customRenderWidth > 0) ? customRenderWidth : width;
        int renderHeight = (customRenderHeight > 0) ? customRenderHeight : height;
        
        if (customRenderWidth > 0 || customRenderHeight > 0) {
            Log.d(TAG, "Using custom render dimensions: " + renderWidth + "x" + renderHeight);
        }
        
        try {
            if (mProjectMInitialized) {
                ProjectMJNI.onSurfaceChanged(renderWidth, renderHeight);
                Log.d(TAG, "Surface resized successfully");
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
        
        customRenderWidth = width;
        customRenderHeight = height;
        Log.d(TAG, "Custom resolution set to " + width + "x" + height);
        
        // If already initialized, update the size now
        if (mProjectMInitialized) {
            try {
                // We can't change OpenGL context dimensions directly, so we notify the native code
                // to use a different internal buffer size for rendering
                ProjectMJNI.onSurfaceChanged(width, height);
                Log.i(TAG, "Applied new resolution " + width + "x" + height);
                
                // Display a warning if this is a high resolution on a device that might struggle
                if (isHighRes && currentFps < 30) {
                    Log.w(TAG, "High resolution may impact performance. Current FPS: " + currentFps);
                    // In a real app, we might show a toast or other UI notification here
                }
            } catch (Exception e) {
                Log.e(TAG, "Error changing resolution", e);
                
                // Fallback to a safer resolution if there was an error
                if (width > 1280 || height > 720) {
                    Log.i(TAG, "Falling back to 720p resolution after error");
                    customRenderWidth = 1280;
                    customRenderHeight = 720;
                    try {
                        ProjectMJNI.onSurfaceChanged(1280, 720);
                    } catch (Exception e2) {
                        Log.e(TAG, "Fallback resolution also failed", e2);
                    }
                }
            }
        }
    }
}
