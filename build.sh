#!/usr/bin/env bash
# Build script for Security Shield Android App

echo "🛡️ Building Security Shield Android App..."
echo "================================================"

# Check if gradle wrapper exists
if [ ! -f "gradlew" ]; then
    echo "❌ Gradle wrapper not found. Downloading..."
    gradle wrapper
fi

# Check Java version
echo "📋 Checking Java version..."
java -version

# Clean and build
echo "🔨 Building APK..."
./gradlew clean build --info

if [ $? -eq 0 ]; then
    echo "✅ Build successful!"
    echo "📦 APK location: app/build/outputs/apk/debug/app-debug.apk"
else
    echo "❌ Build failed!"
    exit 1
fi
