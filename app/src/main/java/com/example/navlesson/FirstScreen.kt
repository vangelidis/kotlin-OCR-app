package com.example.navlesson

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
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
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.suspendCoroutine
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.io.IOException

/****************  MAIN SCREEN  ****************/

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun FirstScreen(onNavigateToSecondScreen: (String, Int) -> Unit) {
    Log.d("Explain", "FirstScreen | Composable entered")

    val context = LocalContext.current
    val cameraPermission = rememberPermissionState(permission = Manifest.permission.CAMERA)

    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var extractedText by remember { mutableStateOf("") }
    var captureNow by remember { mutableStateOf(false) }

    val foundEntry = remember { mutableStateOf<Entry?>(null) }


    // Copy tessdata files for ell + eng
    LaunchedEffect(Unit) {
        Log.d("Explain", "FirstScreen | Copying tessdata files...")
        copyTessDataFiles(context, listOf("ell", "eng"))
        Log.d("Explain", "FirstScreen | Tessdata ready")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (cameraPermission.status.isGranted) {
            Log.d("Explain", "FirstScreen | Camera permission granted")

            // Camera preview
            CameraCapture(
                captureNow = captureNow,
                onCaptureConsumed = {
                    Log.d("Explain", "FirstScreen | Capture consumed, resetting trigger")
                    captureNow = false
                },
                onImageCaptured = { bitmap ->
                    Log.d("Explain", "FirstScreen | Image captured")
                    val rotatedBitmap = Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.width, bitmap.height,
                        android.graphics.Matrix().apply { postRotate(90f) },
                        true
                    )
                    capturedBitmap = rotatedBitmap
                    performOCR(rotatedBitmap, context, listOf("ell", "eng")) { text ->
                        Log.d("Explain", "FirstScreen | OCR callback received")
                        correctTextWithGeminiAI(
                            apiKey = Constants.API_KEY,
                            text = text
                        ) { correctedText ->
                            Log.d("Explain", "FirstScreen | Corrected text received")
                            extractedText = correctedText

                            // Check if the text exists in the database
                            val db = AppDatabase.getDatabase(context)
                            CoroutineScope(Dispatchers.IO).launch {
                                val entries = db.entryDao().getAllEntries()
                                val cleanedCorrectedText = correctedText.replace("\n", " ").replace("\t", " ").trim()
                                Log.d("Explain", "Cleaned TextA: $cleanedCorrectedText")

                                for (entry in entries) {
                                    val isIncluded = suspendCoroutine<Boolean> { continuation ->
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
                                        }
                                        break
                                    }
                                }

                                withContext(Dispatchers.Main) {
                                    foundEntry.value?.let {
                                        Log.d("Explain", "Entry found: Link=${it.link}, Type=${it.type}")
                                        sendLinkAndTypeToServer(it.link, it.type)
                                    } ?: Log.d("Explain", "No matching entry found in the database")
                                }
                            }
                        }
                    }
                },
                onError = { exception ->
                    Log.e("Explain", "FirstScreen | Capture error: ${exception.message}", exception)
                }
            )

            // Capture button
            Button(onClick = {
                Log.d("Explain", "FirstScreen | Capture button pressed")
                captureNow = true
            }) {
                Text("Capture & OCR")
            }

            // Captured preview
            capturedBitmap?.let {
                Log.d("Explain", "FirstScreen | Displaying captured preview")
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Captured Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
            }


        } else {
            Log.d("Explain", "FirstScreen | Camera permission not granted")
            Button(onClick = {
                Log.d("Explain", "FirstScreen | Requesting camera permission")
                cameraPermission.launchPermissionRequest()
            }) {
                Text("Grant Camera Permission")
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
            tess.init(dataPath, lang)
            val argb = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            tess.setImage(argb)
            Log.d("Explain", "performOCR | Image set, extracting text...")
            val rawText = tess.utF8Text ?: ""
            //Log.d("Explain", "performOCR | Raw OCR result: $rawText")
            val cleaned = cleanText(rawText)
            //Log.d("Explain", "performOCR | Cleaned OCR result: $cleaned")
            mainHandler.post { callback(cleaned) }
        } catch (e: Exception) {
            Log.e("Explain", "performOCR | OCR error", e)
            mainHandler.post { callback("") }
        } finally {
            try {
                tess.end()
                Log.d("Explain", "performOCR | Tesseract ended")
            } catch (_: Exception) {}
        }
    }.start()
}

fun cleanText(text: String): String =
    text.replace("\n", " ").replace("\\s+".toRegex(), " ").trim()

fun copyTessDataFiles(context: Context, languages: List<String>) {
    Log.d("Explain", "copyTessDataFiles | Checking tessdata files")
    val assetManager = context.assets
    val tessDataPath = context.filesDir.toString() + "/tesseract/tessdata/"
    val tessDataDir = File(tessDataPath)
    if (!tessDataDir.exists()) {
        tessDataDir.mkdirs()
        Log.d("Explain", "copyTessDataFiles | Created tessdata dir")
    }
    try {
        languages.forEach { language ->
            val fileName = "$language.traineddata"
            val outFile = File(tessDataPath, fileName)
            if (!outFile.exists()) {
                Log.d("Explain", "copyTessDataFiles | Copying $fileName from assets")
                assetManager.open("tessdata/$fileName").use { input ->
                    FileOutputStream(outFile).use { out ->
                        val buf = ByteArray(4096)
                        var r: Int
                        while (input.read(buf).also { r = it } != -1) out.write(buf, 0, r)
                    }
                }
                Log.d("Explain", "copyTessDataFiles | Copied $fileName")
            } else {
                Log.d("Explain", "copyTessDataFiles | $fileName already exists")
            }
        }
    } catch (e: Exception) {
        Log.e("Explain", "copyTessDataFiles | Error copying tessdata", e)
    }
}

fun sendLinkAndTypeToServer(content: String, type: String) {
    when (type.lowercase()) {
        "text" -> sendTextPayload(content)
        "image" -> sendImagePayload(content)
        "video" -> sendVideoPayload(content)
        else -> Log.e("SendToServer", "Unknown type: $type")
    }
}

fun sendTextPayload(text: String) {
    val client = OkHttpClient()
    val url = "http://192.168.31.177:8081/payload"

    val json = JSONObject().apply {
        put("status", "ok")
        put("type", "Text")
        put("text", text)
    }

    val requestBody = RequestBody.create(
        "application/json".toMediaTypeOrNull(),
        json.toString()
    )

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
    val client = OkHttpClient()
    val url = "http://192.168.31.177:8081/payload"

    val json = JSONObject().apply {
        put("status", "ok")
        put("type", "image")
        put("link", link)
    }

    val requestBody = RequestBody.create(
        "application/json".toMediaTypeOrNull(),
        json.toString()
    )

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
    val client = OkHttpClient()
    val url = "http://192.168.31.177:8081/payload"

    val json = JSONObject().apply {
        put("status", "ok")
        put("type", "video")
        put("link", link)
    }

    val requestBody = RequestBody.create(
        "application/json".toMediaTypeOrNull(),
        json.toString()
    )

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