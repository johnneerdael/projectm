# projectM Android TV 1.2 - Performance & User Experience Update

![ProjectM Visualizer for Android TV](https://raw.githubusercontent.com/projectM-visualizer/projectm/master/projectm-android-tv/app/src/main/res/drawable/launcher_icon.png)

## � Enhanced Visualization Controls

We're excited to announce version 1.2 of projectM for Android TV, featuring improved performance adaptation, an enhanced loading experience, and better control navigation. This update provides a more responsive and stable visualization experience, especially on lower-end devices.

### ✨ New Features

- **Modern Loading Screen** - Visual feedback during the asset extraction process with progress updates
- **Automatic Resolution Adjustment** - Dynamically lowers resolution based on performance
- **Enhanced Low Performance Mode** - Smarter performance adaptation with quality level adjustments
- **Improved Navigation** - Better menu control and D-pad navigation for resolution settings
- **Visual Feedback** - Toast notifications when changing presets or adjusting resolution
- **Performance Optimizations** - Enhanced frame rate monitoring and quality adjustments
- **Bug Fixes** - Improved stability and responsiveness in low-performance conditions

### 🎵 Existing Features

- **Complete Cream of the Crop Preset Collection** - All 9,795 handpicked presets included
- **System Audio Visualization** - Visualizes any audio playing on your Android TV
- **Android TV Remote Control**
  - Left/Right buttons to change presets (now with instant transitions)
  - Center/Menu button to toggle settings overlay
  - Back button to exit or hide overlay
- **Auto Preset Switching** - Configurable timing with new extended range
- **Performance Monitoring** - Adaptive rendering with improved transition handling
- **Full Screen Immersive Mode** - Complete Android TV visual experience

### 🔧 Technical Details

- Built on projectM-4 native visualization library
- OpenGL ES 2.0 hardware-accelerated rendering
- FPS monitoring with performance mode switching
- Preset count and current preset name display
- Version information accessible in settings menu

### 📱 Device Compatibility

- **Minimum Android Version**: Android 5.0 (Lollipop)
- **Target Devices**: Android TV boxes and TVs with Android TV OS
- **GPU Requirement**: OpenGL ES 2.0 capable GPU
- **Performance**: Now better optimized for lower-end devices with automatic quality adjustments

### 📋 Permissions Required

- `RECORD_AUDIO` - Required for capturing system audio
- `MODIFY_AUDIO_SETTINGS` - Required for audio visualization processing

### 🐛 Known Issues

- Some complex presets may still cause performance issues, but are now automatically managed
- Occasional black screen when switching between certain presets
- Audio capture may not work on all devices depending on manufacturer restrictions
- Navigation in the settings menu may require multiple button presses on some remote controls

### 🎮 Usage Tips

- **Classic Experience**: Keep transitions at 7s and preset duration around 30s
- **Dynamic Show**: Try shorter preset durations (10-15s) with shorter transitions (2-3s)
- **Manual Control**: Disable auto-change and use the remote for immediate transitions
- **Performance Mode**: Lower transition duration and resolution on less powerful devices

### 🔜 Upcoming Features

- Preset search and filtering
- Custom preset categories
- Background audio playback
- Beat detection sensitivity adjustment
- Preset rating system

### 🚀 Installation

1. Download the APK file from this release
2. Install on your Android TV using one of these methods:
   - Sideload with adb: `adb install projectm-androidtv-1.2.apk`
   - Transfer via USB and install with a file manager
   - Use a sideloading app like Downloader or Send Files to TV
   
If you're updating from version 1.0 or 1.1, your preferences will be preserved.

### 🙏 Credits

- projectM Development Team for the visualization library
- Jason Fletcher for the incredible Cream of the Crop preset collection
- The entire Milkdrop community for creating thousands of amazing presets

### 📄 License

This application is released under the GPL v2 license, the same as the core projectM library.

---

## SHA-256 Checksums
```
projectm-androidtv-1.2.apk: [checksum will be generated after building the final APK]
```

## Full Changelog for v1.2

### Added
- Modern loading screen with progress feedback during asset extraction
- Automatic resolution adjustment based on performance monitoring
- Enhanced performance level handling with quality adjustments
- Improved D-pad navigation in menus and settings
- Toast notifications when changing presets or adjusting resolution
- Preset name display in toast notifications when changing presets
- Automatic resolution reduction for devices with poor performance
- Background thread for asset extraction with progress monitoring
- Build script wrapper for easier compilation with correct Java environment

### Fixed
- Menu navigation with D-pad for resolution selection
- Improved low performance mode handling
- Enhanced error recovery in low-performance conditions
- Better handling of transitions in low-performance mode
- Optimized rendering for low-end devices
- Improved menu focus handling and control navigation
- Fixed java.lang.NullPointerException in GLSurfaceView initialization

### Changed
- Updated loading sequence for better user feedback
- Improved auto-resolution system that adapts to device capabilities
- Enhanced frame rate monitoring for more accurate performance assessment
- Smarter handling of preset transitions during low performance
- Improved OpenGL rendering optimizations
- Streamlined initialization process for faster startup

## Full Changelog for v1.1

### Added
- New transition duration slider (0-10 seconds) in overlay menu
- Hard cut support for manual preset changes
- Extended preset duration range (10-90 seconds)
- Transition type preference saving
- Usage tips in documentation

### Fixed
- Compilation issues in MainActivity.java
- Improved error handling for preset transitions
- Resolved potential synchronization issues during transitions
- Better OpenGL state handling during preset changes
- Fixed crash related to GLSurfaceView initialization
- Added proper error handling for OpenGL contexts
- Improved lifecycle management for onResume/onPause events
- Enhanced error recovery for Android TV devices
- Fixed potential race conditions in visualization initialization
- Resolved Application Not Responding (ANR) errors on startup
- Moved asset extraction to background thread for improved performance
- Added synchronization mechanism for preset loading

### Changed
- Updated default transition to 7 seconds for smoother visualization
- Manual preset changes now use hard cuts for immediate feedback
- Auto-changing presets use smooth transitions for better visual experience
- Code refactoring for improved maintainability
- Improved initialization sequence for more reliable startup
