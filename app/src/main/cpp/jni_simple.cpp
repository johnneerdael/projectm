#include <android/log.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string>
#include <time.h>
#include "ProjectM.hpp"

#define TAG "ProjectMTV"
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR,    TAG, __VA_ARGS__)
#define ALOGW(...) __android_log_print(ANDROID_LOG_WARN,     TAG, __VA_ARGS__)
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO,     TAG, __VA_ARGS__)
#define ALOGD(...) __android_log_print(ANDROID_LOG_DEBUG,    TAG, __VA_ARGS__)

projectM *instance = NULL;
int current_preset_index = 0;

void next_preset(bool hard_cut) {
    if (!instance) {
        ALOGE("libprojectM not initialized");
        return;
    }
    srand((unsigned) time(NULL));
    int preset_list_size = instance->getPlaylistSize();
    if (preset_list_size <= 0) {
        ALOGE("Could not load any presets");
        return;
    }
    current_preset_index = (current_preset_index + 1) % preset_list_size;
    ALOGD("Switching to preset %d of %d", current_preset_index, preset_list_size);
    instance->selectPreset(current_preset_index, hard_cut);
}

void previous_preset(bool hard_cut) {
    if (!instance) {
        ALOGE("libprojectM not initialized");
        return;
    }
    int preset_list_size = instance->getPlaylistSize();
    if (preset_list_size <= 0) {
        ALOGE("Could not load any presets");
        return;
    }
    current_preset_index = (current_preset_index - 1 + preset_list_size) % preset_list_size;
    ALOGD("Switching to previous preset %d of %d", current_preset_index, preset_list_size);
    instance->selectPreset(current_preset_index, hard_cut);
}

extern "C" JNIEXPORT void JNICALL
Java_com_johnneerdael_projectm_visualizer_ProjectMJNI_onSurfaceCreated(
        JNIEnv *env,
        jclass clazz,
        jint window_width,
        jint window_height,
        jstring jasset_path) {
    ALOGI("onSurfaceCreated called: %dx%d", window_width, window_height);
    
    if (instance) {
        ALOGD("Destroy existing instance");
        delete instance;
        instance = NULL;
    }
    
    const char* asset_path_chars = env->GetStringUTFChars(jasset_path, NULL);
    std::string asset_path(asset_path_chars);
    
    projectM::Settings settings;
    settings.windowHeight = window_height;
    settings.windowWidth = window_width;
    settings.presetURL = asset_path + "/presets";
    settings.smoothPresetDuration = 5; // 5 second transition
    settings.presetDuration = 10; // 10 seconds per preset
    settings.shuffleEnabled = true;
    settings.softCutRatingsEnabled = false;
    
    ALOGD("presetURL: %s", settings.presetURL.c_str());
    env->ReleaseStringUTFChars(jasset_path, asset_path_chars);
    
    try {
        ALOGD("Creating new projectM instance");
        instance = new projectM(settings);
        
        // Initialize with a random preset
        srand(time(0));
        int playlist_size = instance->getPlaylistSize();
        if (playlist_size > 0) {
            current_preset_index = rand() % playlist_size;
            instance->selectPreset(current_preset_index, true);
            ALOGI("ProjectM initialized with %d presets, starting with preset %d", playlist_size, current_preset_index);
        } else {
            ALOGE("No presets found in: %s", settings.presetURL.c_str());
        }
    } catch (const std::exception& e) {
        ALOGE("Failed to create projectM instance: %s", e.what());
        instance = NULL;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_johnneerdael_projectm_visualizer_ProjectMJNI_onSurfaceChanged(
        JNIEnv *env,
        jclass clazz,
        jint window_width,
        jint window_height) {
    ALOGD("onSurfaceChanged: %dx%d", window_width, window_height);
    if (!instance) {
        ALOGE("projectM instance is null in onSurfaceChanged");
        return;
    }
    try {
        instance->projectM_resetGL(window_width, window_height);
    } catch (const std::exception& e) {
        ALOGE("Error in projectM_resetGL: %s", e.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_johnneerdael_projectm_visualizer_ProjectMJNI_onDrawFrame(
        JNIEnv *env,
        jclass clazz) {
    if (!instance) {
        return;
    }
    try {
        instance->renderFrame();
    } catch (const std::exception& e) {
        ALOGE("Error in renderFrame: %s", e.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_johnneerdael_projectm_visualizer_ProjectMJNI_addPCM(
        JNIEnv *env,
        jclass clazz,
        jshortArray pcm_data,
        jshort nsamples) {
    if (!instance) {
        return;
    }
    try {
        jshort *data = env->GetShortArrayElements(pcm_data, NULL);
        if (data != NULL) {
            instance->pcm()->addPCM16Data(data, nsamples);
            env->ReleaseShortArrayElements(pcm_data, data, 0);
        }
    } catch (const std::exception& e) {
        ALOGE("Error adding PCM data: %s", e.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_johnneerdael_projectm_visualizer_ProjectMJNI_nextPreset(
        JNIEnv *env,
        jclass clazz) {
    ALOGD("nextPreset called");
    next_preset(true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_johnneerdael_projectm_visualizer_ProjectMJNI_previousPreset(
        JNIEnv *env,
        jclass clazz) {
    ALOGD("previousPreset called");
    previous_preset(true);
}
