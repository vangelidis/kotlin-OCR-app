package com.example.navlesson

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalPermissionsApi::class)
@Composable


fun FirstScreen() {
    val context = LocalContext.current
    val cameraPermission = rememberPermissionState(permission = Manifest.permission.CAMERA)

    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var extractedText by remember { mutableStateOf("") }
    var captureNow by remember { mutableStateOf(false) }

    val foundEntry = remember { mutableStateOf<Entry?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            if (cameraPermission.status.isGranted) {
                // Camera preview
                CameraCapture(
                    captureNow = captureNow,
                    onCaptureConsumed = { captureNow = false },
                    onImageCaptured = { bitmap ->
                        val rotatedBitmap = Bitmap.createBitmap(
                            bitmap, 0, 0, bitmap.width, bitmap.height,
                            android.graphics.Matrix().apply { postRotate(90f) },
                            true
                        )
                        capturedBitmap = rotatedBitmap
                        performOCR(rotatedBitmap, context, listOf("ell", "eng")) { text ->
                            correctTextWithGeminiAI(
                                apiKey = Constants.API_KEY,
                                text = text
                            ) { correctedText ->
                                extractedText = correctedText
                                // Check if the text exists in the database
                                val db = AppDatabase.getDatabase(context)
                                CoroutineScope(Dispatchers.IO).launch {
                                    val entries = db.entryDao().getAllEntries()
                                    val cleanedCorrectedText =
                                        correctedText.replace("\n", " ").replace("\t", " ").trim()
                                    var entryFound = false
                                    for (entry in entries) {
                                        val isIncluded = suspendCoroutine { continuation ->
                                            isTextAIncludedInTextB(
                                                apiKey = Constants.API_KEY,
                                                textA = cleanedCorrectedText,
                                                textB = entry.text
                                            ) { result ->
                                                continuation.resumeWith(Result.success(result))
                                            }
                                        }
                                        if (isIncluded) {
                                            withContext(Dispatchers.Main) {
                                                foundEntry.value = entry
                                                sendLinkAndTypeToServer(entry.link, entry.type)
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Matching Entry Found - Results sent to PC", Toast.LENGTH_SHORT).show()
                                                }                                            }
                                            entryFound = true
                                            break
                                        }
                                    }
                                    withContext(Dispatchers.Main) {
                                        if (!entryFound) {
                                            Log.d("Explain", "No matching entry found in the database")
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "No matching entry found in the database", Toast.LENGTH_SHORT).show()
                                            }                                        }
                                    }
                                }
                            }
                        }
                    },
                    onError = { exception ->
                        Log.e("Explain", "Capture error: ${exception.message}", exception)
                    }
                )

                // Capture button
                Button(
                    onClick = { captureNow = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Capture & OCR")
                }

            } else {
                // Permission request
                Button(
                    onClick = { cameraPermission.launchPermissionRequest() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Camera Permission")
                }
            }
        }


    }
}

/****************  CAMERA PREVIEW & CAPTURE  ****************/

@SuppressLint("RestrictedApi")
@Composable
fun CameraCapture(
    captureNow: Boolean,
    onCaptureConsumed: () -> Unit,
    onImageCaptured: (Bitmap) -> Unit,
    onError: (ImageCaptureException) -> Unit,
) {
    Log.d("Explain", "CameraCapture | Entered composable")

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember { PreviewView(context) }
    val preview = remember { Preview.Builder().build() }
    val imageCapture = remember { ImageCapture.Builder().build() }

    // Bind once
    DisposableEffect(lifecycleOwner) {
        Log.d("Explain", "CameraCapture | Binding camera")
        val provider = ProcessCameraProvider.getInstance(context).get()
        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        preview.setSurfaceProvider(previewView.surfaceProvider)
        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
        onDispose {
            Log.d("Explain", "CameraCapture | Unbinding camera")
            provider.unbindAll()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
    )

    // Fire capture
    LaunchedEffect(captureNow) {
        if (!captureNow) return@LaunchedEffect
        Log.d("Explain", "CameraCapture | CaptureNow triggered")

        val photo = File.createTempFile("frame_", ".jpg", context.cacheDir)
        val opts = ImageCapture.OutputFileOptions.Builder(photo).build()
        val executor = ContextCompat.getMainExecutor(context)

        imageCapture.takePicture(
            opts, executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.d("Explain", "CameraCapture | Photo saved to ${photo.absolutePath}")
                    Toast.makeText(context, "Image captured and saved", Toast.LENGTH_SHORT).show()
                    val bmp = BitmapFactory.decodeFile(photo.absolutePath)
                    onImageCaptured(bmp)
                    onCaptureConsumed()
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("Explain", "CameraCapture | Error taking photo", exception)
                    onError(exception)
                    onCaptureConsumed()
                }
            }
        )
    }
}

/****************  OCR (Tesseract)  ****************/

fun performOCR(bitmap: Bitmap, context: Context, languages: List<String>, callback: (String) -> Unit) {
    Log.d("Explain", "performOCR | Starting OCR thread")
    Thread {
        val mainHandler = Handler(Looper.getMainLooper())
        val tess = TessBaseAPI()
        val dataPath = context.filesDir.toString() + "/tesseract/"
        val lang = languages.joinToString("+")

        try {
            Log.d("Explain", "performOCR | Initializing Tesseract with lang=$lang")
            mainHandler.post {
                Toast.makeText(context, "Initializing Tesseract..", Toast.LENGTH_SHORT).show()
            }
            tess.init(dataPath, lang)
            val argb = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            tess.setImage(argb)
            Log.d("Explain", "performOCR | Image set, extracting text...")
            mainHandler.post {
                Toast.makeText(context, "Extracting text. Please wait..", Toast.LENGTH_LONG).show()
            }
            val rawText = tess.utF8Text ?: ""
            val cleaned = cleanText(rawText)
            mainHandler.post { callback(cleaned) }
        } catch (e: Exception) {
            Log.e("Explain", "performOCR | OCR error", e)
            mainHandler.post { callback("") }
        } finally {
            try {
                tess.end()
                Log.d("Explain", "performOCR | Tesseract ended")
                mainHandler.post {
                    Toast.makeText(context, "Text exctraction ended", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {}
        }
    }.start()
}

fun cleanText(text: String): String =
    text.replace("\n", " ").replace("\\s+".toRegex(), " ").trim()

fun sendLinkAndTypeToServer(content: String, type: String) {
    when (type.lowercase()) {
        "text" -> sendTextPayload(content)
        "image" -> sendImagePayload(content)
        "video" -> sendVideoPayload(content)
        else -> Log.e("SendToServer", "Unknown type: $type")
    }
}

fun sendTextPayload(text: String) {
    Log.d("Explain", "Send text payload to server: $text")
    val client = OkHttpClient()
    //Local web server URL. Update accordingly
    val url = "http://XXX.XXX.XXX.XXX:8081/payload"

    val json = JSONObject().apply {
        put("status", "ok")
        put("type", "Text")
        put("text", text)
    }

    val requestBody = json.toString()
        .toRequestBody("application/json".toMediaTypeOrNull())

    val request = Request.Builder()
        .url(url)
        .post(requestBody)
        .addHeader("Content-Type", "application/json")
        .build()

    client.newCall(request).enqueue(object : okhttp3.Callback {
        override fun onFailure(call: okhttp3.Call, e: IOException) {
            e.printStackTrace()
        }

        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
            println("Response: ${response.body?.string()}")
        }
    })
}

fun sendImagePayload(link: String) {
    Log.d("Explain", "Send image payload to server: $link")
    val client = OkHttpClient()
    //Local web server URL. Update accordingly
    val url = "http://XXX.XXX.XXX.XXX:8081/payload"

    val json = JSONObject().apply {
        put("status", "ok")
        put("type", "image")
        put("link", link)
    }

    val requestBody = json.toString()
        .toRequestBody("application/json".toMediaTypeOrNull())

    val request = Request.Builder()
        .url(url)
        .post(requestBody)
        .addHeader("Content-Type", "application/json")
        .build()

    client.newCall(request).enqueue(object : okhttp3.Callback {
        override fun onFailure(call: okhttp3.Call, e: IOException) {
            e.printStackTrace()
        }

        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
            println("Response: ${response.body?.string()}")
        }
    })
}

fun sendVideoPayload(link: String) {
    Log.d("Explain", "Send video payload to server: $link")
    val client = OkHttpClient()
    //Local web server URL. Update accordingly
    val url = "http://XXX.XXX.XXX.XXX:8081/payload"

    val json = JSONObject().apply {
        put("status", "ok")
        put("type", "video")
        put("link", link)
    }

    val requestBody = json.toString()
        .toRequestBody("application/json".toMediaTypeOrNull())

    val request = Request.Builder()
        .url(url)
        .post(requestBody)
        .addHeader("Content-Type", "application/json")
        .build()

    client.newCall(request).enqueue(object : okhttp3.Callback {
        override fun onFailure(call: okhttp3.Call, e: IOException) {
            e.printStackTrace()
        }

        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
            println("Response: ${response.body?.string()}")
        }
    })
}
