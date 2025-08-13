#!/bin/bash
# Wrapper script to build ProjectM TV app with Android Studio's bundled JDK

# Find Android Studio's Java Home
if [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
    JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    echo "Using Android Studio's Java at: $JAVA_HOME"
else
    echo "Android Studio's Java not found. Please make sure Android Studio is installed."
    exit 1
fi

# Export the environment variable
export JAVA_HOME

# Change to the script's directory to ensure gradlew can be found
cd "$(dirname "$0")"

# Run Gradle with the specified command or default to "assembleDebug"
if [ $# -eq 0 ]; then
    echo "Running default task: assembleDebug"
    ./gradlew assembleDebug
else
    echo "Running task: $*"
    ./gradlew $*
fi
