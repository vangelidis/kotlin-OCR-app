package com.example.navlesson

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import com.example.navlesson.composable.CameraCapture
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.Manifest
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import java.io.FileOutputStream
import java.io.IOException

class AddEntry : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("Explain", "AddEntry - onCreate")
        super.onCreate(savedInstanceState)
        setContent {
            AddEntryScreen(context = this)
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AddEntryScreen(context: Context) {
    Log.d("Explain", "AddEntry - AddEntryScreen")
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var extractedText by remember { mutableStateOf("") }
    var showCamera by remember { mutableStateOf(false) }
    val languages = listOf("ell", "eng")

    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.let { intent ->
                    val resultUri: Uri? = UCrop.getOutput(intent)
                    resultUri?.let { uri ->
                        val croppedBitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            val source = ImageDecoder.createSource(context.contentResolver, uri)
                            ImageDecoder.decodeBitmap(source)
                        } else {
                            @Suppress("DEPRECATION")
                            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                        }
                        bitmap = croppedBitmap
                        Log.d("Explain", "AddEntry - Inside cropLauncher")
                        coroutineScope.launch {
                            extractedText = coroutineScope.launch {
                                bitmap?.let { capturedBitmap ->
                                    performOCR(capturedBitmap, context, languages) { correctedText ->
                                        Log.d("Explain", "AddEntry - correctedText: $correctedText")
                                        correctTextWithGeminiAI(
                                            apiKey = Constants.API_KEY,
                                            text = correctedText
                                        ) { correctedText ->
                                            Log.d("Explain", "AddEntry - correctTextWithGeminiAI: $correctedText")
                                            // Update the extracted text with the corrected text
                                            extractedText = correctedText
                                        }
                                    }
                                }
                            }.toString()
                        }
                    }
                }
            } else if (result.resultCode == UCrop.RESULT_ERROR) {
                result.data?.let { intent ->
                    val cropError = UCrop.getError(intent)
                    Toast.makeText(context, "Crop error: ${cropError?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(200.dp)
                    .clickable { /* Optionally re-crop */ },
                contentScale = ContentScale.Crop
            )
        }

        Button(onClick = {
            showCamera = true
        }) {
            Text("Take Photo")
        }

        if (showCamera) {
            CameraCapture(
                onImageCaptured = { capturedBitmap ->
                    showCamera = false
                    val sourceUri: Uri = saveBitmapToCache(context, capturedBitmap)
                    val destFile = File(context.cacheDir, "cropped_${System.currentTimeMillis()}.jpg")
                    val destUri: Uri = Uri.fromFile(destFile)

                    val uCropIntent: Intent = UCrop.of(sourceUri, destUri)
                        .useSourceImageAspectRatio()
                        .withMaxResultSize(1000, 1000)
                        .withOptions(UCrop.Options().apply {
                            setFreeStyleCropEnabled(true)
                        })
                        .getIntent(context)
                    cropLauncher.launch(uCropIntent)
                },
                onError = { exc ->
                    showCamera = false
                    Toast.makeText(context, "Image capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }

        val scrollState = rememberScrollState()

        var link by remember { mutableStateOf("") }
        var expanded by remember { mutableStateOf(false) }
        var selectedType by remember { mutableStateOf("text") }
        var editableText by remember { mutableStateOf("") }

        LaunchedEffect(extractedText) {
            editableText = extractedText
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Editable Text Area
            TextField(
                value = editableText,
                onValueChange = { newText -> editableText = newText },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Retrieved Text") },
                placeholder = { Text("No text extracted yet.") }
            )

            // Paste Link Field
            TextField(
                value = link,
                onValueChange = { newLink -> link = newLink },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Paste Link") },
                placeholder = { Text("Enter or paste a link here") }
            )

            // Dropdown Menu
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(onClick = { expanded = true }) {
                    Text(selectedType)
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    listOf("text", "image", "video").forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = {
                                selectedType = type
                                expanded = false
                            }
                        )
                    }
                }
            }
            Button(
                onClick = {
                    coroutineScope.launch {
                        addEntryToDatabase(context, editableText, link, selectedType)
                        Toast.makeText(context, "Entry saved successfully!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Entry")
            }
        }
    }

}

fun saveBitmapToCache(context: Context, bitmap: Bitmap): Uri {
    val cacheDir = context.cacheDir
    val file = File(cacheDir, "captured_image_${System.currentTimeMillis()}.jpg")
    try {
        FileOutputStream(file).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        }
    } catch (e: IOException) {
        e.printStackTrace()
        throw RuntimeException("Failed to save bitmap to cache", e)
    }
    return Uri.fromFile(file)
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RequestCameraPermission(onPermissionGranted: () -> Unit) {
    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        cameraPermissionState.launchPermissionRequest()
    }

    if (cameraPermissionState.status.isGranted) {
        onPermissionGranted()
    } else {
        Text("Camera permission is required to use this feature.")
    }
}


suspend fun addEntryToDatabase(context: Context, text: String, link: String, type: String) {
    val db = AppDatabase.getDatabase(context)
    val newEntry = Entry(text = text, link = link, type = type)
    withContext(Dispatchers.IO) {
        db.entryDao().insertEntry(newEntry)
    }

    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    fun RequestCameraPermission(onPermissionGranted: @Composable () -> Unit) {
        val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)

        LaunchedEffect(Unit) {
            cameraPermissionState.launchPermissionRequest()
        }

        if (cameraPermissionState.status.isGranted) {
            onPermissionGranted()
        } else {
            Text("Camera permission is required to use this feature.")
        }
    }
}
