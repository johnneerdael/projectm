# projectM Visualizer for Android TV (v1.6)

A music visualization powerhouse for your Android TV, bringing the legendary ProjectM (an open-source reimplementation of Milkdrop) to your living room with the complete Cream of the Crop preset collection and advanced transition controls.

## Features

- **Full Android TV Integration** - Designed specifically for the big screen with remote-friendly navigation
- **Complete Preset Library** - Includes the entire Milkdrop Cream of the Crop collection (9,795 presets!)
- **Modern Loading Experience** - Visual feedback during the lengthy preset extraction process
- **Advanced Transition Control** - Fine-tune how presets blend from one to another
- **System Audio Visualization** - Visualizes any audio playing on your device
- **Adaptive Performance** - Automatically adjusts resolution and quality to maintain smooth performance
- **Auto-Resolution Adjustment** - Dynamically lowers resolution when performance drops below target
- **Enhanced Low Performance Mode** - Smart quality adjustments for consistent framerate
- **Intuitive Controls** - Simple navigation with your Android TV remote with improved menu handling
  - **Left/Right** - Previous/Next preset with instant transitions and visual feedback
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

- **Modern Loading Screen** - Visual feedback with progress updates during asset extraction process
- **Full-screen Immersive Experience** - Utilizes the entire TV screen for maximum visual impact
- **Enhanced Remote Navigation** - Improved d-pad controls for menus and settings
- **Visual Feedback** - Toast notifications when changing presets or adjusting settings
- **Automatic Preset Switching** - Configurable timing from 10-90 seconds for hands-free visual variety
- **Smart Transition System** - Smooth transitions for auto-changes, instant cuts for manual navigation
- **Transition Duration Control** - Adjust blend time between presets from 0-10 seconds
- **Adaptive Performance** - Automatically detects performance issues and adjusts resolution and quality
- **Auto-Resolution Adjustment** - Dynamically lowers resolution to maintain target framerate
- **On-screen Overlay Menu** - Easily accessible controls that auto-hide when not needed
- **Persistence** - Continues visualizing even when the app loses focus
- **Version Information** - Easy access to app and library version information

## Technical Details

### Performance Optimization

The app includes several optimizations for Android TV devices:

- **Enhanced Adaptive Frame Rate** - Monitors FPS and automatically adjusts resolution and rendering quality
- **Auto-Resolution Adjustment** - Dynamically switches between 480p, 720p, 1080p, and 4K based on performance
- **Visual Performance Feedback** - Toast notifications when resolution is automatically adjusted
- **Performance Level Management** - Adjusts multiple quality parameters based on device capabilities
- **Smart Transition Handling** - Uses hard cuts in low performance mode, smooth transitions when performance allows
- **OpenGL ES Configuration** - Tuned for the perfect balance of quality and performance
- **Improved Memory Management** - Careful handling of preset loading to avoid OOM errors
- **Enhanced Asset Extraction** - Efficient storage and access of preset files with visual progress feedback

### Audio Processing

Audio is captured from the system output using Android's Visualizer API:

- **Real-time PCM Data** - Direct access to audio waveform data
- **Low Latency** - Minimal delay between sound and visualization
- **Stereo Processing** - Full stereo audio visualization

## Installation

1. Download the APK from the releases section
2. Install on your Android TV device using your preferred method:
   - Sideload with adb: `adb install projectm-androidtv-1.3.apk`
   - Use a file manager app on your Android TV
   - Transfer via USB drive
   
If you're updating from a previous version, your preferences will be preserved.

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

## Usage Tips

- **For a classic "Milkdrop experience":** Keep transitions at 7s and preset duration around 30s
- **For a more dynamic show:** Try shorter preset durations (10-15s) with shorter transitions (2-3s)
- **For manual control:** Disable auto-change and use the remote to change presets with hard cuts
- **For maximum performance:** Let the app automatically adjust resolution, or manually lower it in settings
- **For a cinematic experience:** Set long preset durations (60-90s) with long transitions (8-10s)
- **For consistent navigation:** Use single, deliberate button presses when navigating menus
- **For smooth transitions:** Keep transition duration around 5-7s unless on a low-end device

---

Get ready to melt your eyeballs with the most mesmerizing music visualization experience available for Android TV!
