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
        try {
            Log.d(TAG, "Calling native nextPreset");
            nativeNextPreset();
            Log.d(TAG, "Native nextPreset completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Exception in nextPreset", e);
        }
    }
    
    public static void previousPreset() {
        try {
            Log.d(TAG, "Calling native previousPreset");
            nativePreviousPreset();
            Log.d(TAG, "Native previousPreset completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Exception in previousPreset", e);
        }
    }
    
    public static void selectRandomPreset() {
        try {
            Log.d(TAG, "Calling native selectRandomPreset");
            nativeSelectRandomPreset();
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
    
    // Actual native method declarations
    private static native void nativeOnSurfaceCreated(int windowWidth, int windowHeight, String assetPath);
    private static native void nativeOnSurfaceChanged(int windowWidth, int windowHeight);
    private static native void nativeOnDrawFrame();
    private static native void nativeAddPCM(short[] pcmData, short nsamples);
    private static native void nativeNextPreset();
    private static native void nativePreviousPreset();
    private static native void nativeSelectRandomPreset();
    private static native String nativeGetCurrentPresetName();
    private static native void nativeSetPresetDuration(int seconds);
    private static native void nativeSetSoftCutDuration(int seconds);
    private static native void nativeDestroy();
    private static native String nativeGetVersion();
    private static native int nativeGetPresetCount();
}
