#!/bin/bash
set -e

echo "=== Building Android APK ==="
cd android_app

echo "=== Making gradlew executable ==="
chmod +x gradlew

echo "=== Running gradlew tasks to check available tasks ==="
./gradlew tasks --all | grep -i assemble | head -20

echo "=== Building Debug APK with explicit command ==="
./gradlew :app:assembleDebug --stacktrace --no-daemon

echo "=== Building Release APK with explicit command ==="
./gradlew :app:assembleRelease --stacktrace --no-daemon

echo "=== Build completed ==="
