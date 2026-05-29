package com.securityshield.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object CameraCapture {
    private const val TAG = "CameraCapture"

    /**
     * Captures a single front-camera image.
     * NOTE: Android 9+ always shows the green camera indicator dot — this is intentional
     * and required behavior. We do not attempt to suppress it.
     */
    fun capture(context: Context, onCaptured: (String) -> Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build()

                // Need a LifecycleOwner — use a temporary one
                val lifecycleOwner = context as? LifecycleOwner
                    ?: run {
                        Log.e(TAG, "Context is not LifecycleOwner — cannot bind camera")
                        return@addListener
                    }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageCapture)

                val photoFile = createOutputFile(context)
                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            Log.i(TAG, "Photo saved: ${photoFile.absolutePath}")
                            cameraProvider.unbindAll()
                            onCaptured(photoFile.absolutePath)
                        }
                        override fun onError(exception: ImageCaptureException) {
                            Log.e(TAG, "Camera capture failed", exception)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera setup failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun createOutputFile(context: Context): File {
        val dir = File(context.filesDir, "security_captures").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File(dir, "intruder_$timestamp.jpg")
    }
}
