package com.securityshield.face

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorOptions
import com.securityshield.prefs.EncryptedPrefsManager

class FaceDetectorManager(private val context: Context) {
    companion object { private const val TAG = "FaceDetector" }
    private var faceDetector: FaceDetector? = null
    private val prefs = EncryptedPrefsManager(context)

    fun initialize() {
        faceDetector = FaceDetector.createFromOptions(context,
            FaceDetectorOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("blaze_face_short_range.tflite").build())
                .setRunningMode(RunningMode.IMAGE)
                .setMinDetectionConfidence(0.7f).build()
        )
        Log.i(TAG, "FaceDetector initialized")
    }

    fun detectFace(bitmap: Bitmap): FaceMatchResult {
        faceDetector ?: return FaceMatchResult.ERROR
        if (prefs.getString(EncryptedPrefsManager.KEY_OWNER_FACE_PATH).isBlank())
            return FaceMatchResult.NO_OWNER_REGISTERED
        val result = faceDetector!!.detect(BitmapImageBuilder(bitmap).build())
        return if (result.detections().isEmpty()) FaceMatchResult.NO_FACE_DETECTED
               else FaceMatchResult.FACE_DETECTED
    }

    fun release() { faceDetector?.close() }

    enum class FaceMatchResult { OWNER_MATCH, INTRUDER, NO_FACE_DETECTED, NO_OWNER_REGISTERED, FACE_DETECTED, ERROR }
}
