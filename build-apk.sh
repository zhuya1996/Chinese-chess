#!/bin/bash
set -e

echo "=== Building Android APK ==="
cd android_app

echo "=== Making gradlew executable ==="
chmod +x gradlew

echo "=== Building Debug APK ==="
./gradlew assembleDebug --stacktrace --no-daemon

echo "=== Building Release APK ==="
./gradlew assembleRelease --stacktrace --no-daemon

echo "=== Build completed ==="
