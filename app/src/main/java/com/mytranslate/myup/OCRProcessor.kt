package com.mytranslate.myup

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class OCRProcessor(private val context: Context) {
    
    interface OCRListener { 
        fun onOCRStart()
        fun onOCRComplete(text: String, maxY: Int) // Menambahkan parameter lokasi Y terbawah
        fun onOCRError(error: String) 
    }
    
    fun processImage(bitmap: Bitmap, listener: OCRListener) {
        listener.onOCRStart()
        
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val stringBuilder = StringBuilder()
                    var maxTextY = 0 // Variabel penampung koordinat Y paling bawah
                    
                    for (block in visionText.textBlocks) {
                        val rawText = block.text.trim()
                        
                        // 1. FILTER ANGKA HALAMAN: Abaikan nomor halaman (Contoh: "14 / 38")
                        if (rawText.contains(Regex("\\d+\\s*/\\s*\\d+"))) {
                            continue
                        }
                        
                        // 2. FILTER ANGKA MURNI: Abaikan jika hanya angka
                        if (rawText.matches(Regex("\\d+"))) {
                            continue
                        }
                        
                        var blockText = rawText.replace("\n", " ").trim()
                        
                        // 3. FILTER SFX JALANAN
                        val words = blockText.split(Regex("\\s+"))
                        if (words.size == 1 && blockText.matches(Regex("[A-Z]+"))) {
                            if (!rawText.contains(".") && !rawText.contains("!") && !rawText.contains("?")) {
                                continue 
                            }
                        }
                        
                        // Jika lolos filter, cek posisi kotak teksnya untuk menentukan batas scroll
                        val box = block.boundingBox
                        if (box != null && box.bottom > maxTextY) {
                            maxTextY = box.bottom
                        }
                        
                        blockText = blockText.replace(Regex("[^a-zA-Z0-9\\s.,!?'\\-]"), "")
                        
                        if (blockText.length > 2) {
                            val lastChar = blockText.last()
                            if (lastChar != '.' && lastChar != '?' && lastChar != '!') {
                                blockText += "."
                            }
                            stringBuilder.append(blockText).append(" ")
                        }
                    }
                    
                    val finalResult = stringBuilder.toString().trim()
                    
                    Handler(Looper.getMainLooper()).post { 
                        listener.onOCRComplete(finalResult, maxTextY) 
                    }
                }
                .addOnFailureListener { e ->
                    Handler(Looper.getMainLooper()).post { 
                        listener.onOCRError(e.message ?: "Gagal membaca teks komik") 
                    }
                }
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post { 
                listener.onOCRError(e.message ?: "Error sistem OCR AI") 
            }
        }
    }
}
