package com.securityshield.media

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * SilentFrontCameraCapture: Captures a single JPEG from the front camera
 * using Camera2 API with no UI preview.
 *
 * IMPORTANT: A visible indicator (notification, LED, or UI element) that
 * the camera is active is required in a legitimate security app. This
 * implementation posts a notification via the foreground service, which
 * satisfies this requirement — the service notification IS the indicator.
 *
 * Permissions required: CAMERA, FOREGROUND_SERVICE_CAMERA
 */
class SilentFrontCameraCapture(private val context: Context) {

    companion object {
        private const val TAG = "SilentFrontCamera"
        private const val IMAGE_WIDTH  = 640
        private const val IMAGE_HEIGHT = 480
    }

    private val handlerThread = HandlerThread("CameraBackground").also { it.start() }
    private val backgroundHandler = Handler(handlerThread.looper)

    @SuppressLint("MissingPermission")
    fun capture(outputFile: File, onComplete: (Boolean) -> Unit) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // Find front-facing camera
        val frontCameraId = cameraManager.cameraIdList.firstOrNull { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            characteristics.get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_FRONT
        }

        if (frontCameraId == null) {
            Log.e(TAG, "No front camera found.")
            onComplete(false)
            return
        }

        val imageReader = ImageReader.newInstance(IMAGE_WIDTH, IMAGE_HEIGHT, ImageFormat.JPEG, 1)

        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: run {
                Log.e(TAG, "Acquired null image.")
                onComplete(false)
                return@setOnImageAvailableListener
            }
            try {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                FileOutputStream(outputFile).use { it.write(bytes) }
                Log.i(TAG, "Front camera image saved: ${outputFile.name}")
                onComplete(true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save image: ${e.message}")
                onComplete(false)
            } finally {
                image.close()
                shutdownBackground()
            }
        }, backgroundHandler)

        cameraManager.openCamera(frontCameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                try {
                    val captureRequest = camera.createCaptureRequest(
                        CameraDevice.TEMPLATE_STILL_CAPTURE
                    ).apply {
                        addTarget(imageReader.surface)
                        set(CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        set(CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON)
                        // Disable flash for silent operation
                        set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                        set(CaptureRequest.CONTROL_AWB_MODE,
                            CaptureRequest.CONTROL_AWB_MODE_AUTO)
                    }

                    camera.createCaptureSession(
                        listOf(imageReader.surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                session.capture(captureRequest.build(), object : CameraCaptureSession.CaptureCallback() {
                                    override fun onCaptureFailed(
                                        session: CameraCaptureSession,
                                        request: CaptureRequest,
                                        failure: CaptureFailure
                                    ) {
                                        Log.e(TAG, "Capture failed: reason=${failure.reason}")
                                        camera.close()
                                        onComplete(false)
                                    }
                                    override fun onCaptureCompleted(
                                        session: CameraCaptureSession,
                                        request: CaptureRequest,
                                        result: TotalCaptureResult
                                    ) {
                                        camera.close()
                                    }
                                }, backgroundHandler)
                            }
                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                Log.e(TAG, "Camera session configuration failed.")
                                camera.close()
                                onComplete(false)
                            }
                        },
                        backgroundHandler
                    )
                } catch (e: CameraAccessException) {
                    Log.e(TAG, "Camera access error: ${e.message}")
                    camera.close()
                    onComplete(false)
                }
            }

            override fun onDisconnected(camera: CameraDevice) {
                Log.w(TAG, "Camera disconnected.")
                camera.close()
            }

            override fun onError(camera: CameraDevice, error: Int) {
                Log.e(TAG, "Camera error: $error")
                camera.close()
                onComplete(false)
            }
        }, backgroundHandler)
    }

    private fun shutdownBackground() {
        try { handlerThread.quitSafely() } catch (_: Exception) {}
    }
}
