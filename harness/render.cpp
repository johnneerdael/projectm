// Renders one preset with libprojectM's public C API into an offscreen RGBA8 framebuffer and
// writes selected frames as raw RGB plus a JSON summary. Deterministic: fixed PCM file, fixed
// frame clock (frame / fps) and a fixed rand() seed.
//
// usage: render --preset P --textures DIR --pcm F.f32 --size WxH --frames N --fps 30
//               [--line-reference WxH] [--capture f1,f2,...] [--luma-from F] --out PREFIX
#include <projectM-4/projectM.h>

#include <SDL.h>
#include <OpenGL/gl3.h>

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <set>
#include <sstream>
#include <string>
#include <vector>

static void Fail(const std::string& message)
{
    std::fprintf(stderr, "render: %s\n", message.c_str());
    std::exit(1);
}

static std::string g_failure;

static void OnPresetFailed(const char* filename, const char* message, void*)
{
    g_failure = std::string(filename ? filename : "?") + ": " + (message ? message : "?");
}

int main(int argc, char** argv)
{
    std::string preset, textures, pcmPath, out;
    int width = 0, height = 0, frames = 0, fps = 30, refW = 0, refH = 0, lumaFrom = 0, timeFrom = -1;
    std::set<int> capture;
    for (int i = 1; i + 1 < argc; i += 2)
    {
        std::string key = argv[i], value = argv[i + 1];
        if (key == "--preset") preset = value;
        else if (key == "--textures") textures = value;
        else if (key == "--pcm") pcmPath = value;
        else if (key == "--out") out = value;
        else if (key == "--size") std::sscanf(value.c_str(), "%dx%d", &width, &height);
        else if (key == "--line-reference") std::sscanf(value.c_str(), "%dx%d", &refW, &refH);
        else if (key == "--frames") frames = std::atoi(value.c_str());
        else if (key == "--fps") fps = std::atoi(value.c_str());
        else if (key == "--luma-from") lumaFrom = std::atoi(value.c_str());
        else if (key == "--time-from") timeFrom = std::atoi(value.c_str());
        else if (key == "--capture")
        {
            std::stringstream list(value);
            std::string item;
            while (std::getline(list, item, ',')) capture.insert(std::atoi(item.c_str()));
        }
        else Fail("unknown option " + key);
    }
    if (preset.empty() || out.empty() || width <= 0 || height <= 0 || frames <= 0) Fail("missing options");

    const int block = 44100 / fps;
    std::ifstream pcmFile(pcmPath, std::ios::binary | std::ios::ate);
    if (!pcmFile || pcmFile.tellg() < static_cast<std::streamoff>(frames) * block * 4) Fail("PCM too short");
    pcmFile.seekg(0);

    if (SDL_Init(SDL_INIT_VIDEO) != 0) Fail(SDL_GetError());
    SDL_GL_SetAttribute(SDL_GL_CONTEXT_MAJOR_VERSION, 3);
    SDL_GL_SetAttribute(SDL_GL_CONTEXT_MINOR_VERSION, 3);
    SDL_GL_SetAttribute(SDL_GL_CONTEXT_PROFILE_MASK, SDL_GL_CONTEXT_PROFILE_CORE);
    SDL_GL_SetAttribute(SDL_GL_CONTEXT_FLAGS, SDL_GL_CONTEXT_FORWARD_COMPATIBLE_FLAG);
    SDL_Window* window = SDL_CreateWindow("render", 0, 0, 64, 64, SDL_WINDOW_OPENGL | SDL_WINDOW_HIDDEN);
    if (!window) Fail(SDL_GetError());
    SDL_GLContext context = SDL_GL_CreateContext(window);
    if (!context || SDL_GL_MakeCurrent(window, context) != 0) Fail(SDL_GetError());

    GLuint fbo = 0, texture = 0;
    glGenFramebuffers(1, &fbo);
    glGenTextures(1, &texture);
    glBindTexture(GL_TEXTURE_2D, texture);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) Fail("framebuffer incomplete");
    glClearColor(0, 0, 0, 1);
    glClear(GL_COLOR_BUFFER_BIT);

    std::srand(12345);
    projectm_handle pm = projectm_create();
    if (!pm) Fail("projectm_create failed");
    const char* texturePaths[] = {textures.c_str()};
    projectm_set_texture_search_paths(pm, texturePaths, 1);
    projectm_set_window_size(pm, width, height);
    projectm_set_mesh_size(pm, 48, 32);
    projectm_set_fps(pm, fps);
    projectm_set_preset_locked(pm, true);
    projectm_set_hard_cut_enabled(pm, false);
    projectm_set_soft_cut_duration(pm, 0.0);
    projectm_set_easter_egg(pm, 0.0f);
    projectm_set_preset_switch_failed_event_callback(pm, &OnPresetFailed, nullptr);
#ifdef HAS_LINE_REFERENCE
    projectm_opengl_set_line_reference_size(pm, static_cast<uint32_t>(refW), static_cast<uint32_t>(refH));
#else
    if (refW != 0 || refH != 0) Fail("this build has no line reference API");
#endif
    projectm_set_frame_time(pm, 0.0);
    projectm_load_preset_file(pm, preset.c_str(), false);
    if (!g_failure.empty()) Fail("preset failed: " + g_failure);

    std::vector<float> pcm(block);
    std::vector<double> frameMs;
    std::vector<unsigned char> pixels(static_cast<size_t>(width) * height * 4);
    double lumaSum = 0.0;
    int lumaFrames = 0, errorFrames = 0;
    std::ostringstream captured;
    for (int frame = 0; frame < frames; frame++)
    {
        pcmFile.read(reinterpret_cast<char*>(pcm.data()), block * 4);
        projectm_pcm_add_float(pm, pcm.data(), static_cast<unsigned int>(block), PROJECTM_MONO);
        projectm_set_frame_time(pm, static_cast<double>(frame + 1) / fps);
        glFinish();
        const auto start = std::chrono::steady_clock::now();
        projectm_opengl_render_frame_fbo(pm, fbo);
        if (timeFrom >= 0 && frame >= timeFrom)
        {
            glFinish();
            frameMs.push_back(std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - start).count());
        }
        if (glGetError() != GL_NO_ERROR) errorFrames++;

        const bool wantLuma = lumaFrom > 0 && frame >= lumaFrom && (frame - lumaFrom) % 5 == 0;
        if (!wantLuma && capture.count(frame) == 0) continue;

        glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels.data());
        if (wantLuma)
        {
            double sum = 0.0;
            for (size_t p = 0; p < pixels.size(); p += 4)
                sum += 0.2126 * pixels[p] + 0.7152 * pixels[p + 1] + 0.0722 * pixels[p + 2];
            lumaSum += sum / (255.0 * width * height);
            lumaFrames++;
        }
        if (capture.count(frame))
        {
            // Raw RGB, top row first.
            std::ofstream raw(out + "-f" + std::to_string(frame) + ".rgb", std::ios::binary);
            for (int y = height - 1; y >= 0; y--)
                for (int x = 0; x < width; x++)
                    raw.write(reinterpret_cast<const char*>(&pixels[(static_cast<size_t>(y) * width + x) * 4]), 3);
            captured << (captured.tellp() > 0 ? "," : "") << frame;
        }
    }

    std::ofstream json(out + ".json");
    json << "{\"width\":" << width << ",\"height\":" << height << ",\"frames\":" << frames
         << ",\"line_reference\":[" << refW << "," << refH << "]"
         << ",\"mean_luma\":" << (lumaFrames ? lumaSum / lumaFrames : -1.0)
         << ",\"luma_frames\":" << lumaFrames << ",\"gl_error_frames\":" << errorFrames
         << ",\"captured\":[" << captured.str() << "]"
         << ",\"median_frame_ms\":" << [&] { if (frameMs.empty()) return -1.0; std::sort(frameMs.begin(), frameMs.end()); return frameMs[frameMs.size() / 2]; }()
         << ",\"gl_renderer\":\"" << reinterpret_cast<const char*>(glGetString(GL_RENDERER)) << "\""
         << ",\"gl_version\":\"" << reinterpret_cast<const char*>(glGetString(GL_VERSION)) << "\"}\n";

    projectm_destroy(pm);
    SDL_GL_DeleteContext(context);
    SDL_DestroyWindow(window);
    SDL_Quit();
    return errorFrames ? 2 : 0;
}
