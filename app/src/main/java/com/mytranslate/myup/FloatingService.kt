package com.mytranslate.myup

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class FloatingService : Service(), TextToSpeech.OnInitListener {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: LinearLayout
    private lateinit var btnCapture: TextView
    private lateinit var btnMode: TextView
    private lateinit var btnTextVis: TextView
    private lateinit var btnScroll: TextView
    private lateinit var btnCrop: TextView // TOMBOL BARU
    private lateinit var tvResult: TextView
    
    private var ttsEngine: TextToSpeech? = null
    private var isTtsReady = false

    private var isAutoScanActive = false
    private var isTranslateMode = true 
    private var isTextVisible = true   
    private var isAutoScrollActive = false 
    
    private val autoScanHandler = Handler(Looper.getMainLooper())
    private val ttsDelayHandler = Handler(Looper.getMainLooper())
    private val autoScrollHandler = Handler(Looper.getMainLooper())
    private var lastSpokenText = ""
    private var lastTextYCoordinate = 0

    private val autoScanRunnable = object : Runnable {
        override fun run() {
            if (isAutoScanActive) {
                if (isTtsReady && ttsEngine?.isSpeaking == true) {
                    autoScanHandler.postDelayed(this, 1000)
                    return
                }

                val captureIntent = Intent(this@FloatingService, ScreenCaptureService::class.java).apply {
                    action = "ACTION_CAPTURE"
                    putExtra("MODE_TRANSLATE", isTranslateMode)
                }
                startService(captureIntent)
                autoScanHandler.postDelayed(this, 4000)
            }
        }
    }

    private val autoScrollRunnable = object : Runnable {
        override fun run() {
            if (isAutoScrollActive) {
                if (isTtsReady && ttsEngine?.isSpeaking == true) {
                    autoScrollHandler.postDelayed(this, 1000)
                    return
                }

                val scrollIntent = Intent("com.mytranslate.myup.TRIGGER_SCROLL").apply {
                    putExtra("LAST_TEXT_Y", lastTextYCoordinate)
                }
                sendBroadcast(scrollIntent)
                
                if (isAutoScanActive) {
                    autoScanHandler.removeCallbacks(autoScanRunnable)
                    autoScanHandler.postDelayed(autoScanRunnable, 1500) 
                }
                autoScrollHandler.postDelayed(this, 4000)
            }
        }
    }

    private val updateTextReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val text = intent?.getStringExtra("translated_text")
            val maxY = intent?.getIntExtra("MAX_Y", 0) ?: 0
            
            if (text != null && text.isNotEmpty()) {
                if (text.contains("Memulai") || text.contains("Mati")) {
                    tvResult.text = text
                    return
                }
                if (text.trim() == lastSpokenText.trim()) return

                tvResult.text = text
                tvResult.visibility = if (isTextVisible) View.VISIBLE else View.GONE
                lastSpokenText = text.trim()
                if (maxY > 0) lastTextYCoordinate = maxY
                
                ttsDelayHandler.removeCallbacksAndMessages(null)
                ttsDelayHandler.postDelayed({
                    if (isTtsReady && ttsEngine?.isSpeaking == false) {
                        val textToSpeak = text.lowercase(Locale("id", "ID"))
                        ttsEngine?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "MangaTTSID")
                    }
                }, 800)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        ttsEngine = TextToSpeech(this, this)

        floatingView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#E6000000"))
            setPadding(16, 16, 16, 16)
        }

        btnCapture = TextView(this).apply {
            text = "📷 AUDIO MANGA: OFF"
            setBackgroundColor(Color.DKGRAY)
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 16)
        }

        val controlLayout1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 5)
        }

        btnMode = TextView(this).apply {
            text = "🌐 ENG -> INDO"
            setBackgroundColor(Color.parseColor("#00509E")) 
            setTextColor(Color.WHITE)
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(8, 10, 8, 10)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0,0,5,0) }
        }

        btnTextVis = TextView(this).apply {
            text = "👁️ TEKS: ON"
            setBackgroundColor(Color.parseColor("#696969")) 
            setTextColor(Color.WHITE)
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(8, 10, 8, 10)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(5,0,0,0) }
        }
        controlLayout1.addView(btnMode)
        controlLayout1.addView(btnTextVis)

        val controlLayout2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 5, 0, 10)
        }

        btnScroll = TextView(this).apply {
            text = "📜 SCROLL: OFF"
            setBackgroundColor(Color.parseColor("#4B0082")) 
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(10, 10, 10, 10)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0,0,5,0) }
        }
        
        btnCrop = TextView(this).apply {
            text = "✂️ SCAN AREA"
            setBackgroundColor(Color.parseColor("#D2691E")) 
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(10, 10, 10, 10)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(5,0,0,0) }
        }
        
        controlLayout2.addView(btnScroll)
        controlLayout2.addView(btnCrop)

        tvResult = TextView(this).apply {
            text = "Menunggu komik..."
            setTextColor(Color.WHITE)
            textSize = 14f
            visibility = View.VISIBLE
            setPadding(0, 10, 0, 0)
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(550, LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(tvResult)
        }

        floatingView.addView(btnCapture)
        floatingView.addView(controlLayout1)
        floatingView.addView(controlLayout2)
        floatingView.addView(scrollView)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        windowManager.addView(floatingView, params)

        // KLIK TOMBOL CROP
        btnCrop.setOnClickListener {
            floatingView.visibility = View.GONE
            showCropOverlay()
        }

        btnMode.setOnClickListener {
            isTranslateMode = !isTranslateMode
            if (isTranslateMode) {
                btnMode.text = "🌐 ENG -> INDO"
                btnMode.setBackgroundColor(Color.parseColor("#00509E"))
            } else {
                btnMode.text = "📖 BACA ASLI"
                btnMode.setBackgroundColor(Color.parseColor("#D2691E")) 
            }
        }

        btnTextVis.setOnClickListener {
            isTextVisible = !isTextVisible
            if (isTextVisible) {
                btnTextVis.text = "👁️ TEKS: ON"
                btnTextVis.setBackgroundColor(Color.parseColor("#696969"))
                if (tvResult.text.isNotEmpty()) tvResult.visibility = View.VISIBLE
            } else {
                btnTextVis.text = "🙈 TEKS: OFF"
                btnTextVis.setBackgroundColor(Color.parseColor("#8B0000")) 
                tvResult.visibility = View.GONE
            }
        }

        btnScroll.setOnClickListener {
            isAutoScrollActive = !isAutoScrollActive
            if (isAutoScrollActive) {
                btnScroll.text = "🔮 SCROLL: ON"
                btnScroll.setBackgroundColor(Color.parseColor("#9370DB")) 
                autoScrollHandler.postDelayed(autoScrollRunnable, 3000) 
            } else {
                btnScroll.text = "📜 SCROLL: OFF"
                btnScroll.setBackgroundColor(Color.parseColor("#4B0082"))
                autoScrollHandler.removeCallbacks(autoScrollRunnable) 
            }
        }

        btnCapture.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val xDiff = Math.abs(event.rawX - initialTouchX)
                        val yDiff = Math.abs(event.rawY - initialTouchY)
                        if (xDiff > 10 || yDiff > 10) isDragging = true
                        
                        if (isDragging) {
                            params.x = initialX + (event.rawX - initialTouchX).toInt()
                            params.y = initialY + (event.rawY - initialTouchY).toInt()
                            windowManager.updateViewLayout(floatingView, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            isAutoScanActive = !isAutoScanActive
                            if (isAutoScanActive) {
                                btnCapture.text = "🔊 AUDIO MANGA: ON"
                                btnCapture.setBackgroundColor(Color.parseColor("#006400"))
                                tvResult.text = "Memulai..."
                                lastSpokenText = "" 
                                lastTextYCoordinate = 0 
                                autoScanHandler.post(autoScanRunnable)
                            } else {
                                btnCapture.text = "📷 AUDIO MANGA: OFF"
                                btnCapture.setBackgroundColor(Color.RED)
                                tvResult.text = "Mati."
                                ttsDelayHandler.removeCallbacksAndMessages(null)
                                ttsEngine?.stop()
                                autoScanHandler.removeCallbacks(autoScanRunnable)
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })

        registerReceiver(updateTextReceiver, IntentFilter("com.mytranslate.myup.UPDATE_TEXT"))
    }
    
    // FUNGSI UNTUK MENGGAMBAR KOTAK SELEKSI MANUAL
    private fun showCropOverlay() {
        val cropView = object : View(this@FloatingService) {
            val paint = Paint().apply {
                color = Color.RED
                style = Paint.Style.STROKE
                strokeWidth = 8f
            }
            val bgPaint = Paint().apply {
                color = Color.parseColor("#44000000") // Layar sedikit gelap
            }
            var startX = 0f
            var startY = 0f
            var endX = 0f
            var endY = 0f
            var isDrawing = false

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
                if (isDrawing) {
                    val left = minOf(startX, endX)
                    val top = minOf(startY, endY)
                    val right = maxOf(startX, endX)
                    val bottom = maxOf(startY, endY)
                    canvas.drawRect(left, top, right, bottom, paint)
                }
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.x
                        startY = event.y
                        endX = event.x
                        endY = event.y
                        isDrawing = true
                        invalidate()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        endX = event.x
                        endY = event.y
                        invalidate()
                    }
                    MotionEvent.ACTION_UP -> {
                        isDrawing = false
                        val left = minOf(startX, endX).toInt()
                        val top = minOf(startY, endY).toInt()
                        val right = maxOf(startX, endX).toInt()
                        val bottom = maxOf(startY, endY).toInt()

                        windowManager.removeView(this)
                        floatingView.visibility = View.VISIBLE

                        // Jika kotak cukup besar, mulai eksekusi potong layarnya!
                        if (right - left > 50 && bottom - top > 50) {
                            val captureIntent = Intent(this@FloatingService, ScreenCaptureService::class.java).apply {
                                action = "ACTION_CAPTURE"
                                putExtra("MODE_TRANSLATE", isTranslateMode)
                                putExtra("CROP_LEFT", left)
                                putExtra("CROP_TOP", top)
                                putExtra("CROP_RIGHT", right)
                                putExtra("CROP_BOTTOM", bottom)
                            }
                            startService(captureIntent)
                        }
                    }
                }
                return true
            }
        }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val cropParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(cropView, cropParams)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = ttsEngine?.setLanguage(Locale("id", "ID"))
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true
                ttsEngine?.setSpeechRate(0.95f)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isAutoScanActive = false
        isAutoScrollActive = false
        autoScanHandler.removeCallbacks(autoScanRunnable)
        autoScrollHandler.removeCallbacks(autoScrollRunnable)
        ttsDelayHandler.removeCallbacksAndMessages(null)
        
        if (ttsEngine != null) {
            ttsEngine?.stop()
            ttsEngine?.shutdown()
        }
        
        if (::floatingView.isInitialized) windowManager.removeView(floatingView)
        unregisterReceiver(updateTextReceiver)
    }
}
