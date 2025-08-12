#!/bin/bash

echo "ProjectM Android TV Debug Script"
echo "================================"

echo "Checking for connected devices..."
adb devices

echo ""
echo "Getting system logs (crash information)..."
echo "Press Ctrl+C to stop log monitoring"
echo ""

# Clear old logs and start monitoring
adb logcat -c
adb logcat | grep -E "(projectM|FATAL|AndroidRuntime|E/)"
