package com.example.navlesson

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.view.Surface
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageCapture.OutputFileResults
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/****************  PUBLIC SCREEN  ****************/

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun FirstScreen(navigationToSecondScreen: (String, Int) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    var isScanning by remember { mutableStateOf(false) }
    var captureNow by remember { mutableStateOf(false) }
    var latestBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val languages = listOf("ell", "eng")

    copyTessDataFiles(context, languages) // your helper

    // Ask for permission on first composition
    LaunchedEffect(Unit) { if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest() }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (cameraPermission.status.isGranted) {
            CameraCapture(
                captureNow = captureNow,
                onCaptureConsumed = { captureNow = false },
                onImageCaptured = { bmp -> latestBitmap = bmp },
                onError = { Log.e("Explain", "Camera error", it) }
            )
        } else {
            Text("Camera permission required")
        }

        Spacer(Modifier.height(12.dp))
        Button(onClick = { isScanning = !isScanning }) {
            Text(if (isScanning) "Stop Scanning" else "Start Scanning")
        }
    }

    /* ----------------  scanning loop  ---------------- */
    LaunchedEffect(isScanning) {
        while (isScanning) {
            captureNow = true            // 1️⃣ trigger shutter

            // 2️⃣ wait until a non‑null bitmap arrives
            snapshotFlow { latestBitmap }
                .filterNotNull()
                .first()
                .let { bmp ->
                    latestBitmap = null  // reset so next frame can arrive

                    performOCR(            // your existing heavy‑lifting
                        bitmap = bmp,
                        context = context,
                        languages = languages,
                        apiKey = Constants.API_KEY,
                        lifecycleOwner = lifecycleOwner
                    ) { correctedText ->
                        Log.d("Explain", "OCR result → $correctedText")
                        // database + network handled inside performOCR
                    }
                }

            delay(2_000)                // 3️⃣ pause 2 s
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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }


    // Build once per composition
    val imageCapture = remember { ImageCapture.Builder().setTargetRotation(Surface.ROTATION_90).build() }
    val previewView = remember { PreviewView(context) }
    val preview = androidx.camera.core.Preview.Builder().build()
    preview.setSurfaceProvider(previewView.surfaceProvider)

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.size(300.dp, 225.dp).aspectRatio(3f / 4f)
        )
        // Display Captured Image
        capturedBitmap?.let { bitmap ->
            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Captured Image",
                modifier = Modifier
                    .size(300.dp, 225.dp)
                    .aspectRatio(3f / 4f)
            )
        }
    }
    LaunchedEffect(Unit) {
        val provider = ProcessCameraProvider.getInstance(context).get()
        provider.unbindAll()
        provider.bindToLifecycle(
            lifecycleOwner,
            androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageCapture
        )
    }

    /* fire the shutter when captureNow flips true */
    LaunchedEffect(captureNow) {
        if (!captureNow) return@LaunchedEffect

        val photo = File.createTempFile("frame", ".jpg", context.cacheDir)
        val opts = ImageCapture.OutputFileOptions.Builder(photo).build()
        val executor = ContextCompat.getMainExecutor(context)

        imageCapture.takePicture(
            opts, executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: OutputFileResults) {
                    val bmp = BitmapFactory.decodeFile(photo.absolutePath)
                    capturedBitmap = bmp
                    onImageCaptured(bmp)
                    onCaptureConsumed()
                }

                override fun onError(exception: ImageCaptureException) {
                    onError(exception)
                    onCaptureConsumed()
                }
            }
        )
    }
}



@Composable
fun performOCRComposable(
    bitmap: Bitmap,
    context: Context,
    languages: List<String>,
    apiKey: String,
    callback: (String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    performOCR(bitmap, context, languages, apiKey, lifecycleOwner, callback)
}

fun performOCR(
    bitmap: Bitmap,
    context: Context,
    languages: List<String>,
    apiKey: String,
    lifecycleOwner: LifecycleOwner,
    callback: (String) -> Unit
) {
    Log.d("Explain", "performOCR | Starting OCR process")
    val tessBaseAPI = TessBaseAPI()
    val dataPath = context.filesDir.toString() + "/tesseract/"
    val lang = languages.joinToString("+")
    tessBaseAPI.init(dataPath, lang)
    val argbBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    tessBaseAPI.setImage(argbBitmap)
    val extractedText = tessBaseAPI.utF8Text
    Log.d("Explain", "performOCR | OCR extraction completed: $extractedText")
    tessBaseAPI.end()

    correctTextWithGeminiAI(apiKey, extractedText) { correctedText ->
        val cleanedText = cleanText(correctedText)
        Log.d("Explain", "performOCR | Corrected and cleaned text: $cleanedText")
        /* ------------- Database functions ----------------
        lifecycleOwner.lifecycleScope.launch {
            val entries = getAllEntriesFromDatabase(context)
            Log.d("Explain", "performOCR | Run isTextAIncludedInTextB")
            entries.forEach { entry ->
                isTextAIncludedInTextB(apiKey, entry.text, cleanedText) { isIncluded ->
                    if (isIncluded) {
                        Log.d("Explain", "Found ID")
                        sendPayload("reading", entry.text, entry.link)
                    } else {
                        Log.d("Explain", "No match found in database")
                    }
                }
            }
        }
        */


        callback(cleanedText)
    }
}

fun sendPayload(status: String, text: String, link: String) {
    val client = OkHttpClient()

    val json = JSONObject().apply {
        put("status", status)
        put("text", text)
        put("link", link)
    }.toString()

    Log.d("Explain", "sendPayload | JSON Payload: $json")

    val mediaType = "application/json; charset=utf-8".toMediaType()
    val body = json.toRequestBody(mediaType)

    val request = Request.Builder()
        .url("http://192.168.31.177:8081/payload") // Replace with the actual IP
        .post(body)
        .build()

    Log.d("Explain", "sendPayload | Request built: $request")

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("Explain", "sendPayload | Request failed", e)
        }

        override fun onResponse(call: Call, response: Response) {
            Log.d("Explain", "sendPayload | Response received: ${response.code}")
            Log.d("Explain", "sendPayload | Response body: ${response.body?.string()}")
            response.close()
        }
    })
}

fun cleanText(text: String): String {
    return text.replace("\n", " ").replace("\\s+".toRegex(), " ")
}

fun copyTessDataFiles(context: Context, languages: List<String>) {
    val assetManager = context.assets
    val tessDataPath = context.filesDir.toString() + "/tesseract/tessdata/"
    val tessDataDir = File(tessDataPath)
    if (!tessDataDir.exists()) {
        tessDataDir.mkdirs()
    }

    try {
        languages.forEach { language ->
            val fileName = "$language.traineddata"
            val outFile = File(tessDataPath, fileName)
            if (!outFile.exists()) {
                assetManager.open("tessdata/$fileName").use { inputStream ->
                    FileOutputStream(outFile).use { outputStream ->
                        val buffer = ByteArray(1024)
                        var read: Int
                        while (inputStream.read(buffer).also { read = it } != -1) {
                            outputStream.write(buffer, 0, read)
                        }
                    }
                }
            }
        }
    } catch (e: IOException) {
        e.printStackTrace()
    }
}

/*
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun FirstScreen(navigationToSecondScreen: (String, Int) -> Unit) {
    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)
    val context = LocalContext.current
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    val textRetrieved = remember { mutableStateOf("") }
    val languages = listOf("ell", "eng") // Add or modify languages as needed

    copyTessDataFiles(context, languages) // Copy the required language data files


    LaunchedEffect(Unit) {
        Log.d("Explain", "Requesting camera permission")
        cameraPermissionState.launchPermissionRequest()
    }

    if (cameraPermissionState.status.isGranted) {
        CameraCapture(
            onImageCaptured = { capturedBitmap ->
                // Save capturedBitmap to a temp file and get URI
                val sourceUri: Uri = saveBitmapToCache(context, capturedBitmap)
                val destFile = File(context.cacheDir, "cropped_${System.currentTimeMillis()}.jpg")
                val destUri: Uri = Uri.fromFile(destFile)

                performOCR(capturedBitmap, context, languages, Constants.API_KEY, lifecycleOwner) { correctedText ->
                    textRetrieved.value = correctedText
                    Log.d("Explain", "OCR and correction: ${textRetrieved.value}")
                }
            },
            onError = { exc ->
                Toast.makeText(context, "Image capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()
            }
        )
    } else {
        Text("Camera permission is required to use this feature.")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(200.dp)
                    .clickable { /* optionally re-crop on tap */ },
                contentScale = ContentScale.Crop
            )
        }
    }
}

/**
 * Save a Bitmap to cache directory and return its content URI.
 */
fun saveBitmapToCache(context: Context, bitmap: Bitmap): Uri {
    val cacheFile = File(context.cacheDir, "source_${System.currentTimeMillis()}.jpg")
    FileOutputStream(cacheFile).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
    }
    return FileProvider.getUriForFile(context, "${context.packageName}.provider", cacheFile)
}


fun performOCR(bitmap: Bitmap, context: Context, languages: List<String>, apiKey: String, callback: (String) -> Unit) {
    Log.d("Explain", "Starting OCR process")
    val tessBaseAPI = TessBaseAPI()
    val dataPath = context.filesDir.toString() + "/tesseract/"
    val lang = languages.joinToString("+")
    tessBaseAPI.init(dataPath, lang)
    val argbBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    tessBaseAPI.setImage(argbBitmap)
    val extractedText = tessBaseAPI.utF8Text
    Log.d("Explain", "OCR extraction completed")
    tessBaseAPI.end()
    Log.d("Explain", "Extracted text before Gemini AI : $extractedText")


    correctTextWithGeminiAI(apiKey, extractedText) { correctedText ->
        callback(cleanText(correctedText))
    }
}

fun cleanText(text: String): String {
    return text.replace("\n", " ").replace("\\s+".toRegex(), " ")
}

fun copyTessDataFiles(context: Context, languages: List<String>) {
    val assetManager = context.assets
    val tessDataPath = context.filesDir.toString() + "/tesseract/tessdata/"
    val tessDataDir = File(tessDataPath)
    if (!tessDataDir.exists()) {
        tessDataDir.mkdirs()
    }

    try {
        languages.forEach { language ->
            val fileName = "$language.traineddata"
            val outFile = File(tessDataPath, fileName)
            if (!outFile.exists()) {
                assetManager.open("tessdata/$fileName").use { inputStream ->
                    FileOutputStream(outFile).use { outputStream ->
                        val buffer = ByteArray(1024)
                        var read: Int
                        while (inputStream.read(buffer).also { read = it } != -1) {
                            outputStream.write(buffer, 0, read)
                        }
                    }
                }
            }
        }
    } catch (e: IOException) {
        e.printStackTrace()
    }
}

 */