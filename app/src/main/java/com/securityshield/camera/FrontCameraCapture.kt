package com.securityshield.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FrontCameraCapture(private val context: Context) {

    companion object {
        const val TAG = "FrontCamera"
    }

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var faceDetector: FaceDetector? = null

    init {
        initFaceDetector()
    }

    private fun initFaceDetector() {
        try {
            val options = FaceDetectorOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath("face_detection_short_range.tflite")
                        .build()
                )
                .setRunningMode(RunningMode.IMAGE)
                .setMinDetectionConfidence(0.7f)
                .build()
            faceDetector = FaceDetector.createFromOptions(context, options)
            Log.d(TAG, "MediaPipe FaceDetector initialized")
        } catch (e: Exception) {
            Log.w(TAG, "FaceDetector init failed (model asset may be missing): ${e.message}")
        }
    }

    /**
     * Captures a photo using the front camera.
     * Note: Android will display the green camera indicator dot automatically.
     * This is by design — transparent operation.
     */
    suspend fun capturePhoto(outputDir: File, timestamp: String): File? {
        return suspendCancellableCoroutine { continuation ->
            val outputFile = File(outputDir, "intruder_$timestamp.jpg")

            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()

                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()

                    val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                    cameraProvider.unbindAll()

                    // Bind to a headless lifecycle for background capture
                    // Android green dot will appear — this is the correct behavior
                    if (context is LifecycleOwner) {
                        cameraProvider.bindToLifecycle(
                            context as LifecycleOwner,
                            cameraSelector,
                            imageCapture
                        )
                    }

                    val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                    imageCapture.takePicture(
                        outputOptions,
                        cameraExecutor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                Log.d(TAG, "Photo saved: ${outputFile.name}")
                                cameraProvider.unbindAll()
                                continuation.resume(outputFile)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Log.e(TAG, "Photo capture failed: ${exception.message}")
                                cameraProvider.unbindAll()
                                continuation.resumeWithException(exception)
                            }
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Camera binding failed: ${e.message}", e)
                    continuation.resumeWithException(e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    /**
     * Detects if a face is present and returns face count.
     * Used with MediaPipe for owner vs intruder comparison.
     */
    fun detectFaces(imagePath: String): Int {
        return try {
            val mpImage = com.google.mediapipe.framework.image.BitmapImageBuilder(
                android.graphics.BitmapFactory.decodeFile(imagePath)
            ).build()
            val result = faceDetector?.detect(mpImage)
            result?.detections()?.size ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Face detection error: ${e.message}")
            0
        }
    }

    fun shutdown() {
        cameraExecutor.shutdown()
        faceDetector?.close()
    }
}
