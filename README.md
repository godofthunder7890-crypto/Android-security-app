# 🛡️ Security Shield — Android Anti-Theft App

**Target:** Realme GT 6T (RMX3853) / Android 16 / Realme UI 7.0

## ✨ Features
- ✅ Failed unlock attempt detection (DeviceAdmin — user must grant)
- ✅ Front camera capture on trigger (Android indicator shown — transparent)
- ✅ Location + photo sent to Telegram Bot
- ✅ 20s screen recording (MediaProjection — requires user consent)
- ✅ Gemini 1.5 Flash AI analysis in Hinglish
- ✅ MediaPipe face detection
- ✅ Glassmorphism dashboard UI
- ✅ EncryptedSharedPreferences for all sensitive keys
- ✅ WorkManager for reliable background pipeline
- ✅ 7-day auto log cleanup
- ✅ Thermal safety (640x360, 15fps recording)

## 🚀 Setup

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 17+
- Android SDK 35
- Minimum API 26 (Android 8.0)

### Build Steps
1. Clone this repo and open in Android Studio
2. Add your `google-services.json` to `/app`
3. Build → Build Bundle(s) / APK(s) → Build Release APK
4. Install on device via: `adb install -r app/release/app-release.apk`
5. Open app → Settings → Enter credentials:
   - Telegram Bot Token
   - Chat ID
   - Gemini API Key
6. Tap "Register Owner Face"
7. Enable Protection toggle → Grant Device Admin when prompted

## 📐 Architecture
```
SecurityAdminReceiver (password fail)
        │
        ▼
SecurityService (Foreground — visible notification)
        │
        ├── CameraCapture (CameraX — green dot shown)
        ├── ScreenRecordService (MediaProjection — user consent)
        └── WorkManager Pipeline:
                GeminiAnalysisWorker → TelegramUploadWorker → LogCleanupWorker
```

## 🔐 Security Notes
- Screen recording requires user to grant MediaProjection permission each session
- Camera indicator (green dot) is always shown — this is correct Android behaviour
- All API keys stored in EncryptedSharedPreferences (AES256-GCM)
- Logs auto-deleted after 7 days
- Release builds include ProGuard obfuscation

## 📦 Build Variants
- **Debug**: Fast build, no obfuscation, debugging enabled
- **Release**: Optimized, ProGuard obfuscation, signing required

## 🔨 Build Commands
```bash
# Debug APK
./gradlew assembleDebug

# Release APK (requires signing config)
./gradlew assembleRelease

# Build and install debug
./gradlew installDebug

# Clean build
./gradlew clean build
```

## 📱 Gradle Configuration
- **AGP**: 8.5.0
- **Kotlin**: 1.9.24
- **Compile SDK**: 35
- **Target SDK**: 35
- **Min SDK**: 26 (Android 8.0)
- **Java Target**: 17

## 📄 License
Private repository - All rights reserved

## 🤝 Support
For issues or contributions, contact the repository owner.
