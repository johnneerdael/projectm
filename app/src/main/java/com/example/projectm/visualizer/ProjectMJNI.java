package com.example.projectm.visualizer;

import android.util.Log;

public class ProjectMJNI {
    private static final String TAG = "ProjectMJNI";
    
    static {
        try {
            System.loadLibrary("projectmtv");
            Log.i(TAG, "Successfully loaded projectmtv library");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load projectmtv library", e);
        }
    }

    // Native method declarations
    private static native void nativeOnSurfaceCreated(int windowWidth, int windowHeight, String assetPath);
    private static native void nativeOnSurfaceChanged(int windowWidth, int windowHeight);
    private static native void nativeOnDrawFrame();
    private static native void nativeAddPCM(short[] pcmData, short nsamples);
    private static native void nativeNextPreset(boolean hardCut);
    private static native void nativePreviousPreset(boolean hardCut);
    private static native void nativeSelectRandomPreset(boolean hardCut);
    private static native String nativeGetCurrentPresetName();
    private static native void nativeSetPresetDuration(int seconds);
    private static native void nativeSetSoftCutDuration(int seconds);
    private static native void nativeDestroy();
    private static native String nativeGetVersion();
    private static native int nativeGetPresetCount();
    
    // Performance optimization methods
    private static native void nativeSetPerformanceLevel(int level);
    private static native void nativeSetViewport(int x, int y, int width, int height);
    private static native void nativeOptimizeForPerformance(int performanceLevel);
    private static native int nativeGetDeviceTier();
    private static native void nativeTrimMemory();

    // Wrapped native method calls with logging
    public static void onSurfaceCreated(int windowWidth, int windowHeight, String assetPath) {
        try {
            Log.d(TAG, "Calling native onSurfaceCreated: " + windowWidth + "x" + windowHeight + 
                  " with path: " + assetPath);
            nativeOnSurfaceCreated(windowWidth, windowHeight, assetPath);
            Log.d(TAG, "Native onSurfaceCreated completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Exception in onSurfaceCreated", e);
        }
    }
    
    public static void onSurfaceChanged(int windowWidth, int windowHeight) {
        try {
            Log.d(TAG, "Calling native onSurfaceChanged: " + windowWidth + "x" + windowHeight);
            nativeOnSurfaceChanged(windowWidth, windowHeight);
            Log.d(TAG, "Native onSurfaceChanged completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Exception in onSurfaceChanged", e);
        }
    }
    
    public static void onDrawFrame() {
        try {
            nativeOnDrawFrame();
        } catch (Exception e) {
            Log.e(TAG, "Exception in onDrawFrame", e);
        }
    }
    
    public static void addPCM(short[] pcmData, short nsamples) {
        try {
            nativeAddPCM(pcmData, nsamples);
        } catch (Exception e) {
            Log.e(TAG, "Exception in addPCM", e);
        }
    }
    
    public static void nextPreset() {
        nextPreset(false); // Default to soft transition
    }
    
    public static void nextPreset(boolean hardCut) {
        try {
            Log.d(TAG, "Calling native nextPreset (hardCut=" + hardCut + ")");
            nativeNextPreset(hardCut);
            Log.d(TAG, "Native nextPreset completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Exception in nextPreset", e);
        }
    }
    
    public static void previousPreset() {
        previousPreset(false); // Default to soft transition
    }
    
    public static void previousPreset(boolean hardCut) {
        try {
            Log.d(TAG, "Calling native previousPreset (hardCut=" + hardCut + ")");
            nativePreviousPreset(hardCut);
            Log.d(TAG, "Native previousPreset completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Exception in previousPreset", e);
        }
    }
    
    public static void selectRandomPreset() {
        selectRandomPreset(false); // Default to soft transition
    }
    
    public static void selectRandomPreset(boolean hardCut) {
        try {
            Log.d(TAG, "Calling native selectRandomPreset (hardCut=" + hardCut + ")");
            nativeSelectRandomPreset(hardCut);
            Log.d(TAG, "Native selectRandomPreset completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Exception in selectRandomPreset", e);
        }
    }
    
    public static String getCurrentPresetName() {
        try {
            Log.d(TAG, "Calling native getCurrentPresetName");
            String name = nativeGetCurrentPresetName();
            Log.d(TAG, "Native getCurrentPresetName returned: " + name);
            return name;
        } catch (Exception e) {
            Log.e(TAG, "Exception in getCurrentPresetName", e);
            return "Error";
        }
    }
    
    public static void setPresetDuration(int seconds) {
        try {
            Log.d(TAG, "Calling native setPresetDuration: " + seconds);
            nativeSetPresetDuration(seconds);
            Log.d(TAG, "Native setPresetDuration completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Exception in setPresetDuration", e);
        }
    }
    
    public static void setSoftCutDuration(int seconds) {
        try {
            Log.d(TAG, "Calling native setSoftCutDuration: " + seconds);
            nativeSetSoftCutDuration(seconds);
            Log.d(TAG, "Native setSoftCutDuration completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Exception in setSoftCutDuration", e);
        }
    }
    
    // Simple getter - we don't have a native method for this, so return a reasonable default
    public static int getSoftCutDuration() {
        return 7; // Default soft cut duration
    }
    
    public static void destroy() {
        try {
            Log.d(TAG, "Calling native destroy");
            nativeDestroy();
            Log.d(TAG, "Native destroy completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Exception in destroy", e);
        }
    }
    
    // Get ProjectM library version
    public static String getVersion() {
        try {
            return nativeGetVersion();
        } catch (Exception e) {
            Log.e(TAG, "Error getting version", e);
            return "Unknown";
        }
    }
    
    // Get total preset count
    public static int getPresetCount() {
        try {
            return nativeGetPresetCount();
        } catch (Exception e) {
            Log.e(TAG, "Error getting preset count", e);
            return 0;
        }
    }
    
    // Set performance level (1=low, 2=normal, 3=high)
    public static void setPerformanceLevel(int level) {
        try {
            Log.d(TAG, "Setting performance level: " + level);
            // The actual native method may not exist, so we'll handle it gracefully
            if (level < 1) level = 1;
            if (level > 3) level = 3;
            
            // We'll implement a fallback if the native method doesn't exist
            // by adjusting other parameters that can affect performance
            
            // For level 1 (low performance), reduce some quality settings
            if (level == 1) {
                // Reduce soft cut duration to minimize transition overhead
                setSoftCutDuration(1);
            }
            // For level 2 (normal performance), use default settings
            else if (level == 2) {
                // Restore default soft cut duration
                setSoftCutDuration(7);
            }
            
            // Try to call native method if it exists
            try {
                nativeSetPerformanceLevel(level);
            } catch (UnsatisfiedLinkError e) {
                // Native method not implemented, using our fallbacks above
                Log.d(TAG, "Native performance level setting not implemented, using fallback");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting performance level", e);
        }
    }
    
    // Enhanced performance optimization using native capabilities
    public static void optimizeForPerformance(int performanceLevel) {
        try {
            Log.d(TAG, "Optimizing for performance level: " + performanceLevel);
            nativeOptimizeForPerformance(performanceLevel);
            Log.d(TAG, "Native performance optimization completed");
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "Native performance optimization not available, using fallback");
            setPerformanceLevel(performanceLevel); // Fallback to existing method
        } catch (Exception e) {
            Log.e(TAG, "Error in performance optimization", e);
        }
    }
    
    // Get device tier from native layer
    public static int getDeviceTier() {
        try {
            int tier = nativeGetDeviceTier();
            Log.d(TAG, "Native device tier: " + tier);
            return tier;
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "Native device tier detection not available, returning default");
            return 1; // Default to MID_RANGE
        } catch (Exception e) {
            Log.e(TAG, "Error getting device tier", e);
            return 1;
        }
    }
    
    // Memory management
    public static void trimMemory() {
        try {
            Log.d(TAG, "Requesting native memory trim");
            nativeTrimMemory();
            Log.d(TAG, "Native memory trim completed");
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "Native memory trimming not available");
            // Could trigger Java GC here as fallback
            System.gc();
        } catch (Exception e) {
            Log.e(TAG, "Error in memory trimming", e);
        }
    }
    
    // Set GL viewport for correct scaling regardless of render resolution
    public static void setViewport(int x, int y, int width, int height) {
        try {
            Log.d(TAG, "Setting GL viewport: x=" + x + ", y=" + y + ", width=" + width + ", height=" + height);
            try {
                nativeSetViewport(x, y, width, height);
                Log.d(TAG, "Native viewport set successfully");
            } catch (UnsatisfiedLinkError e) {
                // Native method not implemented, we'll handle it in Java instead
                Log.d(TAG, "Native viewport setting not implemented, using Java implementation");
                android.opengl.GLES20.glViewport(x, y, width, height);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting viewport", e);
        }
    }
}
