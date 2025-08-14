#include <jni.h>
#include <string>
#include <android/log.h>
#include <GLES2/gl2.h> // For glViewport
#include <GLES2/gl2ext.h> // For FBO extensions
#ifdef __ANDROID__
#include <GLES3/gl3.h>
#include <EGL/egl.h>
#endif
#include <sys/system_properties.h> // For system property detection
#include <algorithm> // For std::transform
#include <vector> // For std::vector
#include <math.h> // For sine wave test audio
#include "projectM-4/projectM.h"
#include "projectM-4/playlist.h"
// New API from integrated projectM source to keep external FBO bound
extern "C" void projectm_set_respect_external_framebuffer(int enable);

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

// FBO-based performance mode globals
// Performance mode (FBO based). Default ON for anything not clearly HIGH_END so we actually exercise it.
static bool g_performance_mode = true;
static GLuint g_fbo = 0;
static GLuint g_fbo_texture = 0;
static int g_render_width = 1920;
static int g_render_height = 1080;
static int g_target_fps = 60;

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

// FBO management for performance mode
void create_performance_fbo(int width, int height) {
    if (g_fbo != 0) {
        // Clean up existing FBO
        glDeleteFramebuffers(1, &g_fbo);
        glDeleteTextures(1, &g_fbo_texture);
    }
    
    // Create FBO texture
    glGenTextures(1, &g_fbo_texture);
    glBindTexture(GL_TEXTURE_2D, g_fbo_texture);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    
    // Create FBO
    glGenFramebuffers(1, &g_fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, g_fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, g_fbo_texture, 0);
    
    GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    if (status != GL_FRAMEBUFFER_COMPLETE) {
        LOGE("FBO creation failed with status: 0x%x", status);
        glDeleteFramebuffers(1, &g_fbo);
        glDeleteTextures(1, &g_fbo_texture);
        g_fbo = 0;
        g_fbo_texture = 0;
        g_performance_mode = false;
    } else {
        LOGI("Created performance FBO %dx%d", width, height);
    }
    
    // Restore default framebuffer
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
}

void cleanup_performance_fbo() {
    if (g_fbo != 0) {
        glDeleteFramebuffers(1, &g_fbo);
        glDeleteTextures(1, &g_fbo_texture);
        g_fbo = 0;
        g_fbo_texture = 0;
        LOGI("Cleaned up performance FBO");
    }
}

// Shader system for FBO upscaling
static GLuint g_upscale_program = 0;
static GLuint g_upscale_vao = 0;
static GLuint g_upscale_vbo = 0;
static bool g_has_es3 = false;
static bool g_blit_initialized = false; // track if we logged blit usage
// Function pointer for optional ES3 blit (avoid link-time dependency)
typedef void (GL_APIENTRYP PFNGLBLITFRAMEBUFFERPROC)(GLint, GLint, GLint, GLint, GLint, GLint, GLint, GLint, GLbitfield, GLenum);
static PFNGLBLITFRAMEBUFFERPROC p_glBlitFramebuffer = nullptr;
// Discard / invalidate support
static bool g_has_discard_ext = false;        // GL_EXT_discard_framebuffer
static bool g_discard_logged = false;         // one-time log when using discard
static bool g_blit_error_logged = false;      // prevent spam if blit errors
static bool g_disable_blit_fastpath = true;  // start disabled; can enable after validation
static bool g_debug_inject_pattern = true;   // temporary debug aid: fill FBO if content missing
static int  g_fbo_rebind_events = 0;         // count how often projectM escapes our FBO
static const int kRebindDisableThreshold = 120; // after ~2 seconds at 60fps, abandon perf mode
// Function pointer for discard extension (loaded at runtime)
#ifndef PFNGLDISCARDFRAMEBUFFEREXTPROC
typedef void (GL_APIENTRYP PFNGLDISCARDFRAMEBUFFEREXTPROC) (GLenum target, GLsizei numAttachments, const GLenum *attachments);
#endif
static PFNGLDISCARDFRAMEBUFFEREXTPROC p_glDiscardFramebufferEXT = nullptr;
// ES3 invalidate function pointer (avoid direct link requirement)
static PFNGLINVALIDATEFRAMEBUFFERPROC p_glInvalidateFramebuffer = nullptr;

const char* vertex_shader_source = R"(
#version 100
attribute vec2 a_position;
attribute vec2 a_texcoord;
varying vec2 v_texcoord;
void main() {
    gl_Position = vec4(a_position, 0.0, 1.0);
    v_texcoord = a_texcoord;
}
)";

const char* fragment_shader_source = R"(
#version 100
precision mediump float;
precision mediump sampler2D;
uniform sampler2D u_texture;
varying vec2 v_texcoord;
void main() {
    gl_FragColor = texture2D(u_texture, v_texcoord);
}
)";

GLuint compile_shader(GLenum type, const char* source) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &source, NULL);
    glCompileShader(shader);
    
    GLint compiled;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (!compiled) {
        GLint length;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &length);
        char* log = new char[length];
        glGetShaderInfoLog(shader, length, NULL, log);
        LOGE("Shader compilation failed: %s", log);
        delete[] log;
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

void create_upscale_shader() {
    if (g_upscale_program != 0) {
        glDeleteProgram(g_upscale_program);
        g_upscale_program = 0;
    }

    GLuint vertex_shader = compile_shader(GL_VERTEX_SHADER, vertex_shader_source);
    GLuint fragment_shader = compile_shader(GL_FRAGMENT_SHADER, fragment_shader_source);

    if (vertex_shader == 0 || fragment_shader == 0) {
        LOGE("Failed to compile upscale shaders");
        return;
    }

    g_upscale_program = glCreateProgram();
    glAttachShader(g_upscale_program, vertex_shader);
    glAttachShader(g_upscale_program, fragment_shader);
    glBindAttribLocation(g_upscale_program, 0, "a_position");
    glBindAttribLocation(g_upscale_program, 1, "a_texcoord");
    glLinkProgram(g_upscale_program);
    
    GLint linked;
    glGetProgramiv(g_upscale_program, GL_LINK_STATUS, &linked);
    if (!linked) {
        GLint length;
        glGetProgramiv(g_upscale_program, GL_INFO_LOG_LENGTH, &length);
        char* log = new char[length];
        glGetProgramInfoLog(g_upscale_program, length, NULL, log);
        LOGE("Shader linking failed: %s", log);
        delete[] log;
        glDeleteProgram(g_upscale_program);
        g_upscale_program = 0;
        return;
    }
    
    glDeleteShader(vertex_shader);
    glDeleteShader(fragment_shader);
    
    // Create fullscreen quad
    float quad_vertices[] = {
        // positions   // texcoords
        -1.0f, -1.0f,  0.0f, 0.0f,
         1.0f, -1.0f,  1.0f, 0.0f,
        -1.0f,  1.0f,  0.0f, 1.0f,
         1.0f,  1.0f,  1.0f, 1.0f,
    };
    
    glGenBuffers(1, &g_upscale_vbo);
    glBindBuffer(GL_ARRAY_BUFFER, g_upscale_vbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof(quad_vertices), quad_vertices, GL_STATIC_DRAW);
    
    LOGI("Created upscale shader program");
}

void render_fbo_to_screen() {
    if (g_fbo_texture == 0) {
        LOGE("Cannot render FBO to screen - missing texture");
        return;
    }

    // Guard: skip until we have a valid surface size
    if (g_display_width <= 0 || g_display_height <= 0) {
        return;
    }

    // ES3 fast path: blit (saves shader + attribute setup). We need READ/DRAW framebuffers.
    #if defined(GL_READ_FRAMEBUFFER) && defined(GL_DRAW_FRAMEBUFFER)
    if (!g_disable_blit_fastpath && g_has_es3 && p_glBlitFramebuffer) {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, g_fbo);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_STENCIL_TEST);
        glDisable(GL_BLEND);
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_CULL_FACE);
        p_glBlitFramebuffer(0, 0, g_render_width, g_render_height,
                            0, 0, g_display_width, g_display_height,
                            GL_COLOR_BUFFER_BIT, GL_LINEAR);
        GLenum blitErr = glGetError();
        if (!g_blit_initialized) {
            LOGI("Using ES3 blit fast path for upscale (err=0x%x)", blitErr);
            g_blit_initialized = true;
        }
        if (blitErr != GL_NO_ERROR && !g_blit_error_logged) {
            LOGW("Blit GL error=0x%x; disabling blit fast path", blitErr);
            g_blit_error_logged = true;
            g_disable_blit_fastpath = true;
        }
        // Optional post-blit discard of FBO color (content already resolved)
        if ((g_has_es3 || g_has_discard_ext) && g_fbo != 0) {
            glBindFramebuffer(GL_FRAMEBUFFER, g_fbo);
            const GLenum attachments[] = { GL_COLOR_ATTACHMENT0 };
            if (g_has_es3 && p_glInvalidateFramebuffer) {
                p_glInvalidateFramebuffer(GL_FRAMEBUFFER, 1, attachments);
            } else if (g_has_discard_ext && p_glDiscardFramebufferEXT) {
                // Extension path (ES2)
                p_glDiscardFramebufferEXT(GL_FRAMEBUFFER, 1, attachments);
            }
            if (!g_discard_logged) {
                LOGI("Framebuffer color attachment discarded after upscale (mode=%s)", g_has_es3 ? "ES3_invalidate" : "EXT_discard");
                g_discard_logged = true;
            }
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
        }
        return;
    }
    #endif

    // ES2 shader fallback
    if (g_upscale_program == 0) {
        LOGE("Upscale shader program missing in ES2 fallback");
        return;
    }

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glViewport(0, 0, g_display_width, g_display_height);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_STENCIL_TEST);
    glDisable(GL_BLEND);
    glDisable(GL_SCISSOR_TEST);
    glDisable(GL_CULL_FACE);
    glUseProgram(g_upscale_program);
    static GLint uTexLoc = -1;
    if (uTexLoc == -1) uTexLoc = glGetUniformLocation(g_upscale_program, "u_texture");
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, g_fbo_texture);
    glUniform1i(uTexLoc, 0);
    glBindBuffer(GL_ARRAY_BUFFER, g_upscale_vbo);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), (void*)0);
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), (void*)(2 * sizeof(float)));
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    glDisableVertexAttribArray(0);
    glDisableVertexAttribArray(1);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glUseProgram(0);

    // Post-upscale discard for ES2 path (must re-bind FBO once we've sampled its texture)
    if ((g_has_es3 || g_has_discard_ext) && g_fbo != 0) {
        glBindFramebuffer(GL_FRAMEBUFFER, g_fbo);
        const GLenum attachments[] = { GL_COLOR_ATTACHMENT0 };
        if (g_has_es3 && p_glInvalidateFramebuffer) {
            p_glInvalidateFramebuffer(GL_FRAMEBUFFER, 1, attachments);
        } else if (g_has_discard_ext && p_glDiscardFramebufferEXT) {
            p_glDiscardFramebufferEXT(GL_FRAMEBUFFER, 1, attachments);
        }
        if (!g_discard_logged) {
            LOGI("Framebuffer color attachment discarded after upscale (mode=%s)", g_has_es3 ? "ES3_invalidate" : "EXT_discard");
            g_discard_logged = true;
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }
}

void update_performance_settings() {
    if (g_performance_mode) {
        // Use reduced resolution for rendering
        switch (g_device_tier) {
            case LOW_END:
                g_render_width = 854;
                g_render_height = 480;
                break;
            case MID_RANGE:
                g_render_width = 1280;
                g_render_height = 720;
                break;
            case HIGH_END:
                g_render_width = 1600;
                g_render_height = 900;
                break;
        }
        LOGI("Performance mode: rendering at %dx%d", g_render_width, g_render_height);
    } else {
        // Use full resolution
        g_render_width = g_display_width;
        g_render_height = g_display_height;
        LOGI("Quality mode: rendering at full %dx%d", g_render_width, g_render_height);
    }
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

    // Enable respecting externally bound FBO (our performance mode offscreen target)
    projectm_set_respect_external_framebuffer(1);
    
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

    // Disable dithering (bandwidth / perf win on most GPUs) once after context creation
    glDisable(GL_DITHER);

    // Detect GL version for ES3 features
    const char* glver = (const char*)glGetString(GL_VERSION);
    if (glver) {
        g_has_es3 = (strstr(glver, "OpenGL ES 3") != nullptr);
        LOGI("GL version string: %s (ES3=%d)", glver, g_has_es3 ? 1 : 0);
        if (g_has_es3) {
            p_glBlitFramebuffer = (PFNGLBLITFRAMEBUFFERPROC)eglGetProcAddress("glBlitFramebuffer");
            if (!p_glBlitFramebuffer) {
                p_glBlitFramebuffer = (PFNGLBLITFRAMEBUFFERPROC)eglGetProcAddress("glBlitFramebufferEXT");
            }
            p_glInvalidateFramebuffer = (PFNGLINVALIDATEFRAMEBUFFERPROC)eglGetProcAddress("glInvalidateFramebuffer");
            LOGI("ES3 blit proc %s", p_glBlitFramebuffer ? "resolved" : "NOT FOUND - fallback to shader");
            LOGI("ES3 invalidate proc %s", p_glInvalidateFramebuffer ? "resolved" : "NOT FOUND - will skip invalidate");
        }
        // Extension string parsing for discard capability when not ES3
        const char* extensions = (const char*)glGetString(GL_EXTENSIONS);
        if (extensions && strstr(extensions, "GL_EXT_discard_framebuffer")) {
            g_has_discard_ext = true;
            // Attempt to load function pointer
            p_glDiscardFramebufferEXT = (PFNGLDISCARDFRAMEBUFFEREXTPROC)eglGetProcAddress("glDiscardFramebufferEXT");
            LOGI("Detected GL_EXT_discard_framebuffer support (proc=%s)", p_glDiscardFramebufferEXT ? "resolved" : "MISSING");
        }
    }

    // Auto performance mode heuristic: disable for explicit HIGH_END
    if (g_is_high_end_device) {
        g_performance_mode = false;
    }

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
    LOGI("Surface changed - display size: %dx%d", width, height);
    
    // Store display dimensions
    g_display_width = width;
    g_display_height = height;
    
    if (!g_projectm) {
        LOGE("ProjectM instance is null in surfaceChanged");
        return;
    }

    update_performance_settings();

    if (g_performance_mode && g_upscale_program == 0) {
        create_upscale_shader();
    }

    if (g_performance_mode) {
        create_performance_fbo(g_render_width, g_render_height);
    } else {
        cleanup_performance_fbo();
    }

    projectm_set_window_size(g_projectm, g_render_width, g_render_height);
    LOGI("ProjectM configured: render=%dx%d display=%dx%d perf=%s FBO=%s es3=%d blit=%s discard=%s", 
         g_render_width, g_render_height, width, height,
         g_performance_mode ? "ON" : "OFF",
         (g_performance_mode && g_fbo != 0) ? "ACTIVE" : "DISABLED",
         g_has_es3 ? 1 : 0,
         (!g_disable_blit_fastpath && g_has_es3 && p_glBlitFramebuffer) ? "ON" : "OFF",
         ( (g_has_es3 && p_glInvalidateFramebuffer) || (g_has_discard_ext && p_glDiscardFramebufferEXT) ) ? "ON" : "OFF");
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_projectm_visualizer_ProjectMJNI_nativeOnDrawFrame(JNIEnv *env, jclass clazz) {
    if (!g_projectm) return;
    if (g_display_width <= 0 || g_display_height <= 0) return; // wait for valid surface

    if (g_performance_mode && g_fbo != 0) {
        glBindFramebuffer(GL_FRAMEBUFFER, g_fbo);
        glViewport(0, 0, g_render_width, g_render_height);
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT); // no depth clear (no depth attachment)
        GLint fbBefore = 0; glGetIntegerv(GL_FRAMEBUFFER_BINDING, &fbBefore);
        projectm_opengl_render_frame(g_projectm);
        GLint fbAfter = 0; glGetIntegerv(GL_FRAMEBUFFER_BINDING, &fbAfter);
        if (fbAfter != (GLint)g_fbo) {
            LOGW("projectM changed framebuffer binding (before=%d after=%d expected=%u) - restoring", fbBefore, fbAfter, g_fbo);
            glBindFramebuffer(GL_FRAMEBUFFER, g_fbo);
            g_fbo_rebind_events++;
            // Attempt a SECOND render pass to actually get content into our FBO.
            // This doubles work only on frames where projectM escaped.
            projectm_opengl_render_frame(g_projectm);
            GLint fbAfterSecond = 0; glGetIntegerv(GL_FRAMEBUFFER_BINDING, &fbAfterSecond);
            if (fbAfterSecond != (GLint)g_fbo) {
                // Still escaped; optionally inject a debug pattern so we at least see something and know upscale path works
                if (g_debug_inject_pattern) {
                    glBindFramebuffer(GL_FRAMEBUFFER, g_fbo);
                    // Simple 2-color vertical bars pattern using glClear + scissor
                    glDisable(GL_SCISSOR_TEST);
                    glClearColor(1.0f, 0.0f, 1.0f, 1.0f); // magenta
                    glClear(GL_COLOR_BUFFER_BIT);
                }
            }
            if (g_fbo_rebind_events == 10) {
                LOGW("projectM is frequently unbinding our FBO (10 events) - performance benefit may be negated");
            }
            if (g_fbo_rebind_events >= kRebindDisableThreshold) {
                LOGW("Disabling performance mode automatically after %d FBO escapes", g_fbo_rebind_events);
                g_performance_mode = false;
                // Fall back: cleanup FBO so subsequent frames go direct
                cleanup_performance_fbo();
                // Reconfigure projectM to full size
                projectm_set_window_size(g_projectm, g_display_width, g_display_height);
            }
        }
        if (g_performance_mode) {
            render_fbo_to_screen();
        } else {
            // We disabled perf mode this frame; draw directly now.
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glViewport(0, 0, g_display_width, g_display_height);
            projectm_opengl_render_frame(g_projectm); // one more direct render ensures visible frame
        }
    } else {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, g_display_width, g_display_height);
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        projectm_opengl_render_frame(g_projectm);
    }

    static int frame_count = 0;
    if (g_memory_optimized && (++frame_count % 600) == 0) {
        optimize_memory_usage();
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
    // GL cleanup
    cleanup_performance_fbo();
    if (g_upscale_program) { glDeleteProgram(g_upscale_program); g_upscale_program = 0; }
    if (g_upscale_vbo) { glDeleteBuffers(1, &g_upscale_vbo); g_upscale_vbo = 0; }
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
    // Intentionally do NOT overwrite canonical display size here; surfaceChanged owns it.
    glViewport(x, y, width, height);
    LOGI("Native setViewport called (no dimension store): %d,%d %dx%d", x, y, width, height);
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

// Set render resolution - this is what the UI controls actually affect
extern "C"
JNIEXPORT void JNICALL
Java_com_example_projectm_visualizer_ProjectMJNI_nativeSetRenderResolution(JNIEnv *env, jclass clazz, jint width, jint height) {
    LOGI("Setting render resolution to %dx%d", width, height);
    
    if (g_performance_mode) {
        // In performance mode, manually override the render resolution
        g_render_width = width;
        g_render_height = height;
        
        // Recreate FBO with new resolution
        if (g_fbo != 0) {
            create_performance_fbo(g_render_width, g_render_height);
        }
        
        if (g_projectm) {
            projectm_set_window_size(g_projectm, g_render_width, g_render_height);
        }
        
        LOGI("Performance mode: updated render resolution to %dx%d", g_render_width, g_render_height);
    } else {
        // In quality mode, resolution changes don't affect render size (always full res)
        LOGI("Quality mode active: render resolution remains at display size %dx%d", g_display_width, g_display_height);
    }
}

// Toggle performance mode from Java
extern "C"
JNIEXPORT void JNICALL
Java_com_example_projectm_visualizer_ProjectMJNI_nativeSetPerformanceMode(JNIEnv *env, jclass clazz, jboolean enabled) {
    bool newMode = enabled == JNI_TRUE;
    if (newMode == g_performance_mode) return; // no change
    g_performance_mode = newMode;
    LOGI("Performance mode toggled: %s", g_performance_mode ? "ON" : "OFF");
    // Re-run sizing logic if we already have a surface
    if (g_display_width > 0 && g_display_height > 0 && g_projectm) {
        update_performance_settings();
        if (g_performance_mode) {
            if (g_upscale_program == 0) create_upscale_shader();
            create_performance_fbo(g_render_width, g_render_height);
        } else {
            cleanup_performance_fbo();
            // Switch projectM back to full size
            projectm_set_window_size(g_projectm, g_display_width, g_display_height);
        }
        projectm_set_window_size(g_projectm, g_render_width, g_render_height);
    }
}

// Getter so Java UI can reflect current native performance mode (after heuristics)
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_projectm_visualizer_ProjectMJNI_nativeIsPerformanceModeEnabled(JNIEnv *env, jclass clazz) {
    return g_performance_mode ? JNI_TRUE : JNI_FALSE;
}

// Set target FPS hint from Java (currently only stored; hook into future adaptive logic)
extern "C"
JNIEXPORT void JNICALL
Java_com_example_projectm_visualizer_ProjectMJNI_nativeSetTargetFPS(JNIEnv *env, jclass clazz, jint fps) {
    if (fps < 15) fps = 15;
    if (fps > 120) fps = 120;
    g_target_fps = fps;
    LOGI("Native target FPS set to %d", g_target_fps);
}

// Toggle external framebuffer respect (allows disabling for debugging)
extern "C"
JNIEXPORT void JNICALL
Java_com_example_projectm_visualizer_ProjectMJNI_nativeSetRespectExternalFramebuffer(JNIEnv* env, jclass clazz, jboolean enable) {
    projectm_set_respect_external_framebuffer(enable ? 1 : 0);
    LOGI("Respect external framebuffer: %s", (enable == JNI_TRUE) ? "ENABLED" : "DISABLED");
}
