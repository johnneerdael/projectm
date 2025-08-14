package com.johnneerdael.projectm.visualizer;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.io.BufferedInputStream;

public class ProjectMApplication extends Application {
    private static final String TAG = "ProjectMApplication";
    private static final String PREFS = "projectm_prefs";
    private static final String KEY_ZIP_SHA256 = "presets_zip_sha256";
    private static final String KEY_SOURCE = "presets_zip_source"; // asset | external
    private static final String ASSET_ZIP_NAME = "presets.zip";
    private static final String ASSET_ZIP_SHA = "presets.zip.sha256"; // may exist at root or under presets/

    // Broadcast action so renderers can hot-reload presets after first-run extraction (path extra provided)
    public static final String ACTION_ASSETS_READY =
            "com.johnneerdael.projectm.visualizer.ASSETS_READY";
    // Incremental progress while extracting (0-100)
    public static final String ACTION_ASSETS_PROGRESS =
            "com.johnneerdael.projectm.visualizer.ASSETS_PROGRESS";

    // Static helpers so other classes don’t hardcode paths
    public static File getAssetRootDir(Context ctx) { return new File(ctx.getFilesDir(), "projectM"); }
    public static String getAssetRootPath(Context ctx) { return getAssetRootDir(ctx).getAbsolutePath(); }
     
    @Override
    public void onCreate() {
        super.onCreate();
    
        Log.i(TAG, "=== ProjectMApplication started ===");
        logSystemInfo();
        // Always move to fingerprint-based ensure path
        new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            try {
                ensurePresetsReadyFingerprint();
            } catch (Throwable t) {
                Log.e(TAG, "Preset fingerprint ensure failed", t);
            }
        }, "PresetEnsure").start();
    }

    /** Fingerprint ensure: external override > asset zip; re-extract only if SHA or source changes or sanity fails. */
    private void ensurePresetsReadyFingerprint() throws IOException {
        AssetManager am = getAssets();
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        File root = getAssetRootDir();
        File presetsDir = new File(root, "presets");
        if (!root.exists() && !root.mkdirs()) {
            throw new IOException("Failed to create asset root: " + root);
        }

        // Determine source (user external override or asset)
        File externalZip = null;
        try { externalZip = new File(getExternalFilesDir(null), ASSET_ZIP_NAME); } catch (Throwable ignored) {}
        boolean useExternal = externalZip != null && externalZip.exists() && externalZip.length() > 0;
        String sourceLabel = useExternal ? "external" : "asset";

        String currentSha;
        try {
            if (useExternal) {
                currentSha = sha256Stream(new FileInputStream(externalZip));
            } else {
                currentSha = readPrecomputedAssetSha(am);
                if (currentSha == null) {
                    try (InputStream is = am.open(ASSET_ZIP_NAME)) { currentSha = sha256Stream(is); }
                }
            }
        } catch (IOException ioe) {
            Log.w(TAG, "SHA compute failed, will extract anyway", ioe);
            currentSha = null;
        }

        String savedSha = prefs.getString(KEY_ZIP_SHA256, null);
        String savedSource = prefs.getString(KEY_SOURCE, null);
        boolean needExtract = (currentSha == null || savedSha == null || !savedSha.equalsIgnoreCase(currentSha) || (savedSource != null && !savedSource.equals(sourceLabel)));
        if (!needExtract && (!presetsDir.isDirectory() || !hasEnoughFiles(presetsDir))) {
            Log.i(TAG, "Preset directory sanity failed; forcing re-extract");
            needExtract = true;
        }

        if (!needExtract) {
            Log.i(TAG, "Presets up-to-date (source=" + sourceLabel + ") SHA=" + currentSha);
            sendBroadcast(new Intent(ACTION_ASSETS_PROGRESS)
                    .putExtra("progress", 100)
                    .putExtra("entries", 0)
                    .putExtra("total", 0));
            sendBroadcast(new Intent(ACTION_ASSETS_READY)
                    .putExtra("path", root.getAbsolutePath()));
            return;
        }

        Log.i(TAG, "Extracting presets (reason: " + (savedSha == null ? "first-run" : "changed") + ") source=" + sourceLabel);
        if (presetsDir.exists()) deleteRecursively(presetsDir);
        if (!presetsDir.mkdirs() && !presetsDir.isDirectory()) throw new IOException("Failed creating presets dir: " + presetsDir);

        int totalEntries = 0;
        if (useExternal) {
            try (FileInputStream fisCount = new FileInputStream(externalZip)) { totalEntries = countZipEntries(fisCount); }
            try (FileInputStream fis = new FileInputStream(externalZip)) { extractZipStream(fis, presetsDir, totalEntries); }
        } else {
            try { totalEntries = countZipEntries(am.open(ASSET_ZIP_NAME)); } catch (IOException e) { Log.w(TAG, "Entry count failed", e); }
            try (InputStream actual = am.open(ASSET_ZIP_NAME)) { extractZipStream(actual, presetsDir, totalEntries); }
        }
        try { new File(presetsDir, ".nomedia").createNewFile(); } catch (Throwable ignored) {}
        if (currentSha != null) {
            prefs.edit().putString(KEY_ZIP_SHA256, currentSha).putString(KEY_SOURCE, sourceLabel).apply();
        }
        sendBroadcast(new Intent(ACTION_ASSETS_READY).putExtra("path", root.getAbsolutePath()));
    }

    private boolean hasEnoughFiles(File dir) {
        String[] list = dir.list();
        return list != null && list.length > 50;
    }

    private String readPrecomputedAssetSha(AssetManager am) {
        String[] candidates = {ASSET_ZIP_SHA, "presets/" + ASSET_ZIP_SHA};
        for (String c : candidates) {
            try (InputStream is = am.open(c)) {
                byte[] buf = new byte[256];
                int n = is.read(buf);
                if (n > 0) {
                    String line = new String(buf, 0, n).trim();
                    int space = line.indexOf(' ');
                    if (space > 0) line = line.substring(0, space);
                    if (line.matches("[0-9a-fA-F]{32,64}")) {
                        Log.i(TAG, "Using precomputed SHA asset: " + line);
                        return line.toLowerCase();
                    }
                }
            } catch (IOException ignored) {}
        }
        return null;
    }

    private String sha256Stream(InputStream is) throws IOException {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[64 * 1024];
            int r; while ((r = is.read(buf)) != -1) md.update(buf, 0, r);
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format(java.util.Locale.US, "%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 unavailable", e);
        } finally { try { is.close(); } catch (Throwable ignored) {} }
    }

    /** Extract a zip InputStream to destination directory recursively. Returns count of files created.
     *  Provides incremental progress broadcasts and light throttling to reduce I/O contention.
     */
    private int extractZipStream(InputStream zipIn, File destDir, int totalEntries) throws IOException {
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new IOException("Failed to create dest dir: " + destDir);
        }
        int files = 0;
        long lastBroadcastTime = 0L;
        final long BROADCAST_MIN_INTERVAL_MS = 150; // avoid spamming UI thread
        long bytesSinceSleep = 0L;
        final long SLEEP_THRESHOLD_BYTES = 2 * 1024 * 1024; // yield every ~2MB
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(zipIn))) {
            ZipEntry entry;
            byte[] buffer = new byte[32 * 1024];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    File dir = new File(destDir, entry.getName());
                    if (!dir.exists() && !dir.mkdirs()) {
                        Log.w(TAG, "Could not create dir: " + dir);
                    }
                    continue;
                }
                File outFile = new File(destDir, entry.getName());
                File parent = outFile.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    Log.w(TAG, "Could not create parent: " + parent);
                }
                if (outFile.exists() && outFile.length() > 0) { // Skip existing
                    continue;
                }
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    int r;
                    while ((r = zis.read(buffer)) != -1) {
                        fos.write(buffer, 0, r);
                        bytesSinceSleep += r;
                        if (bytesSinceSleep >= SLEEP_THRESHOLD_BYTES) {
                            // Light cooperative yield to lower storage bus contention for foreground thread(s)
                            try { Thread.sleep(2); } catch (InterruptedException ignored) {}
                            bytesSinceSleep = 0L;
                        }
                    }
                }
                files++;
                // Progress broadcast (rate-limited)
                if (totalEntries > 0) {
                    long now = System.currentTimeMillis();
                    if (now - lastBroadcastTime >= BROADCAST_MIN_INTERVAL_MS || files == totalEntries) {
                        int progress = (int) Math.min(100, Math.round((files * 100f) / totalEntries));
                        sendBroadcast(new Intent(ACTION_ASSETS_PROGRESS)
                                .putExtra("progress", progress)
                                .putExtra("entries", files)
                                .putExtra("total", totalEntries));
                        lastBroadcastTime = now;
                    }
                } else if (files % 1000 == 0) { // fallback logging if no total
                    Log.d(TAG, "Zip extracted files=" + files);
                }
            }
        }
        // Ensure a final 100% broadcast
        if (totalEntries > 0) {
            sendBroadcast(new Intent(ACTION_ASSETS_PROGRESS)
                    .putExtra("progress", 100)
                    .putExtra("entries", files)
                    .putExtra("total", totalEntries));
        }
        return files;
    }

    /** Legacy signature retained (fallback callers) */
    private int extractZipStream(InputStream zipIn, File destDir) throws IOException {
        return extractZipStream(zipIn, destDir, 0);
    }

    /** Count entries consuming provided stream. */
    private int countZipEntries(InputStream in) {
        int count = 0; try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(in))) { while (zis.getNextEntry() != null) count++; } catch (IOException e) { Log.w(TAG, "Count entries failed", e); } return count; }
    
    /** Use filesDir/projectM (persistent). */
    private File getAssetRootDir() {
        return new File(getFilesDir(), "projectM"); // NOT cacheDir
    }
    
    // Version-marker logic removed (replaced by SHA fingerprinting)

    private static String readAll(File f) throws IOException {
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] b = new byte[(int)Math.min(f.length(), 1024)];
            int n = fis.read(b);
            return new String(b, 0, Math.max(0, n)).trim();
        }
    }
    
    @SuppressWarnings("deprecation")
    private String getAppVersionSafe() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                return getPackageManager().getPackageInfo(getPackageName(), android.content.pm.PackageManager.PackageInfoFlags.of(0)).versionName;
            } else {
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            }
        } catch (Throwable t) { return "0"; }
    }
    
    // Legacy extractAssets() method removed in favor of extractAssetsVersioned()
    
    private void verifyExtractedAssets(File cacheDir) {
        try {
            if (!cacheDir.exists() || !cacheDir.isDirectory()) {
                Log.e(TAG, "Assets directory doesn't exist after extraction!");
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

    private static void deleteFile(File entry) { if (entry.isDirectory()) { File[] subs = entry.listFiles(); if (subs != null) for (File s : subs) deleteFile(s); } entry.delete(); }

    // writeStreamToFile no longer used (zip extraction path handles streaming)

    /** Recursive delete for fresh extraction on zip change */
    private static void deleteRecursively(File f) { if (f == null || !f.exists()) return; if (f.isDirectory()) { File[] kids = f.listFiles(); if (kids != null) for (File k : kids) deleteRecursively(k);} f.delete(); }

    /** Zip Slip protection */
    private static File safeChild(File root, String name) throws IOException { File f = new File(root, name); String rp = root.getCanonicalPath(); String cp = f.getCanonicalPath(); if (!cp.startsWith(rp + File.separator) && !cp.equals(rp)) throw new IOException("Blocked Zip Slip entry: " + name); return f; }
}
