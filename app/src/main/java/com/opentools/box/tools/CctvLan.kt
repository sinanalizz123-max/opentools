package com.opentools.box.tools

import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.widget.ImageView
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.io.BufferedInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

/** Shared LAN video helpers: MJPEG server + client. */
object CctvLan {
    const val PORT = 9996

    class MjpegServer(private val frames: () -> ByteArray?, private val token: String? = null) {
        @Volatile var running = false
        @Volatile private var server: SocketServer? = null

        fun start() {
            if (running) return
            running = true
            Thread {
                try {
                    server = SocketServer(java.net.ServerSocket(PORT))
                    while (running) {
                        val socket = server?.socket?.accept() ?: break
                        Thread { serve(socket) }.start()
                    }
                } catch (_: Exception) {
                } finally {
                    try { server?.socket?.close() } catch (_: Exception) { }
                    server = null
                }
            }.start()
        }

        private fun serve(socket: Socket) {
            try {
                socket.use { client ->
                    client.soTimeout = 10000
                    val input = BufferedInputStream(client.getInputStream())
                    val requestHeaders = readRequestHeaders(input)
                    val authLine = requestHeaders.lineSequence().firstOrNull {
                        it.startsWith("Authorization:", ignoreCase = true)
                    }
                    val supplied = authLine?.substringAfter(':')?.trim()?.let {
                        if (it.startsWith("Bearer ", ignoreCase = true)) it.substring(7).trim() else ""
                    }.orEmpty()
                    val out = client.getOutputStream()
                    if (token != null && !MessageDigest.isEqual(token.toByteArray(), supplied.toByteArray())) {
                        out.write("HTTP/1.0 401 Unauthorized\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                        out.flush()
                        return
                    }
                    out.write(("HTTP/1.0 200 OK\r\n" +
                        "Content-Type: multipart/x-mixed-replace; boundary=frame\r\n" +
                        "Cache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII))
                    out.flush()
                    while (running && !client.isClosed) {
                        val jpeg = frames()
                        if (jpeg == null) {
                            Thread.sleep(100)
                            continue
                        }
                        out.write(("--frame\r\nContent-Type: image/jpeg\r\nContent-Length: " +
                            jpeg.size + "\r\n\r\n").toByteArray(Charsets.US_ASCII))
                        out.write(jpeg)
                        out.write("\r\n".toByteArray(Charsets.US_ASCII))
                        out.flush()
                        Thread.sleep(120)
                    }
                }
            } catch (_: Exception) {
            }
        }

        private fun readRequestHeaders(input: BufferedInputStream): String {
            val bytes = ByteArrayOutputStream()
            var state = 0
            while (state < 4) {
                val value = input.read()
                if (value < 0) throw IOException("Camera client closed before request headers arrived")
                bytes.write(value)
                if (bytes.size() > 16 * 1024) throw IOException("Request headers are too large")
                state = when {
                    state == 0 && value == 13 -> 1
                    state == 1 && value == 10 -> 2
                    state == 2 && value == 13 -> 3
                    state == 3 && value == 10 -> 4
                    value == 13 -> 1
                    else -> 0
                }
            }
            return bytes.toString(Charsets.US_ASCII.name())
        }

        fun stop() {
            running = false
            try { server?.socket?.close() } catch (_: Exception) { }
        }

        private class SocketServer(val socket: java.net.ServerSocket)
    }

    /** Pulls MJPEG frames over a raw TCP socket so Android cleartext policy need not be relaxed globally. */
    class MjpegClient(
        private val view: ImageView,
        private val onError: (String) -> Unit,
    ) {
        @Volatile var running = false
        @Volatile private var connection: Socket? = null

        fun start(url: String, token: String? = null) {
            stop()
            running = true
            Thread {
                var socket: Socket? = null
                try {
                    val parsed = URL(url)
                    require(parsed.protocol == "http") { "Only local HTTP camera streams are supported" }
                    socket = Socket()
                    connection = socket
                    socket!!.connect(InetSocketAddress(parsed.host, if (parsed.port > 0) parsed.port else PORT), 8000)
                    socket!!.soTimeout = 10000
                    val requestPath = parsed.file.ifBlank { "/" }
                    val authorization = token?.takeIf { it.isNotBlank() }?.let { "Authorization: Bearer " + it + "\r\n" }.orEmpty()
                    socket!!.getOutputStream().apply {
                        write(("GET " + requestPath + " HTTP/1.0\r\nHost: " + parsed.host +
                            "\r\n" + authorization + "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII))
                        flush()
                    }
                    val input = BufferedInputStream(socket!!.getInputStream())
                    val responseHeaders = readHeaders(input)
                    if (!responseHeaders.startsWith("HTTP/1.0 200") && !responseHeaders.startsWith("HTTP/1.1 200")) {
                        throw IOException("Camera rejected the access token or is unavailable")
                    }
                    var buffer = ByteArray(8192)
                    var used = 0
                    val chunk = ByteArray(8192)
                    while (running) {
                        val count = input.read(chunk)
                        if (count < 0) break
                        if (used + count > MAX_BUFFER) {
                            used = 0
                            buffer = ByteArray(8192)
                        }
                        if (used + count > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, used + count))
                        System.arraycopy(chunk, 0, buffer, used, count)
                        used += count
                        while (running) {
                            val start = indexOf(buffer, used, SOI, 0)
                            if (start < 0) {
                                if (used > 1) {
                                    buffer[0] = buffer[used - 1]
                                    used = 1
                                }
                                break
                            }
                            val end = indexOf(buffer, used, EOI, start + 2)
                            if (end < 0) {
                                if (start > 0) {
                                    System.arraycopy(buffer, start, buffer, 0, used - start)
                                    used -= start
                                }
                                break
                            }
                            val frameEnd = end + EOI.size
                            val bmp = BitmapFactory.decodeByteArray(buffer, start, frameEnd - start)
                            if (bmp != null) view.post { if (running) view.setImageBitmap(bmp) }
                            val remaining = used - frameEnd
                            if (remaining > 0) System.arraycopy(buffer, frameEnd, buffer, 0, remaining)
                            used = remaining
                        }
                    }
                } catch (e: Exception) {
                    if (running) view.post { onError(e.message ?: "Connection failed") }
                } finally {
                    try { socket?.close() } catch (_: Exception) { }
                    if (connection === socket) connection = null
                }
            }.start()
        }

        private fun readHeaders(input: BufferedInputStream): String {
            val bytes = ByteArrayOutputStream()
            var state = 0
            while (state < 4) {
                val value = input.read()
                if (value < 0) throw IOException("Camera closed before HTTP headers arrived")
                bytes.write(value)
                if (bytes.size() > 16 * 1024) throw IOException("Camera response headers are too large")
                state = when {
                    state == 0 && value == 13 -> 1
                    state == 1 && value == 10 -> 2
                    state == 2 && value == 13 -> 3
                    state == 3 && value == 10 -> 4
                    value == 13 -> 1
                    else -> 0
                }
            }
            return bytes.toString(Charsets.US_ASCII.name())
        }

        private fun indexOf(data: ByteArray, length: Int, needle: ByteArray, from: Int): Int {
            if (needle.isEmpty() || length < needle.size || from >= length) return -1
            val last = length - needle.size
            for (i in from..last) {
                var match = true
                for (j in needle.indices) {
                    if (data[i + j] != needle[j]) { match = false; break }
                }
                if (match) return i
            }
            return -1
        }

        fun stop() {
            running = false
            try { connection?.close() } catch (_: Exception) { }
            connection = null
        }

        companion object {
            private const val MAX_BUFFER = 2 * 1024 * 1024
            private val SOI = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
            private val EOI = byteArrayOf(0xFF.toByte(), 0xD9.toByte())
        }
    }

    fun nv21ToJpeg(nv21: ByteArray, w: Int, h: Int): ByteArray {
        val out = ByteArrayOutputStream()
        YuvImage(nv21, ImageFormat.NV21, w, h, null).compressToJpeg(Rect(0, 0, w, h), 60, out)
        return out.toByteArray()
    }
}
