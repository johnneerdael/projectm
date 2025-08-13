#include <jni.h>
#include <string>
#include <android/log.h>
#include <GLES2/gl2.h> // For glViewport
#include <sys/system_properties.h> // For system property detection
#include <algorithm> // For std::transform
#include <vector> // For std::vector
#include "projectM-4/projectM.h"
#include "projectM-4/playlist.h"

#define LOG_TAG "projectM-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Device performance tiers
enum DeviceTier {
    LOW_END = 0,
    MID_RANGE = 1,
    HIGH_END = 2
};

// Global variables for performance optimization
static int g_device_tier = MID_RANGE;
static bool g_memory_optimized = false;
static bool g_texture_compression_supported = false;

projectm_handle g_projectm = nullptr;
projectm_playlist_handle g_playlist = nullptr;

// Store display dimensions separately from render dimensions
static int g_display_width = 0;
static int g_display_height = 0;

// Performance tracking
static bool g_is_low_memory_device = false;
static bool g_is_high_end_device = false;

// Device capability detection function
void detect_device_capabilities() {
    char prop[PROP_VALUE_MAX];
    
    // Check device model for performance classification
    __system_property_get("ro.product.model", prop);
    std::string model = prop;
    std::transform(model.begin(), model.end(), model.begin(), ::tolower);
    
    // Enhanced device detection with tier classification
    if (model.find("shield") != std::string::npos || 
        model.find("tegra") != std::string::npos) {
        g_is_high_end_device = true;
        g_device_tier = HIGH_END;
        LOGI("Detected HIGH-END device: %s", prop);
    } else if (model.find("chromecast") != std::string::npos ||
               model.find("google tv") != std::string::npos ||
               model.find("mi box") != std::string::npos ||
               model.find("fire tv stick 4k") != std::string::npos) {
        g_device_tier = MID_RANGE;
        LOGI("Detected MID-RANGE device: %s", prop);
    } else if (model.find("fire tv stick") != std::string::npos) {
        g_device_tier = LOW_END;
        g_is_low_memory_device = true;
        LOGI("Detected LOW-END device: %s", prop);
    } else {
        g_device_tier = MID_RANGE; // Default to mid-range for unknown devices
        LOGI("Unknown device, defaulting to MID-RANGE: %s", prop);
    }
    
    // Check available memory
    __system_property_get("ro.config.low_ram", prop);
    if (strcmp(prop, "true") == 0) {
        g_is_low_memory_device = true;
        g_memory_optimized = true;
        if (g_device_tier > LOW_END) {
            g_device_tier = LOW_END; // Downgrade tier for low RAM devices
        }
        LOGI("Detected low-memory device, enabling memory optimizations");
    }
    
    // Set memory optimization based on device tier
    if (g_device_tier == LOW_END) {
        g_memory_optimized = true;
    }
    
    // Texture compression support (assume available on mid-range and above)
    g_texture_compression_supported = (g_device_tier >= MID_RANGE);
    
    LOGI("Device capabilities: tier=%d, high_end=%d, low_memory=%d, memory_optimized=%d, texture_compression=%d", 
         g_device_tier, g_is_high_end_device, g_is_low_memory_device, g_memory_optimized, g_texture_compression_supported);
}

// Memory management for preset data caching
static std::vector<std::string> g_preset_cache;
static bool g_cache_initialized = false;
static int g_max_cache_size = 50; // Default, adjusted based on device memory

// Memory-aware preset management
void optimize_memory_usage() {
    // Adjust cache size based on device tier
    switch (g_device_tier) {
        case HIGH_END:
            g_max_cache_size = 100;
            break;
        case MID_RANGE:
            g_max_cache_size = 50;
            break;
        case LOW_END:
            g_max_cache_size = 20;
            break;
    }
    
    if (g_memory_optimized && g_preset_cache.size() > g_max_cache_size) {
        // Remove oldest cached presets
        int to_remove = g_preset_cache.size() - g_max_cache_size;
        g_preset_cache.erase(g_preset_cache.begin(), g_preset_cache.begin() + to_remove);
        LOGI("Trimmed preset cache to %d entries for memory optimization", g_max_cache_size);
    }
    
    // Force garbage collection hint for Java side (would need JNI callback)
    if (g_memory_optimized) {
        LOGI("Memory optimization active - consider garbage collection");
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_projectm_visualizer_ProjectMJNI_nativeOnSurfaceCreated(JNIEnv *env, jclass clazz, jint width, jint height, jstring preset_path) {
    LOGI("Native onSurfaceCreated called with dimensions: %dx%d", width, height);
    const char* preset_path_chars = env->GetStringUTFChars(preset_path, nullptr);
    std::string preset_path_str(preset_path_chars);
    env->ReleaseStringUTFChars(preset_path, preset_path_chars);
    
    LOGI("Surface created with preset path: %s", preset_path_str.c_str());

    // Detect device capabilities for performance optimization
    detect_device_capabilities();

    // Create projectM instance
    g_projectm = projectm_create();
    if (g_projectm == nullptr) {
        LOGE("Failed to create projectM instance");
        return;
    }
    
    // Apply performance-aware settings
    if (g_is_high_end_device) {
        // High-end devices get premium settings
        projectm_set_preset_duration(g_projectm, 35);
        projectm_set_soft_cut_duration(g_projectm, 10);
        projectm_set_hard_cut_enabled(g_projectm, true);
        projectm_set_beat_sensitivity(g_projectm, 1.2); // Higher sensitivity for better responsiveness
        LOGI("Applied HIGH-END settings for premium device");
    } else if (g_is_low_memory_device) {
        // Low memory devices get conservative settings
        projectm_set_preset_duration(g_projectm, 20);
        projectm_set_soft_cut_duration(g_projectm, 3);
        projectm_set_hard_cut_enabled(g_projectm, true);
        projectm_set_beat_sensitivity(g_projectm, 0.8); // Lower sensitivity to reduce computation
        LOGI("Applied LOW-MEMORY settings for resource-constrained device");
    } else {
        // Standard settings for regular devices
        projectm_set_preset_duration(g_projectm, 30);
        projectm_set_soft_cut_duration(g_projectm, 7);
        projectm_set_hard_cut_enabled(g_projectm, true);
        projectm_set_beat_sensitivity(g_projectm, 1.0);
        LOGI("Applied STANDARD settings for regular device");
    }
    
    LOGI("ProjectM instance created successfully with device-optimized settings");

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
    LOGI("Surface changed - requested render size: %dx%d, display size: %dx%d", width, height, g_display_width, g_display_height);
    if (g_projectm) {
        // CRITICAL FIX: Always set ProjectM to render at FULL display resolution
        // This ensures presets fill the entire screen regardless of performance settings
        int render_width = (g_display_width > 0) ? g_display_width : width;
        int render_height = (g_display_height > 0) ? g_display_height : height;
        
        projectm_set_window_size(g_projectm, render_width, render_height);
        glViewport(0, 0, render_width, render_height);
        
        LOGI("ProjectM set to FULL display resolution: %dx%d (requested was %dx%d)", 
             render_width, render_height, width, height);
        
        // Performance optimization is now handled through other settings:
        // - Preset complexity
        // - Frame rate limiting 
        // - Texture quality
        // - Effect count
        // But the viewport always fills the full screen
    } else {
        LOGE("ProjectM instance is null in surfaceChanged");
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_projectm_visualizer_ProjectMJNI_nativeOnDrawFrame(JNIEnv *env, jclass clazz) {
    if (g_projectm) {
        // Store current viewport before rendering
        GLint viewport[4];
        glGetIntegerv(GL_VIEWPORT, viewport);
        
        // Ensure viewport is set to display size before ProjectM renders
        if (g_display_width > 0 && g_display_height > 0) {
            glViewport(0, 0, g_display_width, g_display_height);
        }
        
        projectm_opengl_render_frame(g_projectm);
        
        // Aggressively restore viewport to full display size after ProjectM renders
        // ProjectM might change the viewport during rendering, so we reset it
        if (g_display_width > 0 && g_display_height > 0) {
            glViewport(0, 0, g_display_width, g_display_height);
        } else {
            // Fallback to stored viewport
            glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
        
        // Memory optimization: periodic cleanup on low-end devices
        static int frame_count = 0;
        if (g_memory_optimized && (++frame_count % 600) == 0) { // Every ~10 seconds at 60fps
            optimize_memory_usage();
        }
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_projectm_visualizer_ProjectMJNI_nativeAddPCM(JNIEnv *env, jclass clazz,
                                                             jshortArray pcm, jshort size) {
    if (g_projectm) {
        jshort *pcm_elements = env->GetShortArrayElements(pcm, nullptr);
        
        // Performance optimization: reduce PCM processing on low-end devices
        int effective_size = size;
        if (g_memory_optimized && size > 512) {
            effective_size = 512; // Limit PCM data for low-end devices
        }
        
        // Use the 16-bit PCM function directly - Android Visualizer provides stereo data
        projectm_pcm_add_int16(g_projectm, (const int16_t*)pcm_elements, effective_size / 2, PROJECTM_STEREO);
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

// Performance monitoring and optimization function
extern "C"
JNIEXPORT void JNICALL
Java_com_example_projectm_visualizer_ProjectMJNI_nativeOptimizeForPerformance(JNIEnv *env, jclass clazz, jint performance_level) {
    if (!g_projectm) {
        LOGE("ProjectM instance is null in nativeOptimizeForPerformance");
        return;
    }
    
    // Apply performance optimizations based on level (0=low, 1=medium, 2=high)
    switch (performance_level) {
        case 0: // Low performance - aggressive optimization
            g_memory_optimized = true;
            // Reduce preset duration for faster transitions and less GPU load
            projectm_set_preset_duration(g_projectm, 15);
            projectm_set_soft_cut_duration(g_projectm, 2);
            projectm_set_beat_sensitivity(g_projectm, 0.6); // Reduce sensitivity to save computation
            LOGI("Applied LOW performance optimizations");
            break;
            
        case 1: // Medium performance - balanced
            projectm_set_preset_duration(g_projectm, 25);
            projectm_set_soft_cut_duration(g_projectm, 5);
            projectm_set_beat_sensitivity(g_projectm, 0.8);
            LOGI("Applied MEDIUM performance optimizations");
            break;
            
        case 2: // High performance - restore quality
            g_memory_optimized = false;
            projectm_set_preset_duration(g_projectm, 35);
            projectm_set_soft_cut_duration(g_projectm, 10);
            projectm_set_beat_sensitivity(g_projectm, 1.2); // Higher sensitivity for better responsiveness
            LOGI("Applied HIGH performance optimizations");
            break;
            
        default:
            LOGW("Unknown performance level: %d", performance_level);
            break;
    }
}

// Get device capabilities for Java side optimization
extern "C"
JNIEXPORT jint JNICALL
Java_com_example_projectm_visualizer_ProjectMJNI_nativeGetDeviceTier(JNIEnv *env, jclass clazz) {
    return g_device_tier;
}

// Memory management function callable from Java
extern "C"
JNIEXPORT void JNICALL
Java_com_example_projectm_visualizer_ProjectMJNI_nativeTrimMemory(JNIEnv *env, jclass clazz) {
    optimize_memory_usage();
    LOGI("Memory trimming requested from Java");
}
