package com.example.client

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import java.io.DataInputStream
import java.io.PrintWriter
import java.net.Socket
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {
    private lateinit var surface: SurfaceView
    private val exec = Executors.newFixedThreadPool(3)
    private var frameSocket: Socket? = null
    private var inputSocket: Socket? = null
    private var inputOut: PrintWriter? = null
    private var surfaceReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        surface = findViewById(R.id.surface)
        surface.holder.addCallback(this)

        val hostField = findViewById<EditText>(R.id.hostField)
        val portField = findViewById<EditText>(R.id.portField)
        val connectBtn = findViewById<Button>(R.id.connectBtn)

        connectBtn.setOnClickListener {
            val host = hostField.text.toString().trim()
            val port = portField.text.toString().toIntOrNull() ?: 5555
            connect(host, port)
        }

        surface.setOnTouchListener { _, ev ->
            // MVP: simple taps only
            inputOut?.println("DOWN ${ev.x} ${ev.y}")
            inputOut?.flush()
            true
        }
    }

    private fun connect(host: String, port: Int) {
        exec.execute {
            try {
                frameSocket?.close()
                inputSocket?.close()

                frameSocket = Socket(host, port)
                inputSocket = Socket(host, port + 1)
                inputOut = PrintWriter(inputSocket!!.getOutputStream(), true)

                val dis = DataInputStream(frameSocket!!.getInputStream())

                while (true) {
                    val len = try { dis.readInt() } catch (_: Throwable) { break }
                    if (len <= 0 || len > 50_000_000) continue // guard

                    val buf = ByteArray(len)
                    try { dis.readFully(buf) } catch (_: Throwable) { break }

                    val opts = BitmapFactory.Options().apply {
                        inPreferredConfig = android.graphics.Bitmap.Config.RGB_565 // less RAM
                    }
                    val bmp = BitmapFactory.decodeByteArray(buf, 0, buf.size, opts) ?: continue

                    if (!surfaceReady) continue
                    val holder: SurfaceHolder = surface.holder
                    if (!holder.surface.isValid) continue

                    val c: Canvas = holder.lockCanvas() ?: continue
                    try {
                        val dest = Rect(0, 0, c.width, c.height)
                        c.drawBitmap(bmp, null, dest, null)
                    } catch (_: Throwable) {
                        // ignore draw race
                    } finally {
                        try { holder.unlockCanvasAndPost(c) } catch (_: Throwable) {}
                    }
                }
            } catch (_: Throwable) {
                // swallow; show toast/log in a real app
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) { surfaceReady = true }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { }
    override fun surfaceDestroyed(holder: SurfaceHolder) { surfaceReady = false }

    override fun onDestroy() {
        super.onDestroy()
        try { frameSocket?.close() } catch (_: Throwable) {}
        try { inputSocket?.close() } catch (_: Throwable) {}
        exec.shutdownNow()
    }
}
