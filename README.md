# 🛡️ Security Shield — Android Anti-Theft App

**Target:** Realme GT 6T (RMX3853) / Android 16 / Realme UI 7.0

## Features
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

## Setup
1. Clone this repo and open in Android Studio Hedgehog+
2. Add your `google-services.json` to `/app`
3. Build → Install on device
4. Open app → Settings → Enter Telegram Bot Token, Chat ID, Gemini API Key
5. Tap "Register Owner Face"
6. Enable Protection toggle → Grant Device Admin when prompted

## Architecture
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

## Notes
- Screen recording requires user to grant MediaProjection permission each session
- Camera indicator (green dot) is always shown — this is correct Android behaviour
- All API keys stored in EncryptedSharedPreferences (AES256-GCM)
- Logs auto-deleted after 7 days
