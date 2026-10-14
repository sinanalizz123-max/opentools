package com.opentools.box.tools

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Build
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import kotlin.math.abs

/**
 * Aspect-correct Camera2 preview on a TextureView (center-crop, no stretch).
 * Adapted from the standard Camera2 sample transform.
 */
object CameraPreview {

    /** Pick the largest supported size whose aspect matches the view. */
    fun chooseSize(
        cameraManager: CameraManager,
        cameraId: String,
        viewWidth: Int,
        viewHeight: Int,
    ): Size? {
        if (viewWidth <= 0 || viewHeight <= 0) return null
        return try {
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: return null
            val sizes = map.getOutputSizes(SurfaceTexture::class.java)?.toList()
                ?: return null
            val targetRatio = viewWidth.toDouble() / viewHeight
            sizes.filter { size ->
                    if (size.width <= 0 || size.height <= 0) false
                    else abs(size.width.toDouble() / size.height - targetRatio) < 0.05
                }
                .maxByOrNull { it.width * it.height }
                ?: sizes.maxByOrNull { it.width * it.height }
        } catch (_: Exception) {
            null
        }
    }

    /** Apply a center-crop transform so [previewWidth]x[previewHeight] fills the view. */
    fun fit(view: TextureView, previewWidth: Int, previewHeight: Int) {
        val vw = view.width
        val vh = view.height
        if (vw <= 0 || vh <= 0 || previewWidth <= 0 || previewHeight <= 0) return
        try {
            val rotation = if (Build.VERSION.SDK_INT >= 30) {
                view.display?.rotation ?: Surface.ROTATION_0
            } else {
                @Suppress("DEPRECATION")
                (view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                    .defaultDisplay.rotation
            }
            val matrix = Matrix()
            val viewRect = RectF(0f, 0f, vw.toFloat(), vh.toFloat())
            // Sensor buffer is landscape; when the device is portrait (0/180)
            // the buffer axes are swapped relative to the view.
            val portrait = rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180
            val bufW = if (portrait) previewHeight.toFloat() else previewWidth.toFloat()
            val bufH = if (portrait) previewWidth.toFloat() else previewHeight.toFloat()
            val bufferRect = RectF(0f, 0f, bufW, bufH)
            val centerX = viewRect.centerX()
            val centerY = viewRect.centerY()
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = maxOf(vw / bufW, vh / bufH)
            matrix.postScale(scale, scale, centerX, centerY)
            if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
                matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
            }
            view.setTransform(matrix)
        } catch (_: Exception) {
        }
    }
}
