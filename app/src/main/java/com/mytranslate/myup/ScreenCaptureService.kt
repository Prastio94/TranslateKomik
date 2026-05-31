package com.mytranslate.myup

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    private lateinit var windowManager: WindowManager
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var ocrProcessor: OCRProcessor
    private lateinit var translatorEngine: TranslatorEngine
    private var isCapturing = false
    private var isTranslateMode = true
    
    // Variabel untuk menyimpan area yang dipilih manual
    private var cropRect: Rect? = null

    companion object {
        var resultCode = Activity.RESULT_CANCELED
        var resultData: Intent? = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        ocrProcessor = OCRProcessor(this)
        translatorEngine = TranslatorEngine()
        
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_CAPTURE") {
            isTranslateMode = intent.getBooleanExtra("MODE_TRANSLATE", true)
            
            // Tangkap koordinat crop jika ada
            val left = intent.getIntExtra("CROP_LEFT", -1)
            if (left != -1) {
                val top = intent.getIntExtra("CROP_TOP", 0)
                val right = intent.getIntExtra("CROP_RIGHT", 0)
                val bottom = intent.getIntExtra("CROP_BOTTOM", 0)
                cropRect = Rect(left, top, right, bottom)
            } else {
                cropRect = null // Mode layar penuh normal
            }
            
            if (mediaProjection == null && resultData != null) {
                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData!!)
                setupVirtualDisplay()
            }
            captureCurrentFrame()
        }
        return START_NOT_STICKY
    }

    @SuppressLint("WrongConstant")
    private fun setupVirtualDisplay() {
        val metrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            displayManager.getDisplay(0)?.getRealMetrics(metrics)
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
        }

        val width = metrics.widthPixels
        val height = metrics.heightPixels

        imageReader = ImageReader.newInstance(width, height, 1, 2)
        
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCaptureCore",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
    }

    private fun captureCurrentFrame() {
        if (isCapturing || imageReader == null) return
        isCapturing = true

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        try {
            val image = imageReader?.acquireLatestImage()
            if (image != null) {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width

                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                image.close()

                val finalBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)

                // FITUR CROP MANUAL: Menghitamkan area luar kotak pilihan
                cropRect?.let { rect ->
                    val safeLeft = maxOf(0, rect.left)
                    val safeTop = maxOf(0, rect.top)
                    val safeRight = minOf(width, rect.right)
                    val safeBottom = minOf(height, rect.bottom)
                    
                    val canvas = Canvas(finalBitmap)
                    val paint = Paint().apply {
                        color = Color.BLACK
                        style = Paint.Style.FILL
                    }
                    
                    // Tutupi atas
                    canvas.drawRect(0f, 0f, width.toFloat(), safeTop.toFloat(), paint)
                    // Tutupi bawah
                    canvas.drawRect(0f, safeBottom.toFloat(), width.toFloat(), height.toFloat(), paint)
                    // Tutupi kiri
                    canvas.drawRect(0f, safeTop.toFloat(), safeLeft.toFloat(), safeBottom.toFloat(), paint)
                    // Tutupi kanan
                    canvas.drawRect(safeRight.toFloat(), safeTop.toFloat(), width.toFloat(), safeBottom.toFloat(), paint)
                }

                processCapturedBitmap(finalBitmap)
            } else {
                isCapturing = false
            }
        } catch (e: Exception) {
            isCapturing = false
        }
    }

    private fun processCapturedBitmap(bitmap: Bitmap) {
        ocrProcessor.processImage(bitmap, object : OCRProcessor.OCRListener {
            override fun onOCRStart() {}

            override fun onOCRComplete(text: String, maxY: Int) {
                if (text.length > 2) {
                    if (isTranslateMode) {
                        translatorEngine.translate(text, "auto", "id", object : TranslatorEngine.TranslateListener {
                            override fun onTranslationComplete(translatedText: String) {
                                isCapturing = false
                                sendResultToUI(translatedText, maxY)
                            }
                            override fun onTranslationError(error: String) {
                                isCapturing = false
                            }
                        })
                    } else {
                        isCapturing = false
                        sendResultToUI(text, maxY)
                    }
                } else {
                    isCapturing = false
                }
            }

            override fun onOCRError(error: String) {
                isCapturing = false
            }
        })
    }

    private fun sendResultToUI(message: String, maxY: Int) {
        val intent = Intent("com.mytranslate.myup.UPDATE_TEXT")
        intent.putExtra("translated_text", message)
        intent.putExtra("MAX_Y", maxY)
        sendBroadcast(intent)
    }

    private fun startForegroundNotification() {
        val channelId = "screen_capture_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(channelId, "Translate Komik Core", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Translate Komik")
            .setContentText("Mode Aliran Layar Aktif")
            .setSmallIcon(17301564)
            .build()

        startForeground(1002, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        mediaProjection = null
    }
}
