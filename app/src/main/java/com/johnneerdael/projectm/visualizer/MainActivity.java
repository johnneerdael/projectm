package com.johnneerdael.projectm.visualizer;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetManager;
import android.media.audiofx.Visualizer;
import android.os.Bundle;
import androidx.core.splashscreen.SplashScreen;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final String TAG = "ProjectMTV";
    private static final int AUDIO_CAPTURE_PERMISSION_REQUEST = 1;
    private static final int MENU_AUTO_HIDE_DELAY = 5000; // 5 seconds auto-hide
    private static final long ASSET_EXTRACTION_TIMEOUT = 15000; // 15 seconds timeout for asset extraction

    private VisualizerView visualizerView;
    private VisualizerRenderer renderer;
    private Visualizer audioVisualizer;
    
    // Loading screen components
    private View loadingScreen;
    private ProgressBar loadingProgressBar;
    private TextView loadingStatusText;
    private BroadcastReceiver assetsProgressReceiver;
    private BroadcastReceiver assetsReadyReceiver;
    
    // UI components
    private View overlayMenu;
    private TextView presetNameText;
    private Switch autoChangeSwitch;
    private SeekBar presetDurationSeekBar;
    private TextView presetDurationText;
    private SeekBar transitionDurationSeekBar;
    private TextView transitionDurationText;
    private TextView helpText;
    private TextView fpsDisplay;
    private RadioGroup resolutionGroup;
    private Switch performanceModeSwitch;
    
    // FPS monitoring for auto resolution adjustment
    private static final float FPS_TARGET = 24.0f;
    private static final long FPS_CHECK_INTERVAL = 5000; // Check every 5 seconds
    private final Handler fpsCheckHandler = new Handler(Looper.getMainLooper());
    private Runnable fpsCheckRunnable;
    
    // Resolution options
    private static final int RESOLUTION_480P = 0;
    private static final int RESOLUTION_720P = 1;
    private static final int RESOLUTION_1080P = 2;
    private static final int RESOLUTION_4K = 3;
    
    // Preference keys
    private static final String PREF_NAME = "projectm_settings";
    private static final String PREF_RESOLUTION = "selected_resolution";
    private static final String PREF_AUTO_CHANGE = "auto_change_enabled";
    private static final String PREF_PRESET_DURATION = "preset_duration";
    private static final String PREF_TRANSITION_DURATION = "transition_duration";
    
    // For handling auto-hide
    private final Handler autoHideHandler = new Handler(Looper.getMainLooper());
    private Runnable autoHideRunnable;
    
    // For updating the FPS display
    private final Handler fpsHandler = new Handler(Looper.getMainLooper());
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable fpsUpdateRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            // Install SplashScreen (API 31+ will show system splash; pre-31 no-op)
            SplashScreen splash = SplashScreen.installSplashScreen(this);
            super.onCreate(savedInstanceState);
            if (splash != null) {
                splash.setOnExitAnimationListener((provider) -> {
                    // Simple fade + slight scale for polish
                    View icon = provider.getIconView();
                    icon.animate()
                        .alpha(0f)
                        .scaleX(0.85f)
                        .scaleY(0.85f)
                        .setDuration(300)
                        .withEndAction(provider::remove)
                        .start();
                });
            }
            Log.i(TAG, "==================================");
            Log.i(TAG, "MainActivity onCreate started");
            Log.i(TAG, "==================================");
            
            // Set up full screen activity
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            Log.d(TAG, "Window flags set for fullscreen and screen-on");

            // Show the loading screen first
            setContentView(R.layout.loading_screen);
            Log.d(TAG, "Loading screen set");
            
            // Initialize loading screen components
            loadingScreen = findViewById(android.R.id.content).getRootView();
            loadingProgressBar = findViewById(R.id.loading_progress);
            loadingStatusText = findViewById(R.id.loading_status);
            
            // Set version on loading screen
            TextView versionText = findViewById(R.id.version_text);
            // Defer device/version logging & version label population to next looper turn to avoid delaying first frame
            versionText.post(() -> {
                try {
                    Log.i(TAG, "Device info: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + ", Android " + android.os.Build.VERSION.RELEASE);
                    String appVersion = getAppVersionCompat();
                    versionText.setText("v" + appVersion);
                    Log.i(TAG, "App version: " + appVersion);
                    // Log storage AFTER splash
                    File filesDir = getFilesDir();
                    File cacheDir = getCacheDir();
                    Log.d(TAG, "Cache directory: " + cacheDir.getAbsolutePath() + " (free: " + cacheDir.getFreeSpace()/1024/1024 + "MB)");
                    Log.d(TAG, "Files directory: " + filesDir.getAbsolutePath() + " (free: " + filesDir.getFreeSpace()/1024/1024 + "MB)");
                } catch (Exception e) {
                    Log.e(TAG, "Post-splash logging failed", e);
                }
            });
            
            // Update loading status
            updateLoadingStatus("Preparing visualizer assets...");
            initAssetProgressObservers();
            
            // Initialize renderer with asset path (after splash is visible)
            File presetPath = ProjectMApplication.getAssetRootDir(getApplicationContext());
            File presetsDir = new File(presetPath, "presets");
            Log.d(TAG, "Using presetPath: " + presetPath.getAbsolutePath());
            
            // Continue initialization in a separate thread to prevent UI blocking
            new Thread(() -> {
                // No blocking wait: rely on ACTION_ASSETS_READY broadcast to hot-reload presets when extraction finishes.
                // We optimistically proceed; initial playlist may be small/empty until broadcast triggers reload.

                // Check preset directory
                if (presetPath.exists() && presetPath.isDirectory()) {
                    File[] presetFiles = presetPath.listFiles();
                    if (presetFiles != null) {
                        Log.i(TAG, "Preset directory contains " + presetFiles.length + " files/directories");
                        // Log the first few files to verify content
                        int logCount = Math.min(presetFiles.length, 10);
                        for (int i = 0; i < logCount; i++) {
                            Log.d(TAG, "Preset file " + (i+1) + ": " + presetFiles[i].getName() + 
                                (presetFiles[i].isDirectory() ? " (directory)" : " (file, " + presetFiles[i].length() + " bytes)"));
                        }
                        if (presetFiles.length > logCount) {
                            Log.d(TAG, "... and " + (presetFiles.length - logCount) + " more files");
                        }
                    } else {
                        Log.w(TAG, "Preset directory exists but failed to list files");
                    }
                } else {
                    Log.e(TAG, "Preset directory does not exist or is not a directory");
                }
                
                // Update loading status
                updateLoadingStatus("Initializing visualizer engine...");
                
                // Switch to main view on the UI thread
                runOnUiThread(() -> {
                    try {
                        // Switch to the main layout
                        setContentView(R.layout.activity_main);
                        Log.d(TAG, "Main layout set successfully");
                        
                        // Get the VisualizerView from the layout
                        visualizerView = findViewById(R.id.visualizer_view);
                        if (visualizerView == null) {
                            Log.e(TAG, "Failed to find VisualizerView in layout");
                            finish();
                            return;
                        }
                        Log.d(TAG, "Found VisualizerView in layout");
                        
                        // Create the renderer
                        VisualizerRenderer renderer = new VisualizerRenderer(getApplicationContext(), presetPath.getAbsolutePath());
                        Log.i(TAG, "VisualizerRenderer object created successfully");
                        
                        // Set renderer on view
                        visualizerView.setRenderer(renderer);
                        Log.i(TAG, "Renderer attached to VisualizerView");
                        
                        // Store renderer reference
                        this.renderer = renderer;
                        Log.i(TAG, "VisualizerRenderer creation complete");
                        
                        // Initialize UI after renderer is set up
                        initUI();
                        
                        // With device-tier based performance optimization, we no longer need auto-resolution switching
                        // The fixed resolution per device tier provides optimal performance
                        Log.d(TAG, "Device-tier performance optimization active - auto-resolution disabled");
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error creating renderer", e);
                    }
                });
            }).start();
            
            // Try getting the current preset name to verify initialization
            // But only if renderer is initialized
            try {
                if (renderer != null) {
                    String presetName = renderer.getCurrentPresetName();
                    Log.i(TAG, "Initial preset: " + (presetName != null ? presetName : "NULL"));
                } else {
                    Log.i(TAG, "Initial preset: NULL (renderer not initialized yet)");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting initial preset name", e);
            }
            
            // Initialize the UI components
            initUI();
            
            // Log important objects status
            Log.i(TAG, "Key objects state check:");
            Log.i(TAG, "VisualizerView: " + (visualizerView != null ? "initialized" : "NULL"));
            Log.i(TAG, "Renderer: " + (renderer != null ? "initialized" : "NULL"));
            
            // Verify files in the preset directory one more time
            // reuse presetsDir defined earlier
            if (presetsDir.exists() && presetsDir.isDirectory()) {
                File[] files = presetsDir.listFiles();
                Log.i(TAG, "Presets directory contents: " + (files != null ? files.length + " files" : "failed to list"));
            } else {
                Log.e(TAG, "Presets directory does not exist or is not a directory!");
            }

            // Handle permissions
            Log.i(TAG, "Checking audio permissions...");
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Requesting audio permission");
                ActivityCompat.requestPermissions(this, 
                    new String[]{
                        Manifest.permission.RECORD_AUDIO, 
                        Manifest.permission.MODIFY_AUDIO_SETTINGS
                    }, 
                    AUDIO_CAPTURE_PERMISSION_REQUEST);
            } else {
                Log.d(TAG, "Audio permission already granted");
                initAudio();
            }
            
            // Add a delayed check to make sure visualization starts
            // Use a shorter delay for faster startup
            mainHandler.postDelayed(() -> {
                if (visualizerView != null && renderer != null) {
                    Log.i(TAG, "Requesting render after delay");
                    visualizerView.requestRender();
                    
                    // Try to start a random preset
                    try {
                        // Use a simpler preset initially for faster startup
                        renderer.setStartupMode(true);
                        renderer.randomPreset();
                        String preset = renderer.getCurrentPresetName();
                        Log.i(TAG, "Started preset: " + preset);
                        
                        // After a short delay, disable startup mode
                        mainHandler.postDelayed(() -> {
                            if (renderer != null) {
                                renderer.setStartupMode(false);
                                Log.d(TAG, "Exited startup mode");
                            }
                        }, 3000); // 3 seconds of startup mode
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to start preset", e);
                    }
                }
            }, 500); // Half second delay for faster startup
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate: " + e.getMessage(), e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        try {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            if (requestCode == AUDIO_CAPTURE_PERMISSION_REQUEST) {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Audio permission granted");
                    initAudio();
                } else {
                    Log.w(TAG, "Audio permission denied - visualizer will work without audio");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in permission result: " + e.getMessage());
        }
    }

    private void copyAssetsToStorage() {
        // Assets are now handled by ProjectMApplication, no need to do it here
        Log.d(TAG, "Presets are now handled by ProjectMApplication");
    }
    
    /**
     * Wait for asset extraction to complete with a timeout.
     * This helps prevent starting the visualization before assets are ready.
     * 
     * @param presetPath The path where presets should be extracted to
     */
    /**
     * Updates the loading screen status text and optionally the progress
     * 
     * @param status Text to display as current status
     */
    private void updateLoadingStatus(final String status) {
        if (loadingStatusText == null) return;
        
        runOnUiThread(() -> {
            loadingStatusText.setText(status);
            Log.d(TAG, "Loading status: " + status);
        });
    }

    private void initAssetProgressObservers() {
        if (assetsProgressReceiver != null) return; // already
        try {
            // Ensure progress bar is determinate now that we'll have %
            if (loadingProgressBar != null) {
                loadingProgressBar.setIndeterminate(false);
                loadingProgressBar.setMax(100);
            }
            assetsProgressReceiver = new BroadcastReceiver() {
                @Override public void onReceive(android.content.Context context, Intent intent) {
                    int progress = intent.getIntExtra("progress", -1);
                    int entries = intent.getIntExtra("entries", -1);
                    int total = intent.getIntExtra("total", -1);
                    if (progress >= 0 && loadingProgressBar != null) {
                        loadingProgressBar.setProgress(progress);
                        updateLoadingStatus("Extracting presets " + progress + "% (" + entries + "/" + total + ")");
                    }
                }
            };
            registerReceiver(assetsProgressReceiver, new IntentFilter(ProjectMApplication.ACTION_ASSETS_PROGRESS));

            assetsReadyReceiver = new BroadcastReceiver() {
                @Override public void onReceive(android.content.Context context, Intent intent) {
                    // Finalize loading UI quickly; actual renderer swap may already be underway
                    updateLoadingStatus("Assets ready. Initializing engine...");
                    if (loadingProgressBar != null) loadingProgressBar.setProgress(100);
                }
            };
            registerReceiver(assetsReadyReceiver, new IntentFilter(ProjectMApplication.ACTION_ASSETS_READY));
        } catch (Exception e) {
            Log.e(TAG, "Failed to register asset progress receivers", e);
        }
    }
    
    // Removed synchronous waitForAssetExtraction(): startup now non-blocking; playlist populated/expanded via broadcast.

    private void initUI() {
        try {
            Log.d(TAG, "Initializing UI components");
            
            // Find overlay components
            overlayMenu = findViewById(R.id.overlay_menu);
            presetNameText = findViewById(R.id.preset_name);
            // autoChangeSwitch = findViewById(R.id.auto_change_switch); // Commented out - not in current layout
            presetDurationSeekBar = findViewById(R.id.preset_duration_seekbar);
            presetDurationText = findViewById(R.id.preset_duration_text);
            transitionDurationSeekBar = findViewById(R.id.transition_duration_seekbar);
            transitionDurationText = findViewById(R.id.transition_duration_text);
            helpText = findViewById(R.id.help_text);
            fpsDisplay = findViewById(R.id.fps_display);
            resolutionGroup = findViewById(R.id.resolution_group);
            performanceModeSwitch = findViewById(R.id.performance_mode_switch);
            
            // Log UI component status
            Log.d(TAG, "UI components found: " + 
                  "overlay=" + (overlayMenu != null) + 
                  ", presetText=" + (presetNameText != null) + 
                  ", autoSwitch=" + (autoChangeSwitch != null) + 
                  ", durationBar=" + (presetDurationSeekBar != null) + 
                  ", durationText=" + (presetDurationText != null) + 
                  ", transitionBar=" + (transitionDurationSeekBar != null) + 
                  ", transitionText=" + (transitionDurationText != null) + 
                  ", fpsDisplay=" + (fpsDisplay != null) + 
                  ", resolutionGroup=" + (resolutionGroup != null));
            
            // Set initial values
            if (renderer != null) {
                Log.d(TAG, "Setting initial UI values from renderer");
                
                // Load preferences
                android.content.SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
                
                // Disable auto-change by default since we use device-tier based optimization
                boolean autoChangeEnabled = false;
                Log.d(TAG, "Auto-change disabled in favor of device-tier optimization");
                
                // Restore preset duration
                int presetDuration = prefs.getInt(PREF_PRESET_DURATION, 30); // Default 30 seconds
                presetDurationSeekBar.setProgress(presetDuration);
                presetDurationText.setText(presetDuration + "s");
                
                // Apply preset duration to renderer
                renderer.setPresetDuration(presetDuration);
                Log.d(TAG, "Restored preset duration: " + presetDuration + "s");
                
                // Restore transition duration
                int transitionDuration = prefs.getInt(PREF_TRANSITION_DURATION, 7); // Default 7 seconds
                transitionDurationSeekBar.setProgress(transitionDuration);
                transitionDurationText.setText(transitionDuration + "s");
                
                // Apply transition duration to renderer
                renderer.setSoftCutDuration(transitionDuration);
                Log.d(TAG, "Restored transition duration: " + transitionDuration + "s");
                
                // Display version information
                TextView versionText = findViewById(R.id.version_info);
                if (versionText != null) {
                    try {
                        String appVersion = getAppVersionCompat();
                        String projectMVersion = ProjectMJNI.getVersion();
                        versionText.setText("App v" + appVersion + " | ProjectM: " + projectMVersion);
                    } catch (Exception e) {
                        Log.e(TAG, "Error getting version info", e);
                        versionText.setText("Version info unavailable");
                    }
                }
                
                // Set up resolution radio buttons
                if (resolutionGroup != null) {
                    resolutionGroup.setOnCheckedChangeListener((group, checkedId) -> {
                        int resolution = RESOLUTION_720P; // Default
                        int width = 1280;
                        int height = 720;
                        
                        if (checkedId == R.id.resolution_480p) {
                            resolution = RESOLUTION_480P;
                            width = 854;
                            height = 480;
                        } else if (checkedId == R.id.resolution_720p) {
                            resolution = RESOLUTION_720P;
                            width = 1280;
                            height = 720;
                        } else if (checkedId == R.id.resolution_1080p) {
                            resolution = RESOLUTION_1080P;
                            width = 1920;
                            height = 1080;
                        } else if (checkedId == R.id.resolution_4k) {
                            resolution = RESOLUTION_4K;
                            width = 3840;
                            height = 2160;
                        }
                        
                        if (renderer != null) {
                            renderer.setRenderResolution(width, height);
                            // Also call native method for FBO system
                            ProjectMJNI.setRenderResolution(width, height);
                            Log.d(TAG, "Resolution changed to " + width + "x" + height + " (both renderer and native)");
                            
                            // Save the selected resolution preference
                            prefs.edit().putInt(PREF_RESOLUTION, resolution).apply();
                            Log.d(TAG, "Saved resolution preference: " + resolution);
                        }
                        
                        resetAutoHideTimer();
                    });
                    
                    // Get device-appropriate default resolution for new installations
                    int defaultResolution = getDefaultResolutionForDevice();
                    int savedResolution = prefs.getInt(PREF_RESOLUTION, defaultResolution);
                    Log.d(TAG, "Device default resolution: " + defaultResolution + ", restored saved resolution: " + savedResolution);
                    
                    // Set the radio button based on saved preference
                    int radioButtonId;
                    int width = 1280;
                    int height = 720;
                    
                    switch(savedResolution) {
                        case RESOLUTION_480P:
                            radioButtonId = R.id.resolution_480p;
                            width = 854;
                            height = 480;
                            break;
                        case RESOLUTION_1080P:
                            radioButtonId = R.id.resolution_1080p;
                            width = 1920;
                            height = 1080;
                            break;
                        case RESOLUTION_4K:
                            radioButtonId = R.id.resolution_4k;
                            width = 3840;
                            height = 2160;
                            break;
                        case RESOLUTION_720P:
                        default:
                            radioButtonId = R.id.resolution_720p;
                            width = 1280;
                            height = 720;
                            break;
                    }
                    
                    // Check the appropriate radio button (without triggering listener)
                    resolutionGroup.setOnCheckedChangeListener(null);
                    resolutionGroup.check(radioButtonId);
                    
                    // Apply the resolution to the renderer
                    if (renderer != null) {
                        renderer.setRenderResolution(width, height);
                        // Also call native method for FBO system
                        ProjectMJNI.setRenderResolution(width, height);
                        Log.d(TAG, "Applied saved resolution: " + width + "x" + height + " (both renderer and native)");
                    }
                    
                    // Re-attach the listener
                    resolutionGroup.setOnCheckedChangeListener((group, checkedId) -> {
                        int resolution = RESOLUTION_720P;
                        int newWidth = 1280;
                        int newHeight = 720;
                        
                        if (checkedId == R.id.resolution_480p) {
                            resolution = RESOLUTION_480P;
                            newWidth = 854;
                            newHeight = 480;
                        } else if (checkedId == R.id.resolution_720p) {
                            resolution = RESOLUTION_720P;
                            newWidth = 1280;
                            newHeight = 720;
                        } else if (checkedId == R.id.resolution_1080p) {
                            resolution = RESOLUTION_1080P;
                            newWidth = 1920;
                            newHeight = 1080;
                        } else if (checkedId == R.id.resolution_4k) {
                            resolution = RESOLUTION_4K;
                            newWidth = 3840;
                            newHeight = 2160;
                        }
                        
                        if (renderer != null) {
                            renderer.setRenderResolution(newWidth, newHeight);
                            Log.d(TAG, "Resolution changed to " + newWidth + "x" + newHeight);
                            
                            // Save the selected resolution preference
                            prefs.edit().putInt(PREF_RESOLUTION, resolution).apply();
                            Log.d(TAG, "Saved resolution preference: " + resolution);
                        }
                        
                        resetAutoHideTimer();
                    });
                }
                
                // Set up FPS display update
                if (fpsDisplay != null) {
                    fpsUpdateRunnable = () -> {
                        if (renderer != null) {
                            float fps = renderer.getCurrentFps();
                            fpsDisplay.setText(String.format("FPS: %.1f", fps));
                            
                            // Add performance warning if FPS is very low
                            if (fps < 15) {
                                fpsDisplay.setTextColor(0xFFFF0000); // Red color
                            } else if (fps < 25) {
                                fpsDisplay.setTextColor(0xFFFFAA00); // Orange color
                            } else {
                                fpsDisplay.setTextColor(0xFF00FF00); // Green color
                            }
                        }
                        fpsHandler.postDelayed(fpsUpdateRunnable, 500); // Update twice per second
                    };
                    fpsHandler.post(fpsUpdateRunnable);
                }
            } else {
                Log.w(TAG, "Renderer is null when setting initial UI values");
            }
            
            // Set up button listeners
            Button prevButton = findViewById(R.id.prev_preset_button);
            Button randomButton = findViewById(R.id.random_preset_button);
            Button nextButton = findViewById(R.id.next_preset_button);
            
            prevButton.setOnClickListener(v -> {
                if (renderer != null) {
                    renderer.previousPreset(true); // true means use hard cut
                    updatePresetName();
                }
                resetAutoHideTimer();
            });
            
            randomButton.setOnClickListener(v -> {
                if (renderer != null) {
                    renderer.randomPreset(true); // true means use hard cut
                    updatePresetName();
                }
                resetAutoHideTimer();
            });
            
            nextButton.setOnClickListener(v -> {
                if (renderer != null) {
                    renderer.nextPreset(true); // true means use hard cut
                    updatePresetName();
                }
                resetAutoHideTimer();
            });
            
            // Set up auto change switch
            autoChangeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (renderer != null) {
                    renderer.setAutoChange(isChecked);
                    
                    // Save preference
                    android.content.SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
                    prefs.edit().putBoolean(PREF_AUTO_CHANGE, isChecked).apply();
                    Log.d(TAG, "Saved auto-change preference: " + isChecked);
                }
                resetAutoHideTimer();
            });
            
            // Restore auto-change preference
            android.content.SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
            boolean autoChangeEnabled = prefs.getBoolean(PREF_AUTO_CHANGE, false); // Default to off
            if (autoChangeEnabled && renderer != null) {
                autoChangeSwitch.setChecked(autoChangeEnabled);
                renderer.setAutoChange(autoChangeEnabled);
                Log.d(TAG, "Restored auto-change setting: " + autoChangeEnabled);
            }
            
            // Set up seekbar for preset duration
            presetDurationSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    // The SeekBar now has min/max attributes so we don't need to enforce minimum
                    int duration = progress;
                    presetDurationText.setText(duration + "s");
                    resetAutoHideTimer();
                }
                
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    // Not used
                }
                
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    int duration = seekBar.getProgress();
                    if (renderer != null) {
                        renderer.setPresetDuration(duration);
                        
                        // Save preference
                        android.content.SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
                        prefs.edit().putInt(PREF_PRESET_DURATION, duration).apply();
                        Log.d(TAG, "Saved preset duration preference: " + duration + "s");
                    }
                    resetAutoHideTimer();
                }
            });
            
            // Set up seekbar for transition duration
            transitionDurationSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    transitionDurationText.setText(progress + "s");
                    resetAutoHideTimer();
                }
                
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    // Not used
                }
                
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    int duration = seekBar.getProgress();
                    if (renderer != null) {
                        renderer.setSoftCutDuration(duration);
                        
                        // Save preference
                        android.content.SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
                        prefs.edit().putInt(PREF_TRANSITION_DURATION, duration).apply();
                        Log.d(TAG, "Saved transition duration preference: " + duration + "s");
                    }
                    resetAutoHideTimer();
                }
            });
            
            // Define auto-hide runnable
            autoHideRunnable = () -> {
                if (overlayMenu != null && overlayMenu.getVisibility() == View.VISIBLE) {
                    overlayMenu.setVisibility(View.GONE);
                    helpText.setVisibility(View.VISIBLE);
                }
            };
            
            // Hide overlay initially
            overlayMenu.setVisibility(View.GONE);
            helpText.setVisibility(View.VISIBLE);
            
            performanceModeSwitch = findViewById(R.id.performance_mode_switch);
            if (performanceModeSwitch != null) {
                // Initialize from native (after surface create this will reflect heuristic changes)
                boolean nativePerf = true;
                try { nativePerf = ProjectMJNI.isPerformanceModeEnabled(); } catch (Exception ignored) {}
                performanceModeSwitch.setChecked(nativePerf);
                performanceModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    Log.i(TAG, "UI Performance mode toggled: " + isChecked);
                    ProjectMJNI.setPerformanceMode(isChecked);
                    resetAutoHideTimer();
                });
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing UI: " + e.getMessage(), e);
        }
    }
    
    private void toggleOverlay() {
        if (overlayMenu.getVisibility() == View.VISIBLE) {
            // Hide overlay
            overlayMenu.setVisibility(View.GONE);
            // We no longer show the help text, it's been removed
            autoHideHandler.removeCallbacks(autoHideRunnable);
            
            // Make sure the main visualizer view is focused for key handling
            if (visualizerView != null) {
                visualizerView.requestFocus();
            }
        } else {
            // Show overlay
            overlayMenu.setVisibility(View.VISIBLE);
            
            // Set the menu width to 40% of screen width
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) overlayMenu.getLayoutParams();
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            params.width = (int)(screenWidth * 0.4); // 40% of screen width
            overlayMenu.setLayoutParams(params);
            
            // Focus the preset name for marquee scrolling
            if (presetNameText != null) {
                presetNameText.setSelected(true);
            }
            
            // Update FPS display immediately
            if (fpsDisplay != null && renderer != null) {
                float fps = renderer.getCurrentFps();
                fpsDisplay.setText(String.format("FPS: %.1f", fps));
            }
            
            updatePresetName();
            resetAutoHideTimer();
            
            // Find a focusable control in the menu and focus it for better navigation
            if (autoChangeSwitch != null && autoChangeSwitch.isFocusable()) {
                autoChangeSwitch.requestFocus();
                Log.d(TAG, "Auto change switch focused");
            } else if (resolutionGroup != null) {
                // Try to focus the currently selected radio button
                try {
                    RadioButton selectedButton = findViewById(resolutionGroup.getCheckedRadioButtonId());
                    if (selectedButton != null) {
                        selectedButton.requestFocus();
                        Log.d(TAG, "Resolution radio button focused");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error focusing resolution button: " + e.getMessage());
                }
            }
        }
    }
    
    private void updatePresetName() {
        if (renderer != null && presetNameText != null) {
            String presetName = renderer.getCurrentPresetName();
            // Extract just the filename without path
            int lastSlash = presetName.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < presetName.length() - 1) {
                presetName = presetName.substring(lastSlash + 1);
            }
            
            // Get total preset count to display
            int totalPresets = 0;
            try {
                totalPresets = ProjectMJNI.getPresetCount();
            } catch (Exception e) {
                Log.e(TAG, "Error getting preset count", e);
            }
            
            // Update the display text
            if (totalPresets > 0) {
                presetNameText.setText(String.format("Current: %s (%d presets total)", 
                    presetName, totalPresets));
            } else {
                presetNameText.setText("Current: " + presetName);
            }
            
            // Enable marquee scrolling
            presetNameText.setSelected(true);
            presetNameText.setHorizontallyScrolling(true);
            presetNameText.setMarqueeRepeatLimit(-1); // Infinite
        }
    }
    
    private void resetAutoHideTimer() {
        // Remove any pending auto-hide
        autoHideHandler.removeCallbacks(autoHideRunnable);
        // Schedule a new auto-hide
        autoHideHandler.postDelayed(autoHideRunnable, MENU_AUTO_HIDE_DELAY);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        try {
            Log.d(TAG, "Key pressed: " + keyCode);
            
            // Different behavior depending on whether the menu is visible
            boolean menuVisible = overlayMenu != null && overlayMenu.getVisibility() == View.VISIBLE;
            
            // Get current focused view for better navigation in menu
            View focusedView = getCurrentFocus();
            
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                    if (menuVisible) {
                        // Enhanced menu navigation
                        if (resolutionGroup != null && resolutionGroup.hasFocus()) {
                            // When in resolution group, allow right navigation between radio buttons
                            Log.d(TAG, "Handling right navigation in resolution group");
                            resetAutoHideTimer();
                            // Let the system handle the navigation
                            return super.onKeyDown(keyCode, event);
                        } else {
                            // Standard menu navigation
                            Log.d(TAG, "Standard menu navigation - right");
                            resetAutoHideTimer();
                            return super.onKeyDown(keyCode, event);
                        }
                    } else {
                        // When menu is closed, go to next random preset
                        Log.d(TAG, "Next random preset requested");
                        if (renderer != null) {
                            renderer.randomPreset(true); // true means use hard cut
                            
                            // Update preset name in a toast notification
                            String presetName = renderer.getCurrentPresetName();
                            if (presetName != null) {
                                int lastSlash = presetName.lastIndexOf('/');
                                if (lastSlash >= 0 && lastSlash < presetName.length() - 1) {
                                    presetName = presetName.substring(lastSlash + 1);
                                }
                                android.widget.Toast.makeText(this, "Preset: " + presetName, 
                                                            android.widget.Toast.LENGTH_SHORT).show();
                            }
                        }
                        return true;
                    }
                
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                    if (menuVisible) {
                        // Enhanced menu navigation
                        if (resolutionGroup != null && resolutionGroup.hasFocus()) {
                            // When in resolution group, allow left navigation between radio buttons
                            Log.d(TAG, "Handling left navigation in resolution group");
                            resetAutoHideTimer();
                            // Let the system handle the navigation
                            return super.onKeyDown(keyCode, event);
                        } else {
                            // Standard menu navigation
                            Log.d(TAG, "Standard menu navigation - left");
                            resetAutoHideTimer();
                            return super.onKeyDown(keyCode, event);
                        }
                    } else {
                        // When menu is closed, go back to previous preset
                        Log.d(TAG, "Previous preset requested");
                        if (renderer != null) {
                            renderer.previousPreset(true); // true means use hard cut
                            
                            // Update preset name in a toast notification
                            String presetName = renderer.getCurrentPresetName();
                            if (presetName != null) {
                                int lastSlash = presetName.lastIndexOf('/');
                                if (lastSlash >= 0 && lastSlash < presetName.length() - 1) {
                                    presetName = presetName.substring(lastSlash + 1);
                                }
                                android.widget.Toast.makeText(this, "Preset: " + presetName, 
                                                            android.widget.Toast.LENGTH_SHORT).show();
                            }
                        }
                        return true;
                    }
                
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (menuVisible) {
                        // Enhanced menu navigation for up/down
                        Log.d(TAG, "Menu navigation: " + (keyCode == KeyEvent.KEYCODE_DPAD_UP ? "up" : "down"));
                        resetAutoHideTimer();
                        return super.onKeyDown(keyCode, event);
                    }
                    // Let the system handle it if menu is not visible
                    return super.onKeyDown(keyCode, event);
                
                case KeyEvent.KEYCODE_MENU:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    Log.d(TAG, "Menu/select key pressed");
                    
                    // If we're in the resolution group, handle selection differently
                    if (menuVisible && resolutionGroup != null && resolutionGroup.hasFocus()) {
                        Log.d(TAG, "Select key in resolution group - letting system handle it");
                        resetAutoHideTimer();
                        return super.onKeyDown(keyCode, event);
                    }
                    
                    toggleOverlay();
                    return true;
                
                case KeyEvent.KEYCODE_BACK:
                    if (menuVisible) {
                        // If menu is visible, hide it instead of exiting
                        toggleOverlay();
                        return true;
                    } else {
                        Log.d(TAG, "Back key pressed, finishing activity");
                        finish();
                        return true;
                    }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling key event: " + e.getMessage());
        }
        return super.onKeyDown(keyCode, event);
    }

    private void initAudio() {
        try {
            Log.i(TAG, "=== Initializing audio visualizer for system audio ===");
            
            // Note: Visualizer.isSupported() is not available in Android API
            // We'll attempt to create the visualizer directly and handle any exceptions
            Log.i(TAG, "Attempting to create system audio visualizer");
            
            // Create visualizer with session ID 0 (system audio output)
            Log.i(TAG, "Creating Visualizer with session ID 0 (system audio)");
            audioVisualizer = new Visualizer(0);
            
            if (audioVisualizer == null) {
                Log.e(TAG, "Failed to create Visualizer instance");
                return;
            }
            Log.i(TAG, "Visualizer instance created successfully");

            // Get capture size
            int[] captureSizeRange = Visualizer.getCaptureSizeRange();
            int captureSize = captureSizeRange[1]; // Use maximum
            Log.i(TAG, "Capture size range: " + captureSizeRange[0] + " - " + captureSizeRange[1] + ", using: " + captureSize);
            
            int result = audioVisualizer.setCaptureSize(captureSize);
            Log.i(TAG, "setCaptureSize result: " + result + " (SUCCESS=" + Visualizer.SUCCESS + ")");
            
            Log.i(TAG, "Setting up data capture listener...");
            audioVisualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {
                    try {
                        if (waveform == null || waveform.length == 0) {
                            Log.w(TAG, "Received null or empty waveform data");
                            return;
                        }
                        
                        Log.v(TAG, "Received waveform data: " + waveform.length + " bytes, sampling rate: " + samplingRate);
                        
                        short[] pcm = new short[waveform.length / 2];
                        for (int i = 0; i < pcm.length; i++) {
                            pcm[i] = (short) ((waveform[2 * i] & 0xFF) | (waveform[2 * i + 1] << 8));
                        }
                        
                        if (visualizerView != null && visualizerView.getRenderer() != null) {
                            visualizerView.getRenderer().addPCMData(pcm, pcm.length);
                            visualizerView.requestRender();
                        } else {
                            Log.w(TAG, "VisualizerView or renderer is null, cannot process PCM data");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing waveform data: " + e.getMessage());
                    }
                }

                @Override
                public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
                    // Not used but required by interface
                }
            }, Visualizer.getMaxCaptureRate() / 2, true, false);

            Log.i(TAG, "Enabling audio visualizer...");
            int result2 = audioVisualizer.setEnabled(true);
            if (result2 == Visualizer.SUCCESS) {
                Log.i(TAG, "=== Audio visualizer enabled successfully ===");
            } else {
                Log.e(TAG, "=== Failed to enable audio visualizer, status code: " + result2 + " ===");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing audio: " + e.getMessage(), e);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            // Stop auto-hide timer
            autoHideHandler.removeCallbacks(autoHideRunnable);
            
            if (visualizerView != null) {
                try {
                    visualizerView.onPause();
                } catch (NullPointerException e) {
                    // This can happen if GLThread is not initialized yet
                    Log.d(TAG, "GLThread not ready yet in onPause: " + e.getMessage());
                }
            }
            if (audioVisualizer != null && audioVisualizer.getEnabled()) {
                audioVisualizer.setEnabled(false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onPause: " + e.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            Log.d(TAG, "onResume called");
            
            // Check window focus
            boolean hasFocus = hasWindowFocus();
            Log.i(TAG, "Window has focus: " + hasFocus);
            
            if (visualizerView != null) {
                try {
                    Log.d(TAG, "Resuming VisualizerView");
                    visualizerView.onResume();
                    // Request render to ensure we're displaying visuals
                    visualizerView.requestRender();
                } catch (NullPointerException e) {
                    // This can happen if GLThread is not initialized yet
                    Log.d(TAG, "GLThread not ready yet, will retry later: " + e.getMessage());
                }
            } else {
                Log.w(TAG, "VisualizerView is null in onResume");
            }
            
            if (audioVisualizer != null) {
                boolean isEnabled = audioVisualizer.getEnabled();
                Log.d(TAG, "Audio visualizer enabled: " + isEnabled);
                if (!isEnabled) {
                    int result = audioVisualizer.setEnabled(true);
                    Log.d(TAG, "Enabled audio visualizer, status code: " + result);
                }
            } else {
                Log.w(TAG, "AudioVisualizer is null in onResume");
            }
            
            // Update preset name if overlay is visible
            if (overlayMenu != null && overlayMenu.getVisibility() == View.VISIBLE) {
                updatePresetName();
            }
            
            // Force the window to gain focus - this is crucial for Android TV
            getWindow().getDecorView().post(() -> {
                Log.d(TAG, "Requesting window focus");
                getWindow().getDecorView().requestFocus();
                
                // Schedule another render after a slight delay to ensure visuals are shown
                mainHandler.postDelayed(() -> {
                    if (visualizerView != null) {
                        try {
                            Log.d(TAG, "Delayed request for render");
                            visualizerView.requestRender();
                            // Try to call onResume again if it failed earlier
                            visualizerView.onResume();
                        } catch (Exception e) {
                            Log.e(TAG, "Error in delayed render request: " + e.getMessage());
                        }
                    }
                }, 500); // 500ms delay
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in onResume: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        Log.d(TAG, "onWindowFocusChanged: " + hasFocus);
        
        try {
            if (hasFocus) {
                Log.d(TAG, "Window gained focus, making adjustments");
                
                applyImmersiveMode();
                
                // Post render request to the UI thread with delay to allow initialization
                mainHandler.postDelayed(() -> {
                    // Request render to ensure visualization is displayed
                    if (visualizerView != null) {
                        try {
                            Log.d(TAG, "Requesting render due to window focus gain");
                            visualizerView.requestRender();
                        } catch (Exception e) {
                            Log.e(TAG, "Error requesting render after focus gain", e);
                        }
                    }
                }, 500);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling window focus change", e);
        }
    }
    
    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(TAG, "onConfigurationChanged: orientation=" + newConfig.orientation);
        
        try {
            // Get saved resolution preference
            android.content.SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
            int savedResolution = prefs.getInt(PREF_RESOLUTION, getDefaultResolutionForDevice());
            
            // Apply the resolution to the renderer
            int width = 1280;
            int height = 720;
            int radioButtonId = R.id.resolution_720p;
            
            switch(savedResolution) {
                case RESOLUTION_480P:
                    width = 854;
                    height = 480;
                    radioButtonId = R.id.resolution_480p;
                    break;
                case RESOLUTION_1080P:
                    width = 1920;
                    height = 1080;
                    radioButtonId = R.id.resolution_1080p;
                    break;
                case RESOLUTION_4K:
                    width = 3840;
                    height = 2160;
                    radioButtonId = R.id.resolution_4k;
                    break;
                case RESOLUTION_720P:
                default:
                    width = 1280;
                    height = 720;
                    radioButtonId = R.id.resolution_720p;
                    break;
            }
            
            // Apply resolution to renderer
            if (renderer != null) {
                Log.d(TAG, "Re-applying resolution setting from preferences: " + width + "x" + height);
                renderer.setRenderResolution(width, height);
            }
            
            // Update radio button if needed
            if (resolutionGroup != null && resolutionGroup.getCheckedRadioButtonId() != radioButtonId) {
                resolutionGroup.setOnCheckedChangeListener(null);
                resolutionGroup.check(radioButtonId);
                // Re-attach the listener (this simplified version assumes we reset it elsewhere)
                resolutionGroup.setOnCheckedChangeListener((group, checkedId) -> resetAutoHideTimer());
            }
            
            // Re-apply overlay menu width if it's visible
            if (overlayMenu != null && overlayMenu.getVisibility() == View.VISIBLE) {
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) overlayMenu.getLayoutParams();
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                params.width = (int)(screenWidth * 0.4); // 40% of screen width
                overlayMenu.setLayoutParams(params);
                Log.d(TAG, "Re-applied overlay menu width: " + params.width + "px");
            }
            
            // Request render to refresh visualization
            if (visualizerView != null) {
                visualizerView.requestRender();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling configuration change", e);
        }
    }

    /**
     * Initializes the automatic resolution adjustment system
     * This monitors FPS and reduces resolution when performance is poor
     */
    private void initAutoResolutionAdjustment() {
        if (renderer == null) return;
        
        fpsCheckRunnable = () -> {
            if (renderer != null) {
                float currentFps = renderer.getCurrentFps();
                
                // Check if we need to adjust resolution based on FPS
                if (currentFps < FPS_TARGET && currentFps > 0) {
                    Log.d(TAG, "Low FPS detected: " + currentFps + " - adjusting resolution");
                    
                    // Get current resolution settings
                    android.content.SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
                    int currentResolution = prefs.getInt(PREF_RESOLUTION, getDefaultResolutionForDevice());
                    
                    // Lower resolution if possible
                    int newResolution = currentResolution;
                    int width = 0;
                    int height = 0;
                    int radioButtonId = 0;
                    
                    // Don't go below 480p
                    if (currentResolution > RESOLUTION_480P) {
                        newResolution = currentResolution - 1;
                        
                        switch(newResolution) {
                            case RESOLUTION_480P:
                                width = 854;
                                height = 480;
                                radioButtonId = R.id.resolution_480p;
                                break;
                            case RESOLUTION_720P:
                                width = 1280;
                                height = 720;
                                radioButtonId = R.id.resolution_720p;
                                break;
                            case RESOLUTION_1080P:
                                width = 1920;
                                height = 1080;
                                radioButtonId = R.id.resolution_1080p;
                                break;
                        }
                        
                        // Update renderer resolution
                        if (width > 0 && height > 0) {
                            final int finalRadioButtonId = radioButtonId;
                            final int finalWidth = width;
                            final int finalHeight = height;
                            final float finalFps = currentFps;
                            final int finalNewResolution = newResolution;
                            final android.content.SharedPreferences finalPrefs = prefs;
                            
                            runOnUiThread(() -> {
                                // First ensure visualization view is properly set up
                                if (visualizerView != null) {
                                    // Force a clear frame to remove any artifacts before changing resolution
                                    visualizerView.requestRender();
                                    
                                    // Apply new resolution
                                    renderer.setRenderResolution(finalWidth, finalHeight);
                                    Log.i(TAG, "Auto-adjusted resolution to " + finalWidth + "x" + finalHeight + 
                                         " due to low FPS (" + finalFps + ")");
                                    
                                    // Request another render to immediately show the changed resolution
                                    visualizerView.requestRender();
                                }
                                
                                // Update radio button if UI is initialized
                                if (resolutionGroup != null) {
                                    resolutionGroup.setOnCheckedChangeListener(null);
                                    resolutionGroup.check(finalRadioButtonId);
                                    
                                    // Re-attach listener
                                    resolutionGroup.setOnCheckedChangeListener((group, checkedId) -> {
                                        // (Resolution change code that's already in the file)
                                        resetAutoHideTimer();
                                    });
                                }
                                
                                // Save the new resolution preference
                                finalPrefs.edit().putInt(PREF_RESOLUTION, finalNewResolution).apply();
                                Log.d(TAG, "Saved auto-adjusted resolution preference: " + finalNewResolution);
                                
                                // Show a brief toast notification
                                android.widget.Toast.makeText(MainActivity.this, 
                                    "Resolution automatically lowered for better performance", 
                                    android.widget.Toast.LENGTH_SHORT).show();
                            });
                        }
                    }
                }
            }
            
            // Schedule next check
            fpsCheckHandler.postDelayed(fpsCheckRunnable, FPS_CHECK_INTERVAL);
        };
        
        // Delay the first check to allow system to stabilize
        fpsCheckHandler.postDelayed(fpsCheckRunnable, FPS_CHECK_INTERVAL * 2);
        Log.d(TAG, "Auto resolution adjustment monitoring started");
    }
    
    @Override
    protected void onDestroy() {
        Log.i(TAG, "MainActivity onDestroy called");
        
        try {
            // Unregister receivers
            if (assetsProgressReceiver != null) {
                try { unregisterReceiver(assetsProgressReceiver); } catch (Exception ignored) {}
                assetsProgressReceiver = null;
            }
            if (assetsReadyReceiver != null) {
                try { unregisterReceiver(assetsReadyReceiver); } catch (Exception ignored) {}
                assetsReadyReceiver = null;
            }

            // Clean up FPS update handler
            if (fpsHandler != null && fpsUpdateRunnable != null) {
                fpsHandler.removeCallbacks(fpsUpdateRunnable);
                Log.d(TAG, "FPS update handler removed");
            }
            
            // Clean up FPS check handler for auto resolution
            if (fpsCheckHandler != null && fpsCheckRunnable != null) {
                fpsCheckHandler.removeCallbacks(fpsCheckRunnable);
                Log.d(TAG, "FPS check handler for auto-resolution removed");
            }
            
            // Clean up audio visualizer
            if (audioVisualizer != null) {
                audioVisualizer.setEnabled(false);
                audioVisualizer.release();
                audioVisualizer = null;
                Log.d(TAG, "AudioVisualizer released");
            }
            
            // Release renderer resources (broadcast receiver)
            if (renderer != null) {
                try { renderer.release(); } catch (Throwable t) { Log.w(TAG, "Renderer release error", t); }
            }

            // Clean up ProjectMJNI resources
            try {
                ProjectMJNI.destroy();
                Log.d(TAG, "ProjectMJNI resources destroyed");
            } catch (Exception e) {
                Log.e(TAG, "Error destroying ProjectMJNI resources", e);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy", e);
        }
        
        super.onDestroy();
    }
    
    /**
     * Get the optimal default resolution based on device performance tier
     * This replaces the auto-switching system with fixed defaults per device capability
     */
    private int getDefaultResolutionForDevice() {
        if (renderer == null) {
            Log.w(TAG, "Renderer not available, defaulting to 720p");
            return RESOLUTION_720P;
        }
        
        // Get device performance from renderer
        VisualizerRenderer.DevicePerformance performance = renderer.getDevicePerformance();
        
        switch (performance) {
            case LOW:
                Log.i(TAG, "LOW-END device detected - defaulting to 480p");
                return RESOLUTION_480P;
            case MEDIUM:
                Log.i(TAG, "MID-RANGE device detected - defaulting to 720p");
                return RESOLUTION_720P;
            case HIGH:
            case PREMIUM:
                Log.i(TAG, "HIGH-END device detected - defaulting to 1080p");
                return RESOLUTION_1080P;
            default:
                Log.w(TAG, "Unknown device performance - defaulting to 720p");
                return RESOLUTION_720P;
        }
    }

    @SuppressWarnings("deprecation")
    private String getAppVersionCompat() {
        // Pre-API 33 requires the deprecated 2-arg getPackageInfo
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                return getPackageManager().getPackageInfo(getPackageName(), PackageManager.PackageInfoFlags.of(0)).versionName;
            } else {
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get app version", e);
            return "?";
        }
    }

    private void applyImmersiveMode() {
        try {
            View decorView = getWindow().getDecorView();
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), decorView);
            if (controller == null) return;
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } catch (Exception e) {
            Log.w(TAG, "Immersive mode apply failed (no deprecated fallback used)", e);
        }
    }
}
