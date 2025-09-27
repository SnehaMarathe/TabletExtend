package com.example.host

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var streamer: ScreenStreamer

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data != null) {
            streamer.startProjection(res.resultCode, res.data!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val portField = findViewById<EditText>(R.id.portField)
        val startBtn = findViewById<Button>(R.id.startServerBtn)
        val startCaptureBtn = findViewById<Button>(R.id.startCaptureBtn)

        streamer = ScreenStreamer(applicationContext) { /* onError */ }

        startBtn.setOnClickListener {
            val port = portField.text.toString().toIntOrNull() ?: 5555
            streamer.startServer(port)
        }

        startCaptureBtn.setOnClickListener {
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(mpm.createScreenCaptureIntent())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        streamer.stopAll()
    }
}
