#!/bin/bash
# Auto-build Android APK from React Native / Expo project
set -e

echo "=== Progress Tracker Build Pipeline ==="
npm ci
npx expo build:android --no-publish --output build/progress-tracker.apk
echo "APK ready: build/progress-tracker.apk"
