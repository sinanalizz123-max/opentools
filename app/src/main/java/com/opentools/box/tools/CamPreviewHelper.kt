package com.opentools.box.tools
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import androidx.core.app.ActivityCompat
object CamPreviewHelper {
    private var dev: CameraDevice? = null; private var sess: CameraCaptureSession? = null
    private var thread: HandlerThread? = null; private var handler: Handler? = null
    fun open(ctx: android.app.Activity, v: TextureView) {
        try {
            if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { ActivityCompat.requestPermissions(ctx, arrayOf(Manifest.permission.CAMERA), 99); return }
            val man = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = man.cameraIdList.firstOrNull() ?: return
            thread = HandlerThread("camov").also { it.start() }; handler = Handler(thread!!.looper)
            man.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(c: CameraDevice) { dev = c
                    try { val tex = v.surfaceTexture ?: return; val opt = CameraPreview.chooseSize(man, id, v.width.coerceAtLeast(1), v.height.coerceAtLeast(1)); tex.setDefaultBufferSize(opt?.width ?: 640, opt?.height ?: 480); val surf = Surface(tex)
                    val req = c.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(surf) }
                    c.createCaptureSession(listOf(surf), object : CameraCaptureSession.StateCallback() { override fun onConfigured(s: CameraCaptureSession) { sess = s; try { s.setRepeatingRequest(req.build(), null, handler) } catch (_: Exception) { } } override fun onConfigureFailed(s: CameraCaptureSession) = Unit }, handler) } catch (_: Exception) { } }
                override fun onDisconnected(c: CameraDevice) { c.close(); if (dev === c) dev = null }
                override fun onError(c: CameraDevice, e: Int) { c.close(); if (dev === c) dev = null }
            }, handler)
        } catch (_: Exception) { }
    }
    fun close() { try { sess?.close() } catch (_: Exception) { }; sess = null; try { dev?.close() } catch (_: Exception) { }; dev = null; thread?.quitSafely(); thread = null }
}
