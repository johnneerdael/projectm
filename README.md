# projectM Visualizer for Android TV

![ProjectM Visualizer](app/src/main/res/drawable/launcher_icon.png)

A music visualization powerhouse for your Android TV, bringing the legendary ProjectM (an open-source reimplementation of Milkdrop) to your living room with the complete Cream of the Crop preset collection.

## Features

- **Full Android TV Integration** - Designed specifically for the big screen with remote-friendly navigation
- **Complete Preset Library** - Includes the entire Milkdrop Cream of the Crop collection (9,795 presets!)
- **System Audio Visualization** - Visualizes any audio playing on your device
- **Adaptive Performance** - Automatically adjusts to maintain smooth performance on all devices
- **Intuitive Controls** - Simple navigation with your Android TV remote
  - **Left/Right** - Previous/Next preset
  - **Center/Menu** - Toggle settings overlay
  - **Back** - Exit or hide overlay

## Technical Overview

This app is built on the ProjectM-4 library, combining native C++ visualization code with Android's Java framework. Key components include:

### Core Components

- **Native Visualization Engine** - ProjectM-4 C++ library accessed via JNI
- **GLSurfaceView Rendering** - Hardware-accelerated OpenGL ES 2.0 rendering
- **System Audio Capture** - Android Visualizer API for capturing audio output
- **Preset Management** - Dynamic loading and switching between thousands of presets
- **Performance Monitoring** - Adaptive rendering based on device capabilities

### Architecture

The app follows a layered architecture:

1. **UI Layer** - Android activities and views for user interaction
2. **Visualization Layer** - Java wrapper around native visualization code
3. **Native Layer** - C++ code interfacing with ProjectM-4 library
4. **Asset Management** - Extraction and management of preset files

## What is Milkdrop & the "Cream of the Crop" Pack?

### What is Milkdrop?

Imagine your music transforming into a vibrant, ever-changing universe of color, light, and motion. That's Milkdrop. At its core, it's a music visualizer, a plug-in originally created for the iconic Winamp media player, and now available for various other players like Kodi and projectM. Milkdrop uses your device's graphics power to generate intricate and dynamic visualizations that react in real-time to the beats, melodies, and frequencies of the music you're listening to. The result is a captivating and often trippy visual experience that perfectly complements your auditory journey.

### What Makes the "Cream of the Crop" Pack So Special?

With a vast and dedicated community of artists creating and sharing their own visual "presets" for Milkdrop over the years, the sheer volume of available options can be overwhelming. This is where the Cream of the Crop pack comes in as your expert guide.

Curated by Jason Fletcher, a respected figure in the Milkdrop community, this pack is a meticulously selected compilation of the "best of the best" presets. Fletcher sifted through thousands upon thousands of creations to handpick the most stunning, innovative, and awe-inspiring visuals. Think of it as the ultimate playlist for your eyes.

Boasting an incredible 9,795 presets, the Cream of the Crop pack is a testament to the creativity and technical artistry of the Milkdrop community. Its quality is so highly regarded that it has become the default preset pack for some versions of projectM, an open-source and cross-platform implementation of the Milkdrop engine.

### What to Expect from the Cream of the Crop Pack:

- **A Universe of Variety**: From pulsating geometric patterns and swirling nebulae to abstract landscapes and futuristic cityscapes, the diversity of visuals within the pack is staggering. You'll find a visual style to match any genre of music, from the most serene ambient tracks to the most frenetic electronic beats.

- **A Feast for the Eyes**: These aren't just simple loops. The presets in the Cream of the Crop pack are known for their complexity, smooth transitions, and breathtaking beauty. Prepare to be hypnotized by the intricate details and fluid animations.

- **A Gateway to a Thriving Community**: Exploring the Cream of the Crop pack is also a fantastic way to discover the work of talented visual artists and delve deeper into the world of music visualization.

## Android TV-Specific Features

This implementation includes several features specifically designed for the Android TV platform:

- **Full-screen Immersive Experience** - Utilizes the entire TV screen for maximum visual impact
- **Remote-Friendly Navigation** - Optimized for d-pad navigation with your TV remote
- **Automatic Preset Switching** - Configurable timing for hands-free visual variety
- **Low Power Mode** - Automatically detects performance issues and adapts
- **On-screen Overlay Menu** - Easily accessible controls that auto-hide when not needed
- **Persistence** - Continues visualizing even when the app loses focus
- **Version Information** - Easy access to app and library version information

## Technical Details

### Performance Optimization

The app includes several optimizations for Android TV devices:

- **Adaptive Frame Rate** - Monitors FPS and adjusts rendering complexity
- **OpenGL ES Configuration** - Tuned for the perfect balance of quality and performance
- **Memory Management** - Careful handling of preset loading to avoid OOM errors
- **Asset Extraction** - Efficient storage and access of preset files

### Audio Processing

Audio is captured from the system output using Android's Visualizer API:

- **Real-time PCM Data** - Direct access to audio waveform data
- **Low Latency** - Minimal delay between sound and visualization
- **Stereo Processing** - Full stereo audio visualization

## Installation

1. Download the APK from the releases section
2. Install on your Android TV device using your preferred method:
   - Sideload with adb: `adb install projectm-androidtv.apk`
   - Use a file manager app on your Android TV
   - Transfer via USB drive

## Permissions

The app requires the following permissions:

- `RECORD_AUDIO` - For capturing system audio
- `MODIFY_AUDIO_SETTINGS` - For audio processing
- `INTERNET` - For potential future online preset sharing

## Credits

- **ProjectM Team** - For the incredible open-source visualization library
- **Jason Fletcher** - For curating the Cream of the Crop preset collection
- **Milkdrop Community** - For creating thousands of amazing presets
- **Android Open Source Project** - For the Android TV platform

## License

This application is released under the same license as ProjectM (GPL v2).

---

Get ready to melt your eyeballs with the most mesmerizing music visualization experience available for Android TV!
