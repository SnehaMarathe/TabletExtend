package com.example.host

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat   // ← add this
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.Executors

class ScreenStreamer(
    private val appContext: Context,
    private val onError: (Throwable) -> Unit
) {
    private var projection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var server: ServerSocket? = null
    private var inputServer: ServerSocket? = null
    private val exec = Executors.newFixedThreadPool(4)

    fun startServer(port: Int) {
        exec.execute {
            try {
                server = ServerSocket(port)
                inputServer = ServerSocket(port + 1)
                while (!server!!.isClosed) {
                    val frameClient = server!!.accept()
                    val inputClient = inputServer!!.accept()
                    handleClient(frameClient, inputClient)
                }
            } catch (t: Throwable) { onError(t) }
        }
    }

    fun startProjection(resultCode: Int, data: Intent) {
        val mpm = appContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(resultCode, data)

        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        // FIX #1: Use PixelFormat.RGBA_8888
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        projection!!.createVirtualDisplay(
            "cap",
            width, height, density,
            0,
            imageReader!!.surface,
            null, null
        )
    }

    private fun handleClient(frameSocket: Socket, inputSocket: Socket) {
        exec.execute { inputLoop(inputSocket) }
        exec.execute {
            try {
                val dos = DataOutputStream(frameSocket.getOutputStream())
                while (!frameSocket.isClosed) {
                    val img = imageReader?.acquireLatestImage()

                    // FIX #2: avoid 'continue' in lambda
                    if (img == null) {
                        Thread.sleep(8)
                    } else {
                        val plane = img.planes[0]
                        val buffer: ByteBuffer = plane.buffer
                        val rowStride = plane.rowStride
                        val w = img.width
                        val h = img.height

                        val tight = ByteArray(w * h * 4)
                        val arr = ByteArray(buffer.remaining())
                        buffer.get(arr)
                        var dstPos = 0
                        for (y in 0 until h) {
                            val srcPos = y * rowStride
                            System.arraycopy(arr, srcPos, tight, dstPos, w * 4)
                            dstPos += w * 4
                        }

                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.copyPixelsFromBuffer(ByteBuffer.wrap(tight))

                        val baos = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, 60, baos)
                        val bytes = baos.toByteArray()
                        dos.writeInt(bytes.size)
                        dos.write(bytes)
                        dos.flush()
                        img.close()
                    }
                }
            } catch (t: Throwable) { onError(t) }
        }
    }

    private fun inputLoop(sock: Socket) {
        sock.getInputStream().bufferedReader().use { br ->
            // FIX #3: fully-qualified reference to the service singleton
            val injector = com.example.host.input.InjectorAccessibilityService.controller
            if (injector == null) return@use
            while (true) {
                val line = br.readLine() ?: break
                val parts = line.split(" ")
                if (parts.size == 3) {
                    val x = parts[1].toFloat()
                    val y = parts[2].toFloat()
                    injector.injectTap(x, y)
                }
            }
        }
    }

    fun stopAll() {
        try { server?.close() } catch (_: Throwable) {}
        try { inputServer?.close() } catch (_: Throwable) {}
        try { projection?.stop() } catch (_: Throwable) {}
        try { imageReader?.close() } catch (_: Throwable) {}
        exec.shutdownNow()
    }
}
