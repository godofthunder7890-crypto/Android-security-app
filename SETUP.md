# 🛡️ SecurityShield Setup Guide

## ✅ Prerequisites

- **JDK 17+** - [Download](https://www.oracle.com/java/technologies/downloads/)
- **Android Studio Hedgehog+** - [Download](https://developer.android.com/studio)
- **Android SDK 35** - Install via Android Studio SDK Manager
- **Git** - [Download](https://git-scm.com/)

## 🚀 Quick Start

### 1️⃣ Clone Repository
```bash
git clone https://github.com/godofthunder7890-crypto/Android-security-app.git
cd Android-security-app
```

### 2️⃣ Setup (Local Machine)

#### Option A: Using Build Script (Easiest)
```bash
# Make script executable
chmod +x build-apk.sh

# Run script
./build-apk.sh

# Choose option 1 for Debug APK
```

#### Option B: Manual Build
```bash
# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease

# Clean build
./gradlew clean build
```

### 3️⃣ Generate Signing Key (For Release APK)

```bash
chmod +x generate-signing-key.sh
./generate-signing-key.sh
```

This will create `release-keystore.jks` file. **Keep it safe!** ⚠️

### 4️⃣ Install on Device

```bash
# Debug APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Or use Android Studio: Run → Select Device
```

## 📂 Build Output Locations

| Type | Path |
|------|------|
| **Debug APK** | `app/build/outputs/apk/debug/app-debug.apk` |
| **Release APK** | `app/build/outputs/apk/release/app-release-unsigned.apk` |
| **Build Reports** | `app/build/reports/` |

## 🔧 Configuration

### Add Google Services JSON
1. Get `google-services.json` from Firebase Console
2. Place it in `app/` directory
3. Run build

### Add API Keys
1. Open app on device
2. Go to Settings
3. Add:
   - Telegram Bot Token
   - Telegram Chat ID
   - Gemini API Key

## 🤖 GitHub Actions (Auto Build)

Push to `main` or `merge-security-app` branch → **APK builds automatically!**

Check: [Actions Tab](https://github.com/godofthunder7890-crypto/Android-security-app/actions)

Download APK from artifacts!

## 🐛 Troubleshooting

### Build Fails with Gradle Error
```bash
./gradlew clean
./gradlew build --stacktrace
```

### Java Version Mismatch
```bash
# Check Java version
java -version

# Should be 17 or higher
```

### Android SDK Missing
```bash
# Install via SDK Manager in Android Studio
# Or use: sdkmanager "platforms;android-35"
```

### APK Installation Fails
```bash
# Uninstall existing app first
adb uninstall com.securityshield

# Then install
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 📱 Device Requirements

- **Min SDK**: Android 8.0 (API 26)
- **Target SDK**: Android 15 (API 35)
- **RAM**: 2GB minimum
- **Storage**: 50MB free

## 🔐 Security Notes

- Never commit `release-keystore.jks` to Git
- Keep keystore password safe
- Use `app-debug.apk` for testing only
- Sign `app-release-unsigned.apk` before distribution

## 📚 Useful Commands

```bash
# List connected devices
adb devices

# Clear app data
adb shell pm clear com.securityshield

# View logs
adb logcat -s SecurityShield

# Build and install in one command
./gradlew installDebug

# Generate APK with specific variant
./gradlew assembleDebug --stacktrace --info
```

## ✨ Features

- ✅ Failed unlock detection
- ✅ Camera capture on trigger
- ✅ Location tracking
- ✅ Screen recording
- ✅ AI analysis (Gemini)
- ✅ Telegram notifications
- ✅ Face detection (MediaPipe)
- ✅ Encrypted storage

## 📞 Support

Having issues? 
- Check build logs: `./gradlew build --stacktrace`
- Review GitHub Issues
- Check README.md

## 🎉 Done!

Your APK is ready to use! 🚀

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Happy building! 💚
