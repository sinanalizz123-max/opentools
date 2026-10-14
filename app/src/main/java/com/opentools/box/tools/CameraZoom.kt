package com.opentools.box.tools

import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.TextureView
import android.view.View

class CameraZoom(
    private val view: TextureView,
    private val onZoomChanged: (Float) -> Unit,
) {
    var maxZoom = 4f
    var zoom = 1f
        private set

    fun setZoom(value: Float) {
        zoom = value.coerceIn(1f, maxZoom.coerceAtLeast(1f))
    }

    private val detector = ScaleGestureDetector(view.context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoom = (zoom * detector.scaleFactor).coerceIn(1f, maxZoom.coerceAtLeast(1f))
                onZoomChanged(zoom)
                return true
            }
        })

    fun onTouchEvent(event: MotionEvent): Boolean {
        detector.onTouchEvent(event)
        return detector.isInProgress
    }

    fun attach(onSingleTap: ((MotionEvent) -> Unit)? = null) {
        view.setOnTouchListener { v, event ->
            detector.onTouchEvent(event)
            if (detector.isInProgress) return@setOnTouchListener true
            if (event.action == MotionEvent.ACTION_UP) v?.performClick()
            if (onSingleTap != null && event.action == MotionEvent.ACTION_DOWN) {
                onSingleTap(event)
            }
            onSingleTap != null
        }
    }

    fun detach() {
        view.setOnTouchListener(null)
    }
}
