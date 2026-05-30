package com.mytranslate.myup

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class MangaAccessibilityService : AccessibilityService() {

    private val scrollReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.mytranslate.myup.TRIGGER_SCROLL") {
                // Ambil koordinat Y teks terbawah dari sinyal broadcast
                val lastTextY = intent.getIntExtra("LAST_TEXT_Y", 0)
                scrollScreenDown(lastTextY)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerReceiver(scrollReceiver, IntentFilter("com.mytranslate.myup.TRIGGER_SCROLL"))
    }

    private fun scrollScreenDown(lastTextY: Int) {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        // LOGIKA DINAMIS: Jika koordinat Y terdeteksi masuk akal, taruh jari di titik tersebut.
        // Jika layar kosong atau teks tidak ada, gunakan default aman (75% tinggi layar)
        val startY = if (lastTextY > 150 && lastTextY < height) {
            lastTextY.toFloat()
        } else {
            height * 0.75f
        }

        val startX = width / 2f
        
        // Jari palsu akan menyeret layar dari titik teks terbawah (startY) menuju ke atas (15% dari atas layar).
        // Ini akan memindahkan seluruh kalimat yang SUDAH DIBACA keluar dari layar dengan sempurna!
        val endX = width / 2f
        val endY = height * 0.15f 

        // Pengaman ekstra agar arah tarikan selalu lurus ke atas
        val finalEndY = if (endY >= startY) startY - 200f else endY

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, finalEndY)
        }

        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 450))
        
        dispatchGesture(gestureBuilder.build(), null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(scrollReceiver)
        } catch (e: Exception) {}
    }
}
