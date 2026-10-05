package com.amniscient.price.ui.scan

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * CameraX preview + live analysis + still capture, with tap-to-focus and pinch-to-zoom.
 * [onTapFocus] reports where the user tapped (in view pixels) so the UI can draw a focus ring.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun CameraPreview(
    analyzer: ImageAnalysis.Analyzer,
    imageCapture: ImageCapture,
    onCamera: (Camera) -> Unit,
    modifier: Modifier = Modifier,
    onTapFocus: (x: Float, y: Float) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    DisposableEffect(lifecycleOwner, analyzer) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val analysisExecutor = Executors.newSingleThreadExecutor()
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(analysisExecutor, analyzer) }
            provider.unbindAll()
            val camera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
                imageCapture,
            )
            onCamera(camera)

            val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val current = camera.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                    camera.cameraControl.setZoomRatio(current * detector.scaleFactor)
                    return true
                }
            })
            previewView.setOnTouchListener { _, event ->
                scale.onTouchEvent(event)
                if (event.action == MotionEvent.ACTION_UP && !scale.isInProgress && event.pointerCount == 1) {
                    val point = previewView.meteringPointFactory.createPoint(event.x, event.y)
                    camera.cameraControl.startFocusAndMetering(
                        FocusMeteringAction.Builder(point).setAutoCancelDuration(4, TimeUnit.SECONDS).build(),
                    )
                    onTapFocus(event.x, event.y)
                }
                true
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            previewView.setOnTouchListener(null)
            runCatching { providerFuture.get().unbindAll() }
            analysisExecutor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
