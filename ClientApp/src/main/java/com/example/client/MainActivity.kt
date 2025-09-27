package com.example.client

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
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect

class MainActivity : AppCompatActivity() {
    private lateinit var surface: SurfaceView
    private val exec = Executors.newFixedThreadPool(3)
    private var frameSocket: Socket? = null
    private var inputSocket: Socket? = null
    private var inputOut: PrintWriter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        surface = findViewById(R.id.surface)
        val hostField = findViewById<EditText>(R.id.hostField)
        val portField = findViewById<EditText>(R.id.portField)
        val connectBtn = findViewById<Button>(R.id.connectBtn)

        connectBtn.setOnClickListener {
            val host = hostField.text.toString()
            val port = portField.text.toString().toIntOrNull() ?: 5555
            connect(host, port)
        }

        surface.setOnTouchListener { _, ev ->
            val x = ev.x
            val y = ev.y
            inputOut?.println("DOWN $x $y")
            inputOut?.flush()
            true
        }
    }

    private fun connect(host: String, port: Int) {
        exec.execute {
            try {
                frameSocket = Socket(host, port)
                inputSocket = Socket(host, port + 1)
                inputOut = PrintWriter(inputSocket!!.getOutputStream(), true)

                val dis = DataInputStream(frameSocket!!.getInputStream())
                while (true) {
                    val len = dis.readInt()
                    val buf = ByteArray(len)
                    dis.readFully(buf)
                    val bmp = BitmapFactory.decodeByteArray(buf, 0, buf.size) ?: continue

                    val holder: SurfaceHolder = surface.holder
                    val c: Canvas = holder.lockCanvas() ?: continue
                    val dest = Rect(0, 0, c.width, c.height)
                    c.drawBitmap(bmp, null, dest, null)
                    holder.unlockCanvasAndPost(c)
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { frameSocket?.close() } catch (_: Throwable) {}
        try { inputSocket?.close() } catch (_: Throwable) {}
        exec.shutdownNow()
    }
}
