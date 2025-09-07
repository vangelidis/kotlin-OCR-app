package com.example.navlesson.composable

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.io.File
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.viewinterop.AndroidView
import android.media.ExifInterface
import androidx.camera.core.AspectRatio
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Slider
import androidx.camera.core.*


@Composable
fun CameraCapture(
    onImageCaptured: (Bitmap) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalContext.current as LifecycleOwner
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    val previewView = remember { PreviewView(context) }
    val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    var zoomState by remember { mutableStateOf(0f) }
    var camera: Camera? by remember { mutableStateOf(null) }
    var flashEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(cameraProviderFuture) {
        Log.d("Explain", "CameraProviderFuture launched")
        val cameraProvider = cameraProviderFuture.get()
        val aspectRatio = AspectRatio.RATIO_4_3 // Set the aspect ratio

        Log.d("Explain", "Setting up preview and image capture use cases")
        val preview = androidx.camera.core.Preview.Builder()
            .setTargetAspectRatio(aspectRatio)
            .build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        imageCapture = ImageCapture.Builder()
            .setTargetAspectRatio(aspectRatio)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK) // Use back camera
            .build()

        try {
            Log.d("Explain", "Binding use cases to lifecycle")
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner, cameraSelector, preview, imageCapture
            )
            Log.d("Explain", "Use cases bound to lifecycle")
        } catch (exc: Exception) {
            Log.e("Explain", "Use case binding failed", exc)
        }
    }

    LaunchedEffect(zoomState) {
        camera?.cameraControl?.setLinearZoom(zoomState)
    }

    Box(
        modifier = Modifier
            .size(300.dp, 225.dp) // Set the size of the viewfinder
            .aspectRatio(4f / 3f)
    ) {
        AndroidView({ previewView }, modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Slider(
                value = zoomState,
                onValueChange = { zoomState = it },
                valueRange = 0f..1f,
                modifier = Modifier.padding(16.dp)
            )
            Button(
                onClick = {
                    Log.d("Explain", "Capture Image button clicked")
                    val photoFile = File(
                        context.getExternalFilesDir(null),
                        "${System.currentTimeMillis()}.jpg"
                    )
                    Log.d("Explain", "${photoFile.absolutePath}")

                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                    imageCapture?.takePicture(
                        outputOptions, ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onError(exc: ImageCaptureException) {
                                Log.e("Explain", "Image capture failed", exc)
                                onError(exc)
                            }

                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                Log.d("Explain", "Image saved successfully")
                                val savedUri = Uri.fromFile(photoFile)
                                val bitmap = MediaStore.Images.Media.getBitmap(
                                    context.contentResolver,
                                    savedUri
                                )
                                val croppedBitmap = cropToAspectRatio(bitmap, AspectRatio.RATIO_4_3)
                                val rotatedBitmap = rotateImageIfRequired(croppedBitmap, savedUri)
                                val preprocessedBitmap = preprocessImage(rotatedBitmap)
                                onImageCaptured(preprocessedBitmap)
                                Log.d("Explain", "Image captured and processed")
                            }
                        }
                    )
                }
            ) {
                Text("Capture Image")
            }
            Button(
                onClick = {
                    flashEnabled = !flashEnabled
                    imageCapture?.flashMode = if (flashEnabled) {
                        ImageCapture.FLASH_MODE_ON
                    } else {
                        ImageCapture.FLASH_MODE_OFF
                    }
                }
            ) {
                Text(if (flashEnabled) "Flash On" else "Flash Off")
            }
        }
    }
}

fun cropToAspectRatio(bitmap: Bitmap, aspectRatio: Int): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val targetWidth: Int
    val targetHeight: Int

    if (aspectRatio == AspectRatio.RATIO_4_3) {
        if (width * 3 > height * 4) {
            targetWidth = height * 4 / 3
            targetHeight = height
        } else {
            targetWidth = width
            targetHeight = width * 3 / 4
        }
    } else {
        // Handle other aspect ratios if needed
        targetWidth = width
        targetHeight = height
    }

    val xOffset = (width - targetWidth) / 2
    val yOffset = (height - targetHeight) / 2

    return Bitmap.createBitmap(bitmap, xOffset, yOffset, targetWidth, targetHeight)
}

fun rotateImageIfRequired(img: Bitmap, selectedImage: Uri): Bitmap {
    val ei = ExifInterface(selectedImage.path!!)
    return when (ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(img, 90)
        ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(img, 180)
        ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(img, 270)
        else -> img
    }
}

fun rotateImage(img: Bitmap, degree: Int): Bitmap {
    val matrix = android.graphics.Matrix()
    matrix.postRotate(degree.toFloat())
    return Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)
}


//improve image functions
fun convertToGrayscale(bitmap: Bitmap): Bitmap {
    // Ensure the bitmap is not in HARDWARE config
    val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val width = mutableBitmap.width
    val height = mutableBitmap.height
    val grayscaleBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    for (x in 0 until width) {
        for (y in 0 until height) {
            val pixel = mutableBitmap.getPixel(x, y)
            val gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
            grayscaleBitmap.setPixel(x, y, Color.rgb(gray, gray, gray))
        }
    }
    return grayscaleBitmap
}

fun removeNoise(bitmap: Bitmap): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val blurredBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(blurredBitmap)
    val paint = Paint()
    paint.isAntiAlias = true
    paint.isDither = true
    paint.isFilterBitmap = true

    // Apply Gaussian blur
    val scale = 0.5f
    val scaledBitmap =
        Bitmap.createScaledBitmap(bitmap, (width * scale).toInt(), (height * scale).toInt(), true)
    val blurredScaledBitmap =
        Bitmap.createBitmap(scaledBitmap.width, scaledBitmap.height, Bitmap.Config.ARGB_8888)
    val blurCanvas = Canvas(blurredScaledBitmap)
    blurCanvas.drawBitmap(scaledBitmap, 0f, 0f, paint)
    canvas.drawBitmap(Bitmap.createScaledBitmap(blurredScaledBitmap, width, height, true), 0f, 0f, paint)

    return blurredBitmap
}

fun binarizeImage(bitmap: Bitmap): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val binarizedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    for (x in 0 until width) {
        for (y in 0 until height) {
            val pixel = bitmap.getPixel(x, y)
            val gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
            val binary = if (gray > 128) 255 else 0
            binarizedBitmap.setPixel(x, y, Color.rgb(binary, binary, binary))
        }
    }
    return binarizedBitmap
}

fun adjustContrast(bitmap: Bitmap, contrast: Float): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val contrastedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(contrastedBitmap)
    val paint = Paint()
    val contrastMatrix = android.graphics.ColorMatrix()
    contrastMatrix.set(arrayOf(
        contrast, 0f, 0f, 0f, 0f,
        0f, contrast, 0f, 0f, 0f,
        0f, 0f, contrast, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    ).toFloatArray())
    paint.colorFilter = android.graphics.ColorMatrixColorFilter(contrastMatrix)
    canvas.drawBitmap(bitmap, 0f, 0f, paint)
    return contrastedBitmap
}

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