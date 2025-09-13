package com.example.navlesson

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import androidx.core.content.ContextCompat
import android.annotation.SuppressLint
import androidx.exifinterface.media.ExifInterface
import com.example.navlesson.composable.adjustContrast
import com.example.navlesson.composable.convertToGrayscale
import com.example.navlesson.composable.removeNoise

class AddEntry : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("Explain", "AddEntry - onCreate")
        super.onCreate(savedInstanceState)
        setContent {
            AddEntryScreen(context = this)
        }
    }
}
@Composable
fun AddEntryScreen(context: Context) {
    LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var extractedText by remember { mutableStateOf("") }
    var showCamera by remember { mutableStateOf(false) }
    val languages = listOf("ell", "eng")

    // uCrop launcher (crop after capture)
    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.let { intent ->
                    val resultUri: Uri? = UCrop.getOutput(intent)
                    resultUri?.let { uri ->
                        val croppedBitmap =
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                val source = ImageDecoder.createSource(context.contentResolver, uri)
                                ImageDecoder.decodeBitmap(source)
                            } else {
                                @Suppress("DEPRECATION")
                                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                            }
                        bitmap = croppedBitmap
                        coroutineScope.launch {
                            performOCR(croppedBitmap, context, languages) { rawText ->
                                correctTextWithGeminiAI(
                                    apiKey = Constants.API_KEY,
                                    text = rawText
                                ) { finalText ->
                                    extractedText = finalText
                                }
                            }
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

    var link by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf("text") }
    var editableText by remember { mutableStateOf("") }

    LaunchedEffect(extractedText) {
        editableText = extractedText
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Preview of the cropped image
        item {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clickable { /* Optionally re-crop */ },
                    contentScale = ContentScale.Crop
                )
            }
        }

        // Open camera
        item {
            Button(
                onClick = { showCamera = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Take Photo")
            }
        }

        // Camera block (only visible when taking a photo)
        if (showCamera) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                ) {
                    CameraCapture(
                        modifier = Modifier.fillMaxSize(),
                        onImageCaptured = { capturedBitmap ->
                            showCamera = false
                            // Preprocess the captured image
                            val preprocessedBitmap = preprocessImage(capturedBitmap)

                            // Save to cache and launch uCrop
                            val sourceUri: Uri = saveBitmapToCache(context, preprocessedBitmap)
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
            }
        }

        // Text fields and actions
        item {
            TextField(
                value = editableText,
                onValueChange = { newText -> editableText = newText },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Retrieved Text") },
                placeholder = { Text("No text extracted yet.") }
            )
        }

        item {
            TextField(
                value = link,
                onValueChange = { newLink -> link = newLink },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Paste Link") },
                placeholder = { Text("Enter or paste a link here") }
            )
        }

        item {
            Box(modifier = Modifier.fillMaxWidth()) {
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
        }

        item {
            Button(
                onClick = {
                    coroutineScope.launch {
                        try {
                            addEntryToDatabase(context, editableText, link, selectedType)
                            Toast.makeText(context, "Entry saved successfully!", Toast.LENGTH_SHORT).show()
                        } catch (t: Throwable) {
                            Toast.makeText(context, "Save failed: ${t.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Entry")
            }
        }
    }
}

@SuppressLint("RestrictedApi")
@Composable
fun CameraCapture(
    onImageCaptured: (Bitmap) -> Unit,
    onError: (ImageCaptureException) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val imageCaptureRef = remember { mutableStateOf<ImageCapture?>(null) }
    val rotationDegreesRef = remember { mutableStateOf(0) }

    // The camera preview view
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE // TextureView
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // Place the preview
    AndroidView(
        modifier = modifier,
        factory = { previewView }
    )

    DisposableEffect(lifecycleOwner) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        previewView.post {
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                try {
                    val cameraProvider = providerFuture.get()

                    // Safe rotation
                    val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
                    rotationDegreesRef.value = when (rotation) {
                        Surface.ROTATION_0 -> 0
                        Surface.ROTATION_90 -> 90
                        Surface.ROTATION_180 -> 180
                        Surface.ROTATION_270 -> 270
                        else -> 0
                    }

                    val preview = Preview.Builder()
                        .setTargetRotation(rotation)
                        .build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                    val imageCapture = ImageCapture.Builder()
                        .setTargetRotation(rotation)
                        .build()
                        .also { imageCaptureRef.value = it }

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                    )

                    previewView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                        val rot = previewView.display?.rotation ?: Surface.ROTATION_0
                        rotationDegreesRef.value = when (rot) {
                            Surface.ROTATION_0 -> 0
                            Surface.ROTATION_90 -> 90
                            Surface.ROTATION_180 -> 180
                            Surface.ROTATION_270 -> 270
                            else -> 0
                        }
                        try {
                            preview.targetRotation = rot
                            imageCapture.targetRotation = rot
                        } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
                    onError(
                        ImageCaptureException(
                            ImageCapture.ERROR_UNKNOWN,
                            e.message ?: "Camera init error",
                            e
                        )
                    )
                }
            }, mainExecutor)
        }

        onDispose {
            try {
                val provider = ProcessCameraProvider.getInstance(context).get()
                provider.unbindAll()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Button(onClick = {
            val imageCapture = imageCaptureRef.value
            if (imageCapture == null) {
                onError(
                    ImageCaptureException(
                        ImageCapture.ERROR_INVALID_CAMERA,
                        "Camera not ready",
                        null
                    )
                )
                return@Button
            }

            try {
                val photo = File.createTempFile("frame_", ".jpg", context.cacheDir)
                val opts = ImageCapture.OutputFileOptions.Builder(photo).build()
                val executor = ContextCompat.getMainExecutor(context)

                imageCapture.takePicture(
                    opts, executor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            try {
                                val path = photo.absolutePath
                                val exifDeg = exifRotationDegrees(path)
                                val bmp = BitmapFactory.decodeFile(path)
                                val out = if (exifDeg != 0) rotateBitmapIfNeeded(bmp, exifDeg) else bmp
                                onImageCaptured(out)
                            } catch (e: Exception) {
                                onError(
                                    ImageCaptureException(
                                        ImageCapture.ERROR_FILE_IO,
                                        e.message ?: "Decode error",
                                        e
                                    )
                                )
                            }
                        }
                        override fun onError(exception: ImageCaptureException) {
                            onError(exception)
                        }
                    }
                )
            } catch (e: Exception) {
                onError(
                    ImageCaptureException(
                        ImageCapture.ERROR_UNKNOWN,
                        e.message ?: "Capture error",
                        e
                    )
                )
            }
        }) {
            Text("Capture")
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

fun rotateBitmapIfNeeded(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
    if (rotationDegrees == 0) return bitmap
    val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun exifRotationDegrees(path: String): Int = try {
    val exif = ExifInterface(path)
    when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90  -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
} catch (_: Exception) { 0 }

fun preprocessImage(bitmap: Bitmap): Bitmap {
    Log.d("Explain", "Starting image preprocessing")
    val grayscaleBitmap = convertToGrayscale(bitmap)
    Log.d("Explain", "Converted to grayscale")
    val noiseRemovedBitmap = removeNoise(grayscaleBitmap)
    Log.d("Explain", "Noise removed")
    //val binarizedBitmap = binarizeImage(noiseRemovedBitmap)
    val contrastAdjustedBitmap = adjustContrast(noiseRemovedBitmap, 1.5f)
    Log.d("Explain", "Contrast adjusted")
    Log.d("Explain", "Image preprocessing completed")
    return contrastAdjustedBitmap
}