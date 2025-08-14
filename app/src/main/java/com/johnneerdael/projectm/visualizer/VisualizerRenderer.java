package com.johnneerdael.projectm.visualizer;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.os.Build;
import android.content.Context;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.Intent;
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
    
    // Device performance detection
    public enum DevicePerformance { LOW, MEDIUM, HIGH, PREMIUM }
    private DevicePerformance devicePerformance;
    private int maxTextureSize = 0;
    private String gpuRenderer = "";
    private boolean isHighEndDevice = false;
    private Context appContext; // for receiver
    private BroadcastReceiver assetsReadyReceiver;

    public VisualizerRenderer(Context context, String assetPath) {
        Log.d(TAG, "VisualizerRenderer created with asset path: " + assetPath);
        mAssetPath = assetPath;
        this.appContext = context.getApplicationContext();
        registerAssetsReceiver();
        
        // Detect device performance capabilities
        detectDevicePerformance();
        
        // Use default width and height if not set yet
        if (mWidth <= 0 || mHeight <= 0) {
            mWidth = 1280;  // Default width for most Android TV devices
            mHeight = 720;  // Default height for most Android TV devices
            Log.d(TAG, "Using default dimensions: " + mWidth + "x" + mHeight);
        }
    }

    private void registerAssetsReceiver() {
        if (appContext == null) return;
        if (assetsReadyReceiver != null) return;
        assetsReadyReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (ProjectMApplication.ACTION_ASSETS_READY.equals(i.getAction())) {
                    String p = i.getStringExtra("path");
                    if (p != null && p.length() > 0) {
                        Log.i(TAG, "Assets ready broadcast received; reloading presets from: " + p);
                        try {
                            ProjectMJNI.nativeReloadPresets(p);
                        } catch (Throwable t) {
                            Log.e(TAG, "Failed to reload presets", t);
                        }
                    }
                }
            }
        };
        try {
            appContext.registerReceiver(assetsReadyReceiver, new IntentFilter(ProjectMApplication.ACTION_ASSETS_READY));
            Log.d(TAG, "Registered assets ready receiver");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register assets ready receiver", e);
        }
    }

    public void release() {
        if (appContext != null && assetsReadyReceiver != null) {
            try {
                appContext.unregisterReceiver(assetsReadyReceiver);
                Log.d(TAG, "Unregistered assets ready receiver");
            } catch (Exception e) {
                Log.w(TAG, "Error unregistering assets receiver", e);
            }
            assetsReadyReceiver = null;
        }
    }
    
    private void detectDevicePerformance() {
        // Detect high-end devices by model
        String model = Build.MODEL.toLowerCase();
        String manufacturer = Build.MANUFACTURER.toLowerCase();
        
        // NVIDIA Shield and other premium devices
        if (model.contains("shield") || model.contains("tegra")) {
            devicePerformance = DevicePerformance.PREMIUM;
            isHighEndDevice = true;
            Log.i(TAG, "Detected PREMIUM device (NVIDIA Shield/Tegra): " + Build.MODEL);
        }
        // Other high-end Android TV devices
        else if (model.contains("chromecast") && model.contains("ultra") ||
                 model.contains("mi box s") ||
                 model.contains("fire tv stick 4k")) {
            devicePerformance = DevicePerformance.HIGH;
            isHighEndDevice = true;
            Log.i(TAG, "Detected HIGH-END device: " + Build.MODEL);
        }
        // Medium performance devices
        else if (Build.VERSION.SDK_INT >= 28 && // Android 9+
                Runtime.getRuntime().maxMemory() > 1024 * 1024 * 1024) { // > 1GB RAM
            devicePerformance = DevicePerformance.MEDIUM;
            Log.i(TAG, "Detected MEDIUM performance device: " + Build.MODEL);
        }
        // Low-end devices
        else {
            devicePerformance = DevicePerformance.LOW;
            Log.i(TAG, "Detected LOW-END device: " + Build.MODEL);
        }
        
        Log.i(TAG, "Device Performance Level: " + devicePerformance + 
              ", RAM: " + (Runtime.getRuntime().maxMemory() / 1024 / 1024) + "MB" +
              ", Android API: " + Build.VERSION.SDK_INT);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.d(TAG, "onSurfaceCreated called, GL version: " + gl.glGetString(GL10.GL_VERSION));
        Log.d(TAG, "GL Vendor: " + gl.glGetString(GL10.GL_VENDOR));
        
        // Detect GPU capabilities
        gpuRenderer = gl.glGetString(GL10.GL_RENDERER);
        Log.d(TAG, "GL Renderer: " + gpuRenderer);
        
        // Get maximum texture size for performance optimization
        int[] maxTexSize = new int[1];
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTexSize, 0);
        maxTextureSize = maxTexSize[0];
        Log.i(TAG, "Max texture size: " + maxTextureSize);
        
        // Enhanced GPU detection for better performance classification
        String renderer = gpuRenderer.toLowerCase();
        if (renderer.contains("tegra") || renderer.contains("shield")) {
            devicePerformance = DevicePerformance.PREMIUM;
            isHighEndDevice = true;
        } else if (renderer.contains("adreno 640") || renderer.contains("adreno 650") || 
                   renderer.contains("mali-g76") || renderer.contains("mali-g77") ||
                   renderer.contains("powervr") && renderer.contains("ge8320")) {
            devicePerformance = DevicePerformance.HIGH;
            isHighEndDevice = true;
        } else if (maxTextureSize >= 4096) {
            if (devicePerformance == DevicePerformance.LOW) {
                devicePerformance = DevicePerformance.MEDIUM;
            }
        }
        
        Log.i(TAG, "Final device performance classification: " + devicePerformance);
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
                
                // Apply performance-based optimizations immediately after initialization
                applyPerformanceOptimizations();
                
                // CRITICAL: Set viewport immediately after ProjectM initialization
                // This ensures we capture the full display dimensions for viewport scaling
                try {
                    ProjectMJNI.setViewport(0, 0, mWidth, mHeight);
                    Log.i(TAG, "Set initial viewport to full display: " + mWidth + "x" + mHeight);
                } catch (Exception e) {
                    // Fall back to direct GL call if ProjectMJNI method fails
                    GLES20.glViewport(0, 0, mWidth, mHeight);
                    Log.i(TAG, "Set initial viewport via GLES20: " + mWidth + "x" + mHeight);
                }
                
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

    private void applyPerformanceOptimizations() {
        if (!mProjectMInitialized) return;
        
        try {
            switch (devicePerformance) {
                case PREMIUM: // NVIDIA Shield, high-end devices
                    Log.i(TAG, "Applying PREMIUM performance settings");
                    // Use high quality settings, longer transitions for smooth experience
                    ProjectMJNI.setPresetDuration(35); // Slightly longer for premium experience
                    ProjectMJNI.setSoftCutDuration(10); // Longer transitions look better on premium devices
                    // Use native optimization for enhanced performance
                    ProjectMJNI.optimizeForPerformance(2); // High performance level
                    Log.i(TAG, "Premium device: full quality at native resolution");
                    break;
                    
                case HIGH: // High-end devices like recent Shield, Mi Box S
                    Log.i(TAG, "Applying HIGH-END performance settings");
                    ProjectMJNI.setPresetDuration(30); // Longer duration for high quality
                    ProjectMJNI.setSoftCutDuration(7); // Smooth transitions
                    // High-end devices can handle full quality at full resolution
                    ProjectMJNI.optimizeForPerformance(2); // High performance level
                    Log.i(TAG, "High-end device: full quality at native resolution");
                    break;
                    
                case MEDIUM: // Mid-range Android TV devices
                    Log.i(TAG, "Applying MID-RANGE performance settings");
                    ProjectMJNI.setPresetDuration(25); // Standard duration
                    ProjectMJNI.setSoftCutDuration(5); // Standard transitions
                    // Moderate performance optimization while keeping full resolution
                    ProjectMJNI.optimizeForPerformance(1); // Medium performance level
                    Log.i(TAG, "Mid-range device: balanced quality at native resolution");
                    break;
                    
                case LOW: // Low-end devices, older Android TV boxes
                    Log.i(TAG, "Applying LOW-END performance settings");
                    ProjectMJNI.setPresetDuration(20); // Shorter duration to avoid complex presets
                    ProjectMJNI.setSoftCutDuration(3); // Quick transitions to save performance
                    // Aggressive performance optimization but still full resolution
                    ProjectMJNI.optimizeForPerformance(0); // Low performance level
                    lowPerformanceMode = true; // Enable additional performance monitoring
                    Log.i(TAG, "Low-end device: optimized quality settings at native resolution");
                    break;
            }
            
            // Performance optimizations are now handled through quality settings
            // rather than render resolution, ensuring full-screen coverage
            
        } catch (Exception e) {
            Log.e(TAG, "Error applying performance optimizations", e);
        }
    }
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
                // Store current viewport before rendering
                int[] viewport = new int[4];
                GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, viewport, 0);
                
                // Clear the entire screen to black to avoid artifacts when changing resolution
                GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
                
                // ALWAYS set viewport to full screen BEFORE rendering
                // This ensures the visualization is stretched to fill the entire screen
                try {
                    ProjectMJNI.setViewport(0, 0, mWidth, mHeight);
                    Log.v(TAG, "Set viewport before render: " + mWidth + "x" + mHeight);
                } catch (Exception e) {
                    // Fall back to direct GL call if ProjectMJNI method fails
                    GLES20.glViewport(0, 0, mWidth, mHeight);
                    Log.v(TAG, "Set viewport via GLES20 before render: " + mWidth + "x" + mHeight);
                }
                
                // Render the ProjectM visualization
                ProjectMJNI.onDrawFrame();
                
                // CRITICAL: Set viewport again AFTER ProjectM rendering
                // ProjectM might reset the viewport during rendering, so we need to restore it
                try {
                    ProjectMJNI.setViewport(0, 0, mWidth, mHeight);
                    Log.v(TAG, "Restored viewport after render: " + mWidth + "x" + mHeight);
                } catch (Exception e) {
                    // Fall back to direct GL call if ProjectMJNI method fails
                    GLES20.glViewport(0, 0, mWidth, mHeight);
                    Log.v(TAG, "Restored viewport via GLES20 after render: " + mWidth + "x" + mHeight);
                }
                
                // Verify viewport was set correctly
                int[] finalViewport = new int[4];
                GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, finalViewport, 0);
                if (finalViewport[2] != mWidth || finalViewport[3] != mHeight) {
                    Log.w(TAG, "Viewport mismatch! Expected: " + mWidth + "x" + mHeight + 
                          ", Actual: " + finalViewport[2] + "x" + finalViewport[3]);
                    
                    // Force it again
                    GLES20.glViewport(0, 0, mWidth, mHeight);
                } else {
                    Log.v(TAG, "Viewport correctly set to: " + finalViewport[2] + "x" + finalViewport[3]);
                }
                
                // Enhanced performance tracking with device-aware thresholds
                frameCount++;
                long now = System.currentTimeMillis();
                
                // Check for automatic preset changes
                if (autoChangeEnabled) {
                    if (now - lastPresetChange > PRESET_CHANGE_INTERVAL) {
                        randomPreset(lowPerformanceMode); // Use hardcut in low performance mode
                        lastPresetChange = now;
                        Log.d(TAG, "Auto-changing preset " + (lowPerformanceMode ? "with hardcut" : "with regular transition"));
                    }
                }
                
                // Calculate FPS every 2 seconds
                if (now - lastFpsCheck > FPS_UPDATE_INTERVAL) {
                    currentFps = (float) frameCount * 1000 / (now - lastFpsCheck);
                    Log.d(TAG, "Rendering at " + currentFps + " FPS on " + devicePerformance + " device");
                    
                    // Adaptive FPS thresholds based on device performance
                    float lowThreshold, highThreshold, targetFps;
                    
                    switch (devicePerformance) {
                        case PREMIUM:
                            targetFps = 60;
                            lowThreshold = 50;  // NVIDIA Shield should maintain high FPS
                            highThreshold = 55;
                            break;
                        case HIGH:
                            targetFps = 45;
                            lowThreshold = 35;
                            highThreshold = 40;
                            break;
                        case MEDIUM:
                            targetFps = 30;
                            lowThreshold = 25;
                            highThreshold = 28;
                            break;
                        case LOW:
                        default:
                            targetFps = 24;
                            lowThreshold = 18;
                            highThreshold = 22;
                            break;
                    }
                    
                    // Further adjust thresholds based on current resolution
                    if (customRenderWidth >= 1920 || customRenderHeight >= 1080) {
                        lowThreshold *= 0.8f;  // Be more lenient with high resolutions
                        highThreshold *= 0.8f;
                    }
                    
                    // Performance management logic
                    if (currentFps < lowThreshold && !lowPerformanceMode) {
                        Log.w(TAG, "Low frame rate detected (" + currentFps + " < " + lowThreshold + 
                              ") on " + devicePerformance + " device, activating performance mode");
                        lowPerformanceMode = true;
                        
                        // Apply performance optimizations
                        optimizeForPerformance();
                        
                    } else if (currentFps > highThreshold && lowPerformanceMode) {
                        Log.d(TAG, "Performance improved (" + currentFps + " > " + highThreshold + 
                              ") on " + devicePerformance + " device, restoring quality");
                        lowPerformanceMode = false;
                        
                        // Restore quality settings
                        restoreQualitySettings();
                    }
                    
                    lastFpsCheck = now;
                    frameCount = 0;
                }
                
                // Check for automatic preset changes
                if (autoChangeEnabled) {
                    if (System.currentTimeMillis() - lastPresetChange > PRESET_CHANGE_INTERVAL) {
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
                        lastPresetChange = System.currentTimeMillis();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onDrawFrame", e);
        }
    }

    private void optimizeForPerformance() {
        try {
            // Use native performance optimization for enhanced control
            ProjectMJNI.optimizeForPerformance(0); // Low performance level for degradation
            
            // Set low performance mode in native code (fallback)
            ProjectMJNI.setPerformanceLevel(1);
            
            // Reduce transition duration to minimize heavy transition effects
            ProjectMJNI.setSoftCutDuration(2); // Use quick transitions during performance issues
            
            // Trigger native memory trimming for low-end devices
            ProjectMJNI.trimMemory();
            
            // Automatically reduce resolution based on device capability and current performance
            if (currentFps < 15) { // Very poor performance
                if (customRenderWidth >= 1920 || customRenderHeight >= 1080) {
                    Log.w(TAG, "Critical performance: dropping to 720p");
                    setRenderResolution(1280, 720);
                } else if (customRenderWidth >= 1280 || customRenderHeight >= 720) {
                    Log.w(TAG, "Critical performance: dropping to 480p");
                    setRenderResolution(854, 480);
                }
            } else if (currentFps < 20) { // Poor performance
                if (customRenderWidth >= 3840 || customRenderHeight >= 2160) {
                    Log.w(TAG, "Poor performance: dropping from 4K to 1080p");
                    setRenderResolution(1920, 1080);
                } else if (customRenderWidth >= 1920 || customRenderHeight >= 1080) {
                    Log.w(TAG, "Poor performance: dropping from 1080p to 720p");
                    setRenderResolution(1280, 720);
                }
            }
            
            Log.i(TAG, "Applied performance optimizations with native enhancement for " + devicePerformance + " device");
            
        } catch (Exception e) {
            Log.e(TAG, "Error optimizing for performance", e);
        }
    }
    
    private void restoreQualitySettings() {
        try {
            // Use native performance optimization to restore quality
            switch (devicePerformance) {
                case PREMIUM:
                case HIGH:
                    ProjectMJNI.optimizeForPerformance(2); // High performance level
                    break;
                case MEDIUM:
                    ProjectMJNI.optimizeForPerformance(1); // Medium performance level
                    break;
                case LOW:
                default:
                    ProjectMJNI.optimizeForPerformance(0); // Low performance level (but not degraded)
                    break;
            }
            
            // Restore normal performance mode (fallback)
            ProjectMJNI.setPerformanceLevel(2);
            
            // Restore original transition duration based on device performance
            switch (devicePerformance) {
                case PREMIUM:
                    ProjectMJNI.setSoftCutDuration(10);
                    break;
                case HIGH:
                    ProjectMJNI.setSoftCutDuration(7);
                    break;
                case MEDIUM:
                    ProjectMJNI.setSoftCutDuration(5);
                    break;
                case LOW:
                default:
                    ProjectMJNI.setSoftCutDuration(3);
                    break;
            }
            
            Log.i(TAG, "Restored quality settings with native optimization for " + devicePerformance + " device");
            
            // Optionally restore higher resolution if performance allows
            // But be conservative - don't immediately jump to highest resolution
            if (devicePerformance == DevicePerformance.PREMIUM && currentFps > 45) {
                // Premium devices can handle full resolution when performing well
                if (mWidth > customRenderWidth || mHeight > customRenderHeight) {
                    Log.i(TAG, "Premium device performing well - considering resolution upgrade");
                    // Don't immediately jump to full 4K, step up gradually
                    if (customRenderWidth < 1920) {
                        setRenderResolution(1920, 1080);
                    }
                }
            }
            
            Log.i(TAG, "Restored quality settings for " + devicePerformance + " device");
            
        } catch (Exception e) {
            Log.e(TAG, "Error restoring quality settings", e);
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
        
        // CRITICAL FIX for viewport scaling issue:
        // Always pass FULL screen dimensions to ProjectM, regardless of performance settings
        // This ensures presets always fill the entire screen (720p/480p scaling issue fix)
        int renderWidth = width;   // Always use actual screen width
        int renderHeight = height; // Always use actual screen height
        
        Log.i(TAG, "Using FULL screen dimensions for ProjectM: " + renderWidth + "x" + renderHeight);
        
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
    
    /**
     * Get the detected device performance tier
     */
    public DevicePerformance getDevicePerformance() {
        return devicePerformance;
    }

    public void requestSurfaceResize() {
        // Placeholder: GLSurfaceView will invoke onSurfaceChanged on actual resize; here we just log.
        Log.d(TAG, "requestSurfaceResize invoked - forcing ProjectM to reconfigure on next frame");
        // Could add a flag to trigger reconfiguration if needed.
    }
}
