#include <jni.h>
#include <string>
#include <android/log.h>
#include <GLES2/gl2.h> // For glViewport
#include "projectM-4/projectM.h"
#include "projectM-4/playlist.h"

#define LOG_TAG "projectM-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

projectm_handle g_projectm = nullptr;
projectm_playlist_handle g_playlist = nullptr;

// Store display dimensions separately from render dimensions
static int g_display_width = 0;
static int g_display_height = 0;

extern "C"
JNIEXPORT void JNICALL
Java_com_example_projectm_visualizer_ProjectMJNI_nativeOnSurfaceCreated(JNIEnv *env, jclass clazz, jint width, jint height, jstring preset_path) {
    LOGI("Native onSurfaceCreated called with dimensions: %dx%d", width, height);
    const char* preset_path_chars = env->GetStringUTFChars(preset_path, nullptr);
    std::string preset_path_str(preset_path_chars);
    env->ReleaseStringUTFChars(preset_path, preset_path_chars);
    
    LOGI("Surface created with preset path: %s", preset_path_str.c_str());

    // Create projectM instance
    g_projectm = projectm_create();
    if (g_projectm == nullptr) {
        LOGE("Failed to create projectM instance");
        return;
    }
    
    // Enhanced settings for better visuals
    projectm_set_preset_duration(g_projectm, 30); // 30 seconds default preset duration
    projectm_set_soft_cut_duration(g_projectm, 7); // 7 seconds smooth transition
    projectm_set_hard_cut_enabled(g_projectm, true); // Enable hard cuts for manual transitions
    projectm_set_beat_sensitivity(g_projectm, 1.0); // Default beat sensitivity
    
    LOGI("ProjectM instance created successfully with enhanced settings");

    // Create playlist and connect it to projectM
    g_playlist = projectm_playlist_create(g_projectm);
    if (g_playlist == nullptr) {
        LOGE("Failed to create playlist");
        return;
    }
    LOGI("Playlist created successfully");

    // Enable shuffle
    projectm_playlist_set_shuffle(g_playlist, true);
    LOGI("Shuffle enabled");
    
    // Add preset directory recursively, allowing duplicates
    bool result = projectm_playlist_add_path(g_playlist, preset_path_str.c_str(), true, false);
    LOGI("Add preset path result: %s", result ? "SUCCESS" : "FAILED");
    
    // Get playlist size to verify presets were loaded
    size_t preset_count = projectm_playlist_size(g_playlist);
    LOGI("Loaded %zu presets from path", preset_count);
    
    if (preset_count > 0) {
        // Play the first preset
        projectm_playlist_play_next(g_playlist, true);
        LOGI("Playing first preset");
    } else {
        LOGE("No presets loaded - playlist is empty");
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_projectm_visualizer_ProjectMJNI_nativeOnSurfaceChanged(JNIEnv *env, jclass clazz,
                                                                        jint width, jint height) {
    LOGI("Surface changed - render size: %dx%d, display size: %dx%d", width, height, g_display_width, g_display_height);
    if (g_projectm) {
        // Set ProjectM internal rendering resolution
        projectm_set_window_size(g_projectm, width, height);
        LOGI("ProjectM window size set to %dx%d", width, height);
        
        // Immediately restore viewport to full display size
        // This prevents ProjectM from shrinking the viewport to match render size
        if (g_display_width > 0 && g_display_height > 0) {
            glViewport(0, 0, g_display_width, g_display_height);
            LOGI("Restored viewport to full display: %dx%d", g_display_width, g_display_height);
        }
    } else {
        LOGE("ProjectM instance is null in surfaceChanged");
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_projectm_visualizer_ProjectMJNI_nativeOnDrawFrame(JNIEnv *env, jclass clazz) {
    if (g_projectm) {
        projectm_opengl_render_frame(g_projectm);
        
        // Immediately restore viewport to full display size after ProjectM renders
        // ProjectM might change the viewport during rendering, so we reset it
        if (g_display_width > 0 && g_display_height > 0) {
            glViewport(0, 0, g_display_width, g_display_height);
            LOGI("Restored viewport after ProjectM render to %dx%d", g_display_width, g_display_height);
        }
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_projectm_visualizer_ProjectMJNI_nativeAddPCM(JNIEnv *env, jclass clazz,
                                                             jshortArray pcm, jshort size) {
    if (g_projectm) {
        jshort *pcm_elements = env->GetShortArrayElements(pcm, nullptr);
        // Use the 16-bit PCM function directly - Android Visualizer provides stereo data
        projectm_pcm_add_int16(g_projectm, (const int16_t*)pcm_elements, size / 2, PROJECTM_STEREO);
        env->ReleaseShortArrayElements(pcm, pcm_elements, JNI_ABORT);
    } else {
        LOGE("ProjectM instance is null in addPCM");
    }
}

#include <cstdlib>
#include <ctime>

// Helper function to select a random preset
void select_random_preset(bool hard_cut) {
    if (!g_playlist) {
        LOGE("Playlist is null in select_random_preset");
        return;
    }
    
    size_t preset_count = projectm_playlist_size(g_playlist);
    if (preset_count <= 0) {
        LOGE("No presets available for random selection");
        return;
    }
    
    // Get the current position to avoid selecting it again
    size_t current_position = projectm_playlist_get_position(g_playlist);
    size_t new_position;
    
    // If there's only one preset, we have no choice
    if (preset_count == 1) {
        new_position = 0;
    } else {
        // Select a random position different from the current one
        do {
            new_position = static_cast<size_t>(rand() % preset_count);
        } while (new_position == current_position && preset_count > 1);
    }
    
    LOGI("Selecting random preset: %zu of %zu", new_position, preset_count);
    projectm_playlist_set_position(g_playlist, new_position, hard_cut);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_projectm_visualizer_ProjectMJNI_nativeNextPreset(JNIEnv *env, jclass clazz, jboolean hard_cut) {
    if (g_playlist) {
        projectm_playlist_play_next(g_playlist, hard_cut);
        LOGI("Selected next preset (hard_cut: %s)", hard_cut ? "true" : "false");
    } else {
        LOGE("Playlist is null in selectNextPreset");
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_projectm_visualizer_ProjectMJNI_nativePreviousPreset(JNIEnv *env, jclass clazz, jboolean hard_cut) {
    if (g_playlist) {
        projectm_playlist_play_previous(g_playlist, hard_cut);
        LOGI("Selected previous preset (hard_cut: %s)", hard_cut ? "true" : "false");
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_projectm_visualizer_ProjectMJNI_nativeSelectRandomPreset(JNIEnv *env, jclass clazz, jboolean hard_cut) {
    // Initialize random seed if needed
    static bool seeded = false;
    if (!seeded) {
        srand(static_cast<unsigned int>(time(nullptr)));
        seeded = true;
    }
    
    select_random_preset(hard_cut);
    LOGI("Selected random preset (hard_cut: %s)", hard_cut ? "true" : "false");
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_projectm_visualizer_ProjectMJNI_nativeGetCurrentPresetName(JNIEnv *env, jclass clazz) {
    if (!g_playlist) {
        return env->NewStringUTF("No playlist available");
    }
    
    size_t position = projectm_playlist_get_position(g_playlist);
    char* name = projectm_playlist_item(g_playlist, position);
    
    if (name) {
        jstring result = env->NewStringUTF(name);
        // Free the string allocated by the library
        free(name);
        return result;
    } else {
        return env->NewStringUTF("Unknown preset");
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_projectm_visualizer_ProjectMJNI_nativeSetPresetDuration(JNIEnv *env, jclass clazz, jint seconds) {
    if (g_projectm) {
        projectm_set_preset_duration(g_projectm, seconds);
        LOGI("Preset duration set to %d seconds", seconds);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_projectm_visualizer_ProjectMJNI_nativeSetSoftCutDuration(JNIEnv *env, jclass clazz, jint seconds) {
    if (g_projectm) {
        projectm_set_soft_cut_duration(g_projectm, seconds);
        LOGI("Soft cut duration set to %d seconds", seconds);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_projectm_visualizer_ProjectMJNI_nativeDestroy(JNIEnv *env, jclass clazz) {
    if (g_playlist) {
        projectm_playlist_destroy(g_playlist);
        g_playlist = nullptr;
    }
    if (g_projectm) {
        projectm_destroy(g_projectm);
        g_projectm = nullptr;
    }
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_projectm_visualizer_ProjectMJNI_nativeGetVersion(JNIEnv *env, jclass clazz) {
    // Check if projectM API has a version function
    if (g_projectm) {
        // In projectM-4, we can use various info functions if available
        // This is an example - actual implementation depends on projectM API
        return env->NewStringUTF("ProjectM-4 Android TV Edition 1.5");
    }
    return env->NewStringUTF("ProjectM-4");
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_projectm_visualizer_ProjectMJNI_nativeGetPresetCount(JNIEnv *env, jclass clazz) {
    if (g_playlist) {
        size_t count = projectm_playlist_size(g_playlist);
        return static_cast<jint>(count);
    }
    return 0;
}

// Implementation of the native viewport setting
extern "C"
JNIEXPORT void JNICALL
Java_com_example_projectm_visualizer_ProjectMJNI_nativeSetViewport(JNIEnv *env, jclass clazz, 
                                                                  jint x, jint y, jint width, jint height) {
    // Store display dimensions for later use
    g_display_width = width;
    g_display_height = height;
    
    // Set OpenGL viewport directly - this ensures the visualization is stretched to fit the full screen
    glViewport(x, y, width, height);
    LOGI("Native setViewport called and stored: %d,%d %dx%d", x, y, width, height);
}
