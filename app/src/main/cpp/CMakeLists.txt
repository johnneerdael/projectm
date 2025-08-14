cmake_minimum_required(VERSION 3.22.1)

project(projectmtv)

# Find required system libraries
find_library(log-lib log)
find_library(GLESv2-lib GLESv2)
find_library(android-lib android)
find_library(egl-lib EGL)

# Add prebuilt projectM libraries
add_library(projectM-4 SHARED IMPORTED)
set_target_properties(projectM-4 PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libprojectM-4.so
)

add_library(projectM-4-playlist SHARED IMPORTED)
set_target_properties(projectM-4-playlist PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libprojectM-4-playlist.so
)

# Create our JNI wrapper library
add_library(projectmtv SHARED native-lib.cpp)

# Include projectM headers
target_include_directories(projectmtv PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/include
    ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/include/projectM-4
    ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/include/projectM-4-playlist
)

# Link libraries
target_link_libraries(projectmtv
    projectM-4
    projectM-4-playlist
    ${log-lib}
    ${GLESv2-lib}
    ${egl-lib}
    ${android-lib}
)

# Set C++ standard
set_target_properties(projectmtv PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
)
