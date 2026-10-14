package com.opentools.box.tools

import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.widget.ImageView
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.Collections

/**
 * Shared LAN video helpers: MJPEG server + client.
 * Original implementation.
 */
object CctvLan {
    const val PORT = 9996

    /** Serves JPEG frames as multipart MJPEG until stopped. */
    class MjpegServer(private val frames: () -> ByteArray?) {
        @Volatile var running = false
        private var server: ServerSocket? = null

        fun start() {
            running = true
            Thread {
                try {
                    server = ServerSocket(PORT)
                    while (running) {
                        val socket = server?.accept() ?: break
                        Thread { serve(socket) }.start()
                    }
                } catch (_: Exception) { }
            }.start()
        }

    private fun serve(socket: Socket) {
        var s: Socket? = null
        try {
            s = socket
            val input = s.getInputStream()
                    val buf = ByteArray(1024)
                    input.read(buf)
                    val out = s.getOutputStream()
                    out.write(("HTTP/1.0 200 OK\r\n" +
                        "Content-Type: multipart/x-mixed-replace; boundary=frame\r\n\r\n").toByteArray())
                    out.flush()
                    while (running && !s.isClosed) {
                        val jpeg = frames()
                        if (jpeg == null) { Thread.sleep(200); continue }
                        out.write(("--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${jpeg.size}\r\n\r\n").toByteArray())
                        out.write(jpeg)
                        out.write("\r\n".toByteArray())
                        out.flush()
                        Thread.sleep(120)
                    }
            } catch (_: Exception) { }
            finally { try { s?.close() } catch (_: Exception) { } }
        }

        fun stop() {
            running = false
            try { server?.close() } catch (_: Exception) { }
        }
    }

    /** Pulls MJPEG frames into an ImageView. */
    class MjpegClient(
        private val view: ImageView,
        private val onError: (String) -> Unit,
    ) {
        @Volatile var running = false

        fun start(url: String) {
            running = true
            Thread {
                try {
                    val conn = URL(url).openConnection()
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    val input = conn.getInputStream()
                    val buf = mutableListOf<Byte>()
                    val chunk = ByteArray(8192)
                    var pending: ByteArray? = null
                    while (running) {
                        val n = input.read(chunk)
                        if (n < 0) break
                        for (i in 0 until n) buf.add(chunk[i])
                        // Find JPEG SOI..EOI markers.
                        val bytes = buf.toByteArray()
                        var start = indexOf(bytes, byteArrayOf(0xFF.toByte(), 0xD8.toByte()), 0)
                        var end = if (start >= 0) indexOf(bytes, byteArrayOf(0xFF.toByte(), 0xD9.toByte()), start + 2) else -1
                        if (start >= 0 && end > start) {
                            val jpeg = bytes.copyOfRange(start, end + 2)
                            buf.subList(0, end + 2).clear()
                            val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                            if (bmp != null) {
                                pending?.let { }
                                pending = null
                                view.post { view.setImageBitmap(bmp) }
                            }
                        }
                        if (buf.size > 2_000_000) buf.clear()
                    }
                } catch (e: Exception) {
                    if (running) view.post { onError(e.message ?: "connection failed") }
                }
            }.start()
        }

        private fun indexOf(hay: ByteArray, needle: ByteArray, from: Int): Int {
            outer@ for (i in from..hay.size - needle.size) {
                for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
                return i
            }
            return -1
        }

        fun stop() { running = false }
    }

    /** Convert NV21 data to JPEG bytes. */
    fun nv21ToJpeg(nv21: ByteArray, w: Int, h: Int): ByteArray {
        val out = ByteArrayOutputStream()
        YuvImage(nv21, ImageFormat.NV21, w, h, null).compressToJpeg(Rect(0, 0, w, h), 60, out)
        return out.toByteArray()
    }
}
