package com.example.projectm.visualizer;

import android.app.Application;
import android.content.res.AssetManager;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

public class ProjectMApplication extends Application {
    private static final String TAG = "ProjectMApplication";
    
    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "==========================================");
        Log.i(TAG, "ProjectMApplication started successfully!");
        Log.i(TAG, "Device: " + android.os.Build.MANUFACTURER + " " + 
              android.os.Build.MODEL + ", Android " + android.os.Build.VERSION.RELEASE);
        Log.i(TAG, "Process ID: " + Process.myPid() + ", Thread: " + Thread.currentThread().getName());
        Log.i(TAG, "==========================================");

        // Extract assets in a try-catch block to ensure we capture all errors
        extractAssets();

        // Additional initialization logs
        logSystemInfo();
    }
    
    private void extractAssets() {
        try {
            Log.d(TAG, "Starting asset extraction...");
            
            File cacheDir = new File(getCacheDir(), "projectM");
            Log.d(TAG, "Target cache directory: " + cacheDir.getAbsolutePath());
            
            if (cacheDir.exists()) {
                Log.d(TAG, "Cache directory exists, removing old files");
                deleteFile(cacheDir);
            }
            
            if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                Log.e(TAG, "Failed to create cache directory: " + cacheDir.getAbsolutePath());
                // Try using files directory as a fallback
                cacheDir = new File(getFilesDir(), "projectM");
                Log.d(TAG, "Falling back to files directory: " + cacheDir.getAbsolutePath());
                
                if (cacheDir.exists()) {
                    deleteFile(cacheDir);
                }
                
                if (!cacheDir.mkdirs()) {
                    Log.e(TAG, "Failed to create fallback directory too!");
                    return;
                }
            }

            AssetManager assetManager = getAssets();
            String[] dirNames = {"presets"};
            
            // List all available assets to verify
            try {
                String[] rootAssets = assetManager.list("");
                Log.d(TAG, "Root assets: " + Arrays.toString(rootAssets));
            } catch (IOException e) {
                Log.e(TAG, "Failed to list root assets", e);
            }

            int extractedFiles = 0;
            for (String dirName : dirNames) {
                try {
                    String[] assetsList = assetManager.list(dirName);
                    if (assetsList == null || assetsList.length == 0) {
                        Log.w(TAG, "No assets found in directory: " + dirName);
                        continue;
                    }
                    
                    Log.d(TAG, "Found " + assetsList.length + " assets in " + dirName);
                    
                    File dir = new File(cacheDir, dirName);
                    if (!dir.exists() && !dir.mkdirs()) {
                        Log.e(TAG, "Failed to create directory: " + dir.getAbsolutePath());
                        continue;
                    }
                    
                    String assetDir = dirName; // Our presets are directly in the assets/presets directory
                    for (String fileName : assetsList) {
                        String assetName = dirName + "/" + fileName;
                        File outFile = new File(dir, fileName);
                        try {
                            writeStreamToFile(assetManager.open(assetName), outFile);
                            extractedFiles++;
                            if (extractedFiles % 10 == 0) {
                                Log.d(TAG, "Extracted " + extractedFiles + " files so far");
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "Failed to extract asset: " + assetName, e);
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error listing assets in directory: " + dirName, e);
                }
            }
            
            Log.i(TAG, "Completed asset extraction. Total files extracted: " + extractedFiles);
            Log.i(TAG, "Assets extracted to: " + cacheDir.getAbsolutePath());
            
            // Verify the extraction
            verifyExtractedAssets(cacheDir);
            
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error during asset extraction", e);
        }
    }
    
    private void verifyExtractedAssets(File cacheDir) {
        try {
            if (!cacheDir.exists() || !cacheDir.isDirectory()) {
                Log.e(TAG, "Cache directory doesn't exist after extraction!");
                return;
            }
            
            File presetsDir = new File(cacheDir, "presets");
            if (!presetsDir.exists() || !presetsDir.isDirectory()) {
                Log.e(TAG, "Presets directory doesn't exist after extraction!");
                return;
            }
            
            File[] presetFiles = presetsDir.listFiles();
            if (presetFiles == null || presetFiles.length == 0) {
                Log.e(TAG, "No preset files found after extraction!");
                return;
            }
            
            Log.i(TAG, "Verification complete: Found " + presetFiles.length + 
                  " preset files in " + presetsDir.getAbsolutePath());
            
            // Log a few of the files for debugging
            int filesToLog = Math.min(presetFiles.length, 5);
            for (int i = 0; i < filesToLog; i++) {
                Log.d(TAG, "Sample preset: " + presetFiles[i].getName() + 
                      " - " + presetFiles[i].length() + " bytes");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during verification of extracted assets", e);
        }
    }
    
    private void logSystemInfo() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        
        Log.i(TAG, "Memory - Max: " + maxMemory + "MB, Total: " + totalMemory + 
              "MB, Free: " + freeMemory + "MB");
        
        try {
            File dataDir = new File(getApplicationInfo().dataDir);
            Log.i(TAG, "App data directory: " + dataDir.getAbsolutePath() + 
                  " (free: " + dataDir.getFreeSpace() / (1024 * 1024) + "MB)");
        } catch (Exception e) {
            Log.e(TAG, "Error getting app data directory info", e);
        }
    }

    private static void deleteFile(File entry) {
        if (entry.isDirectory()) {
            for (File sub : entry.listFiles()) {
                deleteFile(sub);
            }
        }
        entry.delete();
    }

    private static void writeStreamToFile(InputStream is, File file) throws IOException {
        OutputStream os = null;
        try {
            os = new FileOutputStream(file);
            int read = 0;
            byte[] buf = new byte[1024];
            while ((read = is.read(buf)) != -1) {
                os.write(buf, 0, read);
            }
        }
        finally {
            if (os != null) {
                os.close();
            }
        }
    }
}
