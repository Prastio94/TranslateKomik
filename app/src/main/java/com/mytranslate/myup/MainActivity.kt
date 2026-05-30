package com.mytranslate.myup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private val REQUEST_OVERLAY_PERMISSION = 1001
    private val REQUEST_MEDIA_PROJECTION = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)

        btnStart.setOnClickListener {
            checkAndStartOverlay()
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, FloatingService::class.java))
            stopService(Intent(this, ScreenCaptureService::class.java))
            Toast.makeText(this, "Overlay Dimatikan", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndStartOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
            } else {
                requestScreenCapturePermission()
            }
        } else {
            requestScreenCapturePermission()
        }
    }

    private fun requestScreenCapturePermission() {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                requestScreenCapturePermission()
            } else {
                Toast.makeText(this, "Izin Overlay ditolak! Aplikasi tidak bisa berjalan.", Toast.LENGTH_SHORT).show()
            }
        }

        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                // Menyimpan token izin ke companion object ScreenCaptureService agar aman digunakan di Chrome
                ScreenCaptureService.resultCode = resultCode
                ScreenCaptureService.resultData = data

                // Menjalankan service mengambang
                startService(Intent(this, FloatingService::class.java))
                Toast.makeText(this, "Aplikasi Aktif! Buka Google Chrome sekarang.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Izin Rekam Layar Ditolak!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
