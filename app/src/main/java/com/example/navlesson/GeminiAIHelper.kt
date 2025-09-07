package com.example.navlesson

import android.util.Log
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

data class GeminiAIRequest(val text: String)
data class GeminiAIResponse(val correctedText: String)
data class GeminiAIResponseResult(
    val candidates: List<Candidate>
)

data class Candidate(
    val content: Content
)

data class Content(
    val parts: List<Part>
)

data class Part(
    val text: String
)

fun correctTextWithGeminiAI(apiKey: String, text: String, callback: (String) -> Unit) {
    val client = OkHttpClient()
    val gson = Gson()
    val requestBody = RequestBody.create(
        "application/json".toMediaTypeOrNull(),
        gson.toJson(mapOf(
            "contents" to listOf(
                mapOf("parts" to listOf(mapOf("text" to "The text was retrieved with OCR. Contains wrong on random characters. Correct the text and return only the corrected text. Absolutely no explanation or additional texts: $text")))
            )
        ))
    )
    val request = Request.Builder()
        .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey")
        .post(requestBody)
        .addHeader("Content-Type", "application/json")
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("Explain", "onFailure | API request failed", e) // Log the failure
            e.printStackTrace()
            callback("Gemini AI correction failed") // Return the original text in case of failure
        }

        override fun onResponse(call: Call, response: Response) {
            val responseBody = response.body?.string()
            if (responseBody != null) {
                Log.d("Explain", "onResponse | API response: $responseBody")
                try {
                    val geminiAIResponseResult = gson.fromJson(responseBody, GeminiAIResponseResult::class.java)
                    val correctedText = geminiAIResponseResult.candidates
                        .firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                    if (correctedText.isNullOrEmpty()) {
                        Log.e("Explain", "onResponse | Corrected text is null or empty, returning original text")
                        callback(text)
                    } else {
                        callback(correctedText)
                    }
                } catch (e: Exception) {
                    Log.e("Explain", "onResponse | Error parsing API response", e)
                    callback(text)
                }
            } else {
                Log.e("Explain", "onResponse | Response body is null, returning original text")
                callback(text)
            }
        }
    })
}

fun isTextAIncludedInTextB(apiKey: String, textA: String, textB: String, callback: (Boolean) -> Unit) {
    val counter = 0
    val client = OkHttpClient()
    val gson = Gson()
    val requestBody = gson.toJson(
        mapOf(
            "contents" to listOf(
                mapOf("parts" to listOf(mapOf("text" to "Check if the following text: '$textA' is included in or is similar to this text: '$textB'. Respond with 'true' or 'false' only. Take into account that the texts were extracted with OCR and may contain random characters. Absolutely no explanation or additional texts.")))
            )
        )
    ).toRequestBody("application/json".toMediaTypeOrNull())

    Log.d("Explain", "isTextAIncludedInTextB | remove log temporarily")
    //Log.d("Explain", "Check if the following text: '$textA' is included in or is similar to this text: '$textB'. Respond with 'true' or 'false' only. Take into account that the texts were extracted with OCR and may contain random characters. Absolutely no explanation or additional texts.")
    val request = Request.Builder()
        .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey")
        .post(requestBody)
        .addHeader("Content-Type", "application/json")
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            e.printStackTrace()
            Log.e("Explain", "GeminiAIHelper Request failed", e)
            callback(false) // Default to false on failure
        }

        override fun onResponse(call: Call, response: Response) {
            response.body?.string()?.let { responseBody ->
                val geminiAIResponseResult = gson.fromJson(responseBody, GeminiAIResponseResult::class.java)
                val resultText = geminiAIResponseResult.candidates
                    .firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()?.toBoolean()
                Log.d("Explain", "ResultResponse: $resultText") // Log the response
                //Log.d("Explain", "Raw Response: $responseBody") // Log the raw response
                callback(resultText ?: false) // Pass the result or default to false
            } ?: run {
                Log.d("Explain", "Response: false (callback)") // Log the default response
                callback(false)
            }
        }
    })
}