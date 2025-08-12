package com.example.projectm.visualizer;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.media.audiofx.Visualizer;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
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

    private VisualizerView visualizerView;
    private VisualizerRenderer renderer;
    private Visualizer audioVisualizer;
    
    // UI components
    private View overlayMenu;
    private TextView presetNameText;
    private Switch autoChangeSwitch;
    private SeekBar presetDurationSeekBar;
    private TextView presetDurationText;
    private TextView helpText;
    
    // For handling auto-hide
    private Handler autoHideHandler = new Handler();
    private Runnable autoHideRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            Log.i(TAG, "==================================");
            Log.i(TAG, "MainActivity onCreate started");
            Log.i(TAG, "==================================");
            
            // Log device info
            Log.i(TAG, "Device info: " + android.os.Build.MANUFACTURER + " " + 
                  android.os.Build.MODEL + ", Android " + android.os.Build.VERSION.RELEASE);
            Log.i(TAG, "App version: " + getPackageManager().getPackageInfo(getPackageName(), 0).versionName);

            // Set up full screen activity
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            Log.d(TAG, "Window flags set for fullscreen and screen-on");

            // Copy presets to device storage
            copyAssetsToStorage();

            // Log available storage
            File cacheDir = getCacheDir();
            File filesDir = getFilesDir();
            Log.d(TAG, "Cache directory: " + cacheDir.getAbsolutePath() + 
                  " (free: " + cacheDir.getFreeSpace()/1024/1024 + "MB)");
            Log.d(TAG, "Files directory: " + filesDir.getAbsolutePath() + 
                  " (free: " + filesDir.getFreeSpace()/1024/1024 + "MB)");

            // Don't create the view manually - just use the one in the layout
            setContentView(R.layout.activity_main);
            Log.d(TAG, "Layout set successfully");
            
            // Get the VisualizerView from the layout
            visualizerView = findViewById(R.id.visualizer_view);
            if (visualizerView == null) {
                Log.e(TAG, "Failed to find VisualizerView in layout");
                finish();
                return;
            }
            Log.d(TAG, "Found VisualizerView in layout");
            
            // Initialize renderer with asset path
            // Use cache directory where ProjectMApplication extracts the assets
            File presetPath = new File(getCacheDir(), "projectM");
            Log.d(TAG, "Using presetPath: " + presetPath.getAbsolutePath());
            
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
            
            try {
                VisualizerRenderer renderer = new VisualizerRenderer(presetPath.getAbsolutePath());
                Log.i(TAG, "VisualizerRenderer object created successfully");
                
                // Set renderer on view
                visualizerView.setRenderer(renderer);
                Log.i(TAG, "Renderer attached to VisualizerView");
                
                // Store renderer reference
                this.renderer = renderer;
                Log.i(TAG, "VisualizerRenderer creation complete");
                
                // Try getting the current preset name to verify initialization
                try {
                    String presetName = renderer.getCurrentPresetName();
                    Log.i(TAG, "Initial preset: " + (presetName != null ? presetName : "NULL"));
                } catch (Exception e) {
                    Log.e(TAG, "Error getting initial preset name", e);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to create or attach VisualizerRenderer", e);
            }
            
            // Initialize the UI components
            initUI();
            
            // Log important objects status
            Log.i(TAG, "Key objects state check:");
            Log.i(TAG, "VisualizerView: " + (visualizerView != null ? "initialized" : "NULL"));
            Log.i(TAG, "Renderer: " + (renderer != null ? "initialized" : "NULL"));
            
            // Verify files in the preset directory one more time
            File presetsDir = new File(getCacheDir(), "projectM/presets");
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
            new Handler().postDelayed(() -> {
                if (visualizerView != null && renderer != null) {
                    Log.i(TAG, "Requesting render after delay");
                    visualizerView.requestRender();
                    
                    // Try to start a random preset
                    try {
                        renderer.randomPreset();
                        String preset = renderer.getCurrentPresetName();
                        Log.i(TAG, "Started preset: " + preset);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to start preset", e);
                    }
                }
            }, 1000); // 1 second delay
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

    private void initUI() {
        try {
            Log.d(TAG, "Initializing UI components");
            
            // Find overlay components
            overlayMenu = findViewById(R.id.overlay_menu);
            presetNameText = findViewById(R.id.preset_name);
            autoChangeSwitch = findViewById(R.id.auto_change_switch);
            presetDurationSeekBar = findViewById(R.id.preset_duration_seekbar);
            presetDurationText = findViewById(R.id.preset_duration_text);
            helpText = findViewById(R.id.help_text);
            
            // Log UI component status
            Log.d(TAG, "UI components found: " + 
                  "overlay=" + (overlayMenu != null) + 
                  ", presetText=" + (presetNameText != null) + 
                  ", autoSwitch=" + (autoChangeSwitch != null) + 
                  ", durationBar=" + (presetDurationSeekBar != null) + 
                  ", durationText=" + (presetDurationText != null) + 
                  ", helpText=" + (helpText != null));
            
            // Set initial values
            if (renderer != null) {
                Log.d(TAG, "Setting initial UI values from renderer");
                autoChangeSwitch.setChecked(renderer.isAutoChangeEnabled());
                presetDurationSeekBar.setProgress(30); // Default 30 seconds
                
                // Display version information
                TextView versionText = findViewById(R.id.version_info);
                if (versionText != null) {
                    try {
                        String appVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                        String projectMVersion = ProjectMJNI.getVersion();
                        versionText.setText("App v" + appVersion + " | ProjectM: " + projectMVersion);
                    } catch (Exception e) {
                        Log.e(TAG, "Error getting version info", e);
                        versionText.setText("Version info unavailable");
                    }
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
                    renderer.previousPreset();
                    updatePresetName();
                }
                resetAutoHideTimer();
            });
            
            randomButton.setOnClickListener(v -> {
                if (renderer != null) {
                    renderer.randomPreset();
                    updatePresetName();
                }
                resetAutoHideTimer();
            });
            
            nextButton.setOnClickListener(v -> {
                if (renderer != null) {
                    renderer.nextPreset();
                    updatePresetName();
                }
                resetAutoHideTimer();
            });
            
            // Set up auto change switch
            autoChangeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (renderer != null) {
                    renderer.setAutoChange(isChecked);
                }
                resetAutoHideTimer();
            });
            
            // Set up seekbar for preset duration
            presetDurationSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int duration = Math.max(5, progress); // Minimum 5 seconds
                    presetDurationText.setText(duration + "s");
                    resetAutoHideTimer();
                }
                
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    // Not used
                }
                
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    int duration = Math.max(5, seekBar.getProgress()); // Minimum 5 seconds
                    if (renderer != null) {
                        renderer.setPresetDuration(duration);
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
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing UI: " + e.getMessage(), e);
        }
    }
    
    private void toggleOverlay() {
        if (overlayMenu.getVisibility() == View.VISIBLE) {
            // Hide overlay
            overlayMenu.setVisibility(View.GONE);
            helpText.setVisibility(View.VISIBLE);
            autoHideHandler.removeCallbacks(autoHideRunnable);
        } else {
            // Show overlay
            overlayMenu.setVisibility(View.VISIBLE);
            helpText.setVisibility(View.GONE);
            updatePresetName();
            resetAutoHideTimer();
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
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                    Log.d(TAG, "Next preset requested");
                    if (renderer != null) {
                        renderer.nextPreset();
                        if (overlayMenu.getVisibility() == View.VISIBLE) {
                            updatePresetName();
                            resetAutoHideTimer();
                        }
                    }
                    return true;
                
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                    Log.d(TAG, "Previous preset requested");
                    if (renderer != null) {
                        renderer.previousPreset();
                        if (overlayMenu.getVisibility() == View.VISIBLE) {
                            updatePresetName();
                            resetAutoHideTimer();
                        }
                    }
                    return true;
                
                case KeyEvent.KEYCODE_MENU:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    Log.d(TAG, "Menu/select key pressed");
                    toggleOverlay();
                    return true;
                
                case KeyEvent.KEYCODE_BACK:
                    if (overlayMenu.getVisibility() == View.VISIBLE) {
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
            Log.d(TAG, "Initializing audio visualizer for system audio...");
            
            // Create visualizer with session ID 0 (system audio output)
            audioVisualizer = new Visualizer(0);
            
            if (audioVisualizer == null) {
                Log.e(TAG, "Failed to create Visualizer instance");
                return;
            }

            // Get capture size
            int[] captureSizeRange = Visualizer.getCaptureSizeRange();
            int captureSize = captureSizeRange[1]; // Use maximum
            Log.d(TAG, "Capture size range: " + captureSizeRange[0] + " - " + captureSizeRange[1]);
            
            audioVisualizer.setCaptureSize(captureSize);
            
            audioVisualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {
                    try {
                        if (waveform == null || waveform.length == 0) {
                            return;
                        }
                        
                        short[] pcm = new short[waveform.length / 2];
                        for (int i = 0; i < pcm.length; i++) {
                            pcm[i] = (short) ((waveform[2 * i] & 0xFF) | (waveform[2 * i + 1] << 8));
                        }
                        
                        if (visualizerView != null && visualizerView.getRenderer() != null) {
                            visualizerView.getRenderer().addPCMData(pcm, pcm.length);
                            visualizerView.requestRender();
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

            int result = audioVisualizer.setEnabled(true);
            if (result == Visualizer.SUCCESS) {
                Log.i(TAG, "Audio visualizer enabled successfully");
            } else {
                Log.e(TAG, "Failed to enable audio visualizer, status code: " + result);
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
                visualizerView.onPause();
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
                Log.d(TAG, "Resuming VisualizerView");
                visualizerView.onResume();
                // Request render to ensure we're displaying visuals
                visualizerView.requestRender();
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
                new Handler().postDelayed(() -> {
                    if (visualizerView != null) {
                        Log.d(TAG, "Delayed request for render");
                        visualizerView.requestRender();
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
                
                // For Android TV, we need to ensure the system UI is hidden for immersive experience
                View decorView = getWindow().getDecorView();
                int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                decorView.setSystemUiVisibility(flags);
                
                // Request render to ensure visualization is displayed
                if (visualizerView != null) {
                    Log.d(TAG, "Requesting render due to window focus gain");
                    visualizerView.requestRender();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling window focus change", e);
        }
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "MainActivity onDestroy called");
        
        try {
            // Clean up audio visualizer
            if (audioVisualizer != null) {
                audioVisualizer.setEnabled(false);
                audioVisualizer.release();
                audioVisualizer = null;
                Log.d(TAG, "AudioVisualizer released");
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
}
