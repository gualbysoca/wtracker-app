package com.wtracker.field.services.camera

import android.content.Context
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.wtracker.field.utils.AppError
import com.wtracker.field.utils.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Wrapper de CameraX para captura instantánea. Deliberadamente NO ofrece selección de
 * galería, filtros, zoom digital ni edición: sólo vista previa + disparo con cámara trasera.
 */
class InstantCameraController(context: Context) {

    private val appContext = context.applicationContext
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null

    private val orientationEventListener = object : OrientationEventListener(appContext) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN) return
            val rotation = when (orientation) {
                in 45..134 -> Surface.ROTATION_270
                in 135..224 -> Surface.ROTATION_180
                in 225..314 -> Surface.ROTATION_90
                else -> Surface.ROTATION_0
            }
            imageCapture?.targetRotation = rotation
        }
    }

    suspend fun bindToLifecycle(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
    ): AppResult<Unit> = try {
        val provider = obtainCameraProvider()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(surfaceProvider) }
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(ImageCapture.FLASH_MODE_AUTO)
            .build()

        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
        imageCapture = capture
        orientationEventListener.enable()
        AppResult.Success(Unit)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (exception: Exception) {
        AppResult.Failure(AppError.Camera("No se pudo iniciar la cámara: ${exception.message.orEmpty()}"))
    }

    suspend fun takePicture(outputFile: File): AppResult<File> {
        val capture = imageCapture ?: return AppResult.Failure(AppError.Camera("La cámara aún no está lista."))
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        return suspendCancellableCoroutine { continuation ->
            capture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(appContext),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        if (continuation.isActive) continuation.resume(AppResult.Success(outputFile))
                    }

                    override fun onError(exception: ImageCaptureException) {
                        if (!continuation.isActive) return
                        continuation.resume(
                            AppResult.Failure(AppError.Camera("Error al capturar la foto: ${exception.message.orEmpty()}")),
                        )
                    }
                },
            )
        }
    }

    fun release() {
        orientationEventListener.disable()
        cameraProvider?.unbindAll()
        imageCapture = null
    }

    private suspend fun obtainCameraProvider(): ProcessCameraProvider {
        cameraProvider?.let { return it }
        val provider = suspendCancellableCoroutine { continuation ->
            val providerFuture = ProcessCameraProvider.getInstance(appContext)
            providerFuture.addListener(
                {
                    try {
                        continuation.resume(providerFuture.get())
                    } catch (exception: Exception) {
                        continuation.resumeWithException(exception)
                    }
                },
                ContextCompat.getMainExecutor(appContext),
            )
        }
        cameraProvider = provider
        return provider
    }
}
