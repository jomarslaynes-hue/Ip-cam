package com.obs.droidcampro.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Range
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraController(private val context: Context) {

    private val TAG = "CameraController"
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var preview: Preview? = null

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // Callback for delivering compressed JPEG frames to the MJPEG/WebRTC server
    var onFrameAvailable: ((ByteArray) -> Unit)? = null

    // State
    val isFrontCamera = MutableStateFlow(false)
    val currentResolution = MutableStateFlow(Size(1920, 1080)) // 1080p default
    val targetFps = MutableStateFlow(30)
    val zoomRatio = MutableStateFlow(1.0f)
    val exposureCompensation = MutableStateFlow(0)
    val isTorchOn = MutableStateFlow(false)
    val compressionQuality = MutableStateFlow(80) // 1-100

    fun initialize(lifecycleOwner: LifecycleOwner, previewSurfaceProvider: Preview.SurfaceProvider? = null) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCamera(lifecycleOwner, previewSurfaceProvider)
        }, ContextCompat.getMainExecutor(context))
    }

    @SuppressLint("UnsafeOptInUsageError")
    fun bindCamera(lifecycleOwner: LifecycleOwner, previewSurfaceProvider: Preview.SurfaceProvider? = null) {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        val cameraSelector = if (isFrontCamera.value) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        val targetSize = currentResolution.value

        // Setup live preview
        preview = Preview.Builder()
            .setTargetResolution(targetSize)
            .build()
        previewSurfaceProvider?.let { preview?.setSurfaceProvider(it) }

        // Setup frame analysis pipeline (for streaming to OBS & HTTP)
        imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(targetSize)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()

        imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
            processImageProxy(imageProxy)
        }

        try {
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )

            // Configure FPS via Camera2 Interop
            applyCamera2Settings()
        } catch (e: Exception) {
            Log.e(TAG, "Binding camera failed", e)
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun applyCamera2Settings() {
        val cam = camera ?: return
        val camera2Control = Camera2CameraControl.from(cam.cameraControl)
        val fps = targetFps.value

        val options = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(fps, fps))
            .setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            .build()

        camera2Control.setCaptureRequestOptions(options)
    }

    private fun processImageProxy(image: ImageProxy) {
        try {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), compressionQuality.value, out)
            val jpegBytes = out.toByteArray()

            onFrameAvailable?.invoke(jpegBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Frame conversion error", e)
        } finally {
            image.close()
        }
    }

    fun switchCamera(lifecycleOwner: LifecycleOwner, previewProvider: Preview.SurfaceProvider? = null) {
        isFrontCamera.value = !isFrontCamera.value
        bindCamera(lifecycleOwner, previewProvider)
    }

    fun setResolution(width: Int, height: Int, lifecycleOwner: LifecycleOwner, previewProvider: Preview.SurfaceProvider? = null) {
        currentResolution.value = Size(width, height)
        bindCamera(lifecycleOwner, previewProvider)
    }

    fun setFps(fps: Int) {
        targetFps.value = fps
        applyCamera2Settings()
    }

    fun setZoom(ratio: Float) {
        val clamped = ratio.coerceIn(1.0f, 10.0f)
        zoomRatio.value = clamped
        camera?.cameraControl?.setZoomRatio(clamped)
    }

    fun setExposure(index: Int) {
        exposureCompensation.value = index
        camera?.cameraControl?.setExposureCompensationIndex(index)
    }

    fun toggleTorch(): Boolean {
        val newState = !isTorchOn.value
        isTorchOn.value = newState
        camera?.cameraControl?.enableTorch(newState)
        return newState
    }

    fun triggerFocus(meteringPoint: MeteringPoint) {
        val action = FocusMeteringAction.Builder(meteringPoint, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    fun shutdown() {
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
    }
}
