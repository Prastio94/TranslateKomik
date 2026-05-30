package com.mytranslate.myup

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

class TranslatorEngine {

    interface TranslateListener {
        fun onTranslationComplete(translatedText: String)
        fun onTranslationError(error: String)
    }

    // SOLUSI DETEKSI BAHASA: Mengubah sourceLang dari "en" menjadi "auto"
    // Google akan mendeteksi sendiri apakah ini bahasa Inggris, Korea, Jepang, atau Indonesia.
    fun translate(text: String, sourceLang: String = "auto", targetLang: String = "id", listener: TranslateListener) {
        if (text.isBlank()) {
            listener.onTranslationComplete("")
            return
        }

        thread {
            try {
                val urlString = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=" +
                        sourceLang + "&tl=" + targetLang + "&dt=t&q=" + URLEncoder.encode(text, "UTF-8")
                
                val url = URL(urlString)
                val con = url.openConnection() as HttpURLConnection
                con.requestMethod = "GET"
                con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
                con.setRequestProperty("Accept", "*/*")
                con.connectTimeout = 5000
                con.readTimeout = 5000

                val responseCode = con.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(con.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()

                    val result = parseTranslateJson(response.toString())
                    
                    Handler(Looper.getMainLooper()).post {
                        listener.onTranslationComplete(result)
                    }
                } else {
                    Handler(Looper.getMainLooper()).post {
                        listener.onTranslationError("Server error code: $responseCode")
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    listener.onTranslationError(e.message ?: "Unknown Error")
                }
            }
        }
    }

    private fun parseTranslateJson(jsonString: String): String {
        return try {
            val jsonArray = JSONArray(jsonString)
            val segments = jsonArray.getJSONArray(0)
            val sb = StringBuilder()
            
            for (i in 0 until segments.length()) {
                val segment = segments.getJSONArray(i)
                sb.append(segment.getString(0)).append(" ")
            }
            sb.toString().trim()
        } catch (e: Exception) {
            "Gagal merangkai kalimat: ${e.message}"
        }
    }
}
