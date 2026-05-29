# 🛡️ Build Instructions for Security Shield

## Prerequisites
- ✅ Java 17+ installed
- ✅ Android SDK 35 (API level 35)
- ✅ Android Studio Hedgehog or newer
- ✅ Gradle wrapper included

## Quick Start

### 1️⃣ Setup
```bash
# Update local.properties with your Android SDK path
echo "sdk.dir=/Users/YourName/Library/Android/sdk" > local.properties
```

### 2️⃣ Build APK
```bash
# Using build script (Linux/Mac)
chmod +x build.sh
./build.sh

# Or using Gradle directly
./gradlew clean build
```

### 3️⃣ Build Release APK
```bash
./gradlew build -PbuildType=release
```

### 4️⃣ Install on Device
```bash
./gradlew installDebug
```

## Build Output
- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release APK**: `app/build/outputs/apk/release/app-release.apk`

## Troubleshooting

### Build fails with "Could not find com.google.gms:google-services"
✅ Add your `google-services.json` to `/app` directory

### Memory error during build
✅ Increase heap size in `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx8192m
```

### Kotlin compilation errors
✅ Ensure Java 17 is being used:
```bash
java -version
```

## Features Included
- ✅ Kotlin 100%
- ✅ Firebase Integration
- ✅ CameraX for capture
- ✅ MediaPipe face detection
- ✅ Gemini AI analysis
- ✅ Telegram Bot integration
- ✅ EncryptedSharedPreferences security
- ✅ WorkManager background tasks
- ✅ Glassmorphism UI

## Next Steps After Build
1. Transfer APK to device or use `./gradlew installDebug`
2. Grant required permissions
3. Configure Firebase, Telegram, and Gemini API keys in app settings
4. Register owner face
5. Enable protection

---
**Need help?** Check the main README.md for more details.
