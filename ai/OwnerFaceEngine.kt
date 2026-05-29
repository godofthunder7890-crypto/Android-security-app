package com.securityshield.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorOptions
import java.io.File
import java.io.FileOutputStream

/**
 * OwnerFaceEngine: On-device face detection and owner identity verification
 * using MediaPipe Face Detection.
 *
 * Architecture note: True face RECOGNITION (matching against a stored face)
 * requires an embedding model (FaceNet, ArcFace). MediaPipe FaceDetector only
 * detects presence. This implementation uses a practical hybrid approach:
 * - MediaPipe detects IF a face is present (fast, on-device)
 * - If detected, a stored reference bitmap is compared via pixel correlation
 * - For production, replace correlation with a MobileNet embedding model
 *
 * Dependencies (build.gradle.kts):
 * implementation("com.google.mediapipe:tasks-vision:0.10.14")
 *
 * Asset: Copy face_detection_short_range.tflite to assets/models/
 * Download from: https://storage.googleapis.com/mediapipe-models/
 */
class OwnerFaceEngine(private val context: Context) {

    companion object {
        private const val TAG              = "OwnerFaceEngine"
        private const val MODEL_ASSET      = "models/face_detection_short_range.tflite"
        private const val REFERENCE_FILE   = "owner_face_reference.jpg"
        private const val MATCH_THRESHOLD  = 0.72f   // Tune this (0.0–1.0)
        private const val MIN_DETECTION_SCORE = 0.7f
    }

    enum class VerificationResult {
        OWNER_MATCH,
        FACE_MISMATCH,
        NO_FACE_DETECTED,
        REFERENCE_NOT_ENROLLED,
        ENGINE_ERROR
    }

    private var faceDetector: FaceDetector? = null

    fun initialize(): Boolean {
        return try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET)
                .build()

            val options = FaceDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setMinDetectionConfidence(MIN_DETECTION_SCORE)
                .build()

            faceDetector = FaceDetector.createFromOptions(context, options)
            Log.i(TAG, "MediaPipe FaceDetector initialized.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize FaceDetector: ${e.message}")
            false
        }
    }

    /**
     * Main verification function.
     * @param inputBitmap Frame from the front camera to verify.
     * @return VerificationResult enum.
     */
    fun verify(inputBitmap: Bitmap): VerificationResult {
        val detector = faceDetector ?: return VerificationResult.ENGINE_ERROR

        return try {
            val mpImage = BitmapImageBuilder(inputBitmap).build()
            val result = detector.detect(mpImage)

            if (result.detections().isEmpty()) {
                Log.d(TAG, "No face detected in frame.")
                return VerificationResult.NO_FACE_DETECTED
            }

            // Face detected — compare against enrolled reference
            val referenceFile = File(context.filesDir, REFERENCE_FILE)
            if (!referenceFile.exists()) {
                Log.w(TAG, "Owner face not enrolled yet.")
                return VerificationResult.REFERENCE_NOT_ENROLLED
            }

            val referenceBitmap = BitmapFactory.decodeFile(referenceFile.absolutePath)
                ?: return VerificationResult.ENGINE_ERROR

            // Extract the largest detected face region from input
            val detection = result.detections().maxByOrNull {
                it.boundingBox().width() * it.boundingBox().height()
            }!!

            val bbox = detection.boundingBox()
            val faceX      = maxOf(0, bbox.left.toInt())
            val faceY      = maxOf(0, bbox.top.toInt())
            val faceWidth  = minOf(bbox.width().toInt(), inputBitmap.width - faceX)
            val faceHeight = minOf(bbox.height().toInt(), inputBitmap.height - faceY)

            if (faceWidth <= 0 || faceHeight <= 0) {
                return VerificationResult.FACE_MISMATCH
            }

            val faceCrop = Bitmap.createBitmap(inputBitmap, faceX, faceY, faceWidth, faceHeight)
            val similarity = computeNormalizedCrossCorrelation(faceCrop, referenceBitmap)

            Log.d(TAG, "Face similarity score: ${"%.3f".format(similarity)} (threshold: $MATCH_THRESHOLD)")

            if (similarity >= MATCH_THRESHOLD) {
                VerificationResult.OWNER_MATCH
            } else {
                VerificationResult.FACE_MISMATCH
            }
        } catch (e: Exception) {
            Log.e(TAG, "Verification error: ${e.message}")
            VerificationResult.ENGINE_ERROR
        }
    }

    /**
     * Enroll the owner's reference face from a bitmap.
     * Call this once during initial setup with a clear front-facing photo.
     */
    fun enrollOwnerFace(bitmap: Bitmap): Boolean {
        return try {
            val referenceFile = File(context.filesDir, REFERENCE_FILE)
            val normalizedBitmap = Bitmap.createScaledBitmap(bitmap, 128, 128, true)
            FileOutputStream(referenceFile).use { fos ->
                normalizedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
            Log.i(TAG, "Owner face enrolled successfully.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enroll face: ${e.message}")
            false
        }
    }

    /**
     * Normalized Cross-Correlation between two bitmaps (grayscale, resized to 64x64).
     * This is a lightweight similarity metric suitable for on-device use.
     * Replace with a proper embedding model for production-grade accuracy.
     *
     * Returns value in [0.0, 1.0] where 1.0 = identical.
     */
    private fun computeNormalizedCrossCorrelation(a: Bitmap, b: Bitmap): Float {
        val size = 64
        val bitmapA = Bitmap.createScaledBitmap(a, size, size, true)
        val bitmapB = Bitmap.createScaledBitmap(b, size, size, true)

        var sumA = 0.0
        var sumB = 0.0
        val pixelsA = IntArray(size * size)
        val pixelsB = IntArray(size * size)
        bitmapA.getPixels(pixelsA, 0, size, 0, 0, size, size)
        bitmapB.getPixels(pixelsB, 0, size, 0, 0, size, size)

        // Convert to grayscale luminance
        val grayA = pixelsA.map { (0.299 * ((it shr 16) and 0xFF) + 0.587 * ((it shr 8) and 0xFF) + 0.114 * (it and 0xFF)) }
        val grayB = pixelsB.map { (0.299 * ((it shr 16) and 0xFF) + 0.587 * ((it shr 8) and 0xFF) + 0.114 * (it and 0xFF)) }

        val meanA = grayA.average()
        val meanB = grayB.average()

        var numerator    = 0.0
        var denomA       = 0.0
        var denomB       = 0.0

        for (i in grayA.indices) {
            val da = grayA[i] - meanA
            val db = grayB[i] - meanB
            numerator += da * db
            denomA    += da * da
            denomB    += db * db
        }

        val denominator = Math.sqrt(denomA * denomB)
        return if (denominator == 0.0) 0f
        else ((numerator / denominator + 1.0) / 2.0).toFloat() // normalize to [0,1]
    }

    fun release() {
        faceDetector?.close()
        faceDetector = null
    }
}
