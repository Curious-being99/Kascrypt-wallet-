package com.example.kaspawallet.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.kaspawallet.ui.theme.KaspaPrimary
import com.example.kaspawallet.ui.theme.KaspaSurface
import com.example.kaspawallet.ui.theme.KaspaSurfaceVariant
import com.example.kaspawallet.ui.theme.KaspaTextPrimary
import com.example.kaspawallet.ui.theme.KaspaTextSecondary
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real Camera and Gallery QR Code Scanner.
 * Utilizes Android CameraX for live video feed and ZXing for hardware-accelerated QR matrix decoding.
 */
@Composable
fun RealQrCodeScannerDialog(
    onDismissRequest: () -> Unit,
    onQrCodeScanned: (address: String, amountKas: Double?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var isTorchOn by remember { mutableStateOf(false) }
    var cameraControlRef by remember { mutableStateOf<Camera?>(null) }
    var scanError by remember { mutableStateOf<String?>(null) }
    val isScanningLocked = remember { AtomicBoolean(false) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                cameraControlRef = null
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                if (cameraProviderFuture.isDone) {
                    cameraProviderFuture.get().unbindAll()
                }
                cameraExecutor.shutdown()
            } catch (_: Exception) {
            }
        }
    }

    // Visual Media Picker for picking a QR code from device gallery
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    val decodedText = decodeQrFromBitmap(bitmap)
                    if (decodedText != null) {
                        if (isKaspaAddressOrUri(decodedText)) {
                            triggerHapticFeedback(context)
                            val (parsedAddress, parsedAmount) = parseKaspaQrPayload(decodedText)
                            onQrCodeScanned(parsedAddress, parsedAmount)
                            onDismissRequest()
                        } else {
                            scanError = "QR is not a valid Kaspa wallet address"
                        }
                    } else {
                        scanError = "No QR code could be detected in the selected image"
                    }
                } else {
                    scanError = "Could not load image"
                }
            } catch (e: Exception) {
                Log.e("QrScanner", "Error decoding photo QR", e)
                scanError = "Failed to process photo: ${e.localizedMessage}"
            }
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (hasCameraPermission) {
                    // Real CameraX Live Preview & Frame Analysis
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }

                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                            cameraProviderFuture.addListener({
                                try {
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }

                                    val imageAnalysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()

                                    val reader = MultiFormatReader().apply {
                                        setHints(
                                            mapOf(
                                                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                                                DecodeHintType.TRY_HARDER to true
                                            )
                                        )
                                    }

                                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                        if (isScanningLocked.get()) {
                                            imageProxy.close()
                                            return@setAnalyzer
                                        }

                                        val scannedText = decodeQrFromImageProxy(imageProxy, reader)
                                        if (scannedText != null) {
                                            if (isKaspaAddressOrUri(scannedText)) {
                                                if (isScanningLocked.compareAndSet(false, true)) {
                                                    ContextCompat.getMainExecutor(ctx).execute {
                                                        triggerHapticFeedback(ctx)
                                                        val (address, amount) = parseKaspaQrPayload(scannedText)
                                                        onQrCodeScanned(address, amount)
                                                        onDismissRequest()
                                                    }
                                                }
                                            } else {
                                                ContextCompat.getMainExecutor(ctx).execute {
                                                    scanError = "QR code is not a Kaspa address"
                                                }
                                            }
                                        }
                                        imageProxy.close()
                                    }

                                    cameraProvider.unbindAll()
                                    val camera = cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        imageAnalysis
                                    )
                                    cameraControlRef = camera
                                } catch (e: Exception) {
                                    Log.e("QrScanner", "Camera binding failure", e)
                                    scanError = "Camera initialization failed: ${e.localizedMessage}"
                                }
                            }, ContextCompat.getMainExecutor(ctx))

                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Camera permission requested UI
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Videocam,
                            contentDescription = null,
                            tint = KaspaPrimary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Camera Access Required",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "To scan physical Kaspa QR codes with your device's camera, please grant camera permission. You can also pick a QR image directly from your photo gallery.",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = KaspaPrimary),
                            modifier = Modifier.testTag("request_camera_permission_button")
                        ) {
                            Text("Grant Camera Permission", color = Color(0xFF091114), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Viewfinder Overlay Reticle
                ViewfinderOverlay(modifier = Modifier.fillMaxSize())

                // Top Controls: Title, Flash Toggle, and Close Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp, start = 20.dp, end = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .testTag("close_qr_scanner_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close scanner", tint = Color.White)
                    }

                    Text(
                        "Scan Kaspa QR",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    IconButton(
                        onClick = {
                            val camera = cameraControlRef
                            if (camera != null && camera.cameraInfo.hasFlashUnit()) {
                                isTorchOn = !isTorchOn
                                camera.cameraControl.enableTorch(isTorchOn)
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .testTag("toggle_torch_button")
                    ) {
                        Icon(
                            if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Toggle flashlight",
                            tint = if (isTorchOn) KaspaPrimary else Color.White
                        )
                    }
                }

                // Bottom Controls: Instructions & "Pick from Photo Library" option
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (scanError != null) {
                        Text(
                            text = scanError!!,
                            color = Color(0xFFFF5252),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    } else {
                        Text(
                            text = "Point camera at Kaspa address QR code",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(48.dp)
                            .testTag("scan_from_gallery_button")
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan from Photos", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewfinderOverlay(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "laser")
    val laserOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_pos"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Target Scanning Window
        Box(
            modifier = Modifier
                .size(260.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(2.dp, KaspaPrimary.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
        ) {
            // Animated Scanning Laser Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .padding(top = (laserOffset * 256).dp)
                    .background(KaspaPrimary)
            )
        }
    }
}

/**
 * Decodes QR code from CameraX ImageProxy using ZXing PlanarYUVLuminanceSource
 */
private fun decodeQrFromImageProxy(imageProxy: ImageProxy, reader: MultiFormatReader): String? {
    return try {
        val plane = imageProxy.planes[0]
        val buffer = plane.buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)

        val width = imageProxy.width
        val height = imageProxy.height
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        val rotatedData: ByteArray
        val rotatedWidth: Int
        val rotatedHeight: Int

        if (rotationDegrees == 90 || rotationDegrees == 270) {
            rotatedData = rotateYuv(data, width, height, rotationDegrees)
            rotatedWidth = height
            rotatedHeight = width
        } else if (rotationDegrees == 180) {
            rotatedData = rotateYuv(data, width, height, 180)
            rotatedWidth = width
            rotatedHeight = height
        } else {
            rotatedData = data
            rotatedWidth = width
            rotatedHeight = height
        }

        val source = PlanarYUVLuminanceSource(
            rotatedData,
            rotatedWidth,
            rotatedHeight,
            0,
            0,
            rotatedWidth,
            rotatedHeight,
            false
        )
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val result = reader.decodeWithState(binaryBitmap)
        reader.reset()
        result?.text
    } catch (_: Exception) {
        reader.reset()
        null
    }
}

/**
 * Rotates Y plane data to match camera sensor rotation
 */
private fun rotateYuv(data: ByteArray, width: Int, height: Int, rotationDegrees: Int): ByteArray {
    val rotated = ByteArray(data.size)
    when (rotationDegrees) {
        90 -> {
            var i = 0
            for (x in 0 until width) {
                for (y in height - 1 downTo 0) {
                    rotated[i++] = data[y * width + x]
                }
            }
        }
        180 -> {
            for (i in data.indices) {
                rotated[data.size - 1 - i] = data[i]
            }
        }
        270 -> {
            var i = 0
            for (x in width - 1 downTo 0) {
                for (y in 0 until height) {
                    rotated[i++] = data[y * width + x]
                }
            }
        }
        else -> System.arraycopy(data, 0, rotated, 0, data.size)
    }
    return rotated
}

/**
 * Decodes QR code from a static Bitmap (e.g. from photo gallery)
 */
fun decodeQrFromBitmap(bitmap: Bitmap): String? {
    return try {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val reader = QRCodeReader()
        val result = reader.decode(binaryBitmap)
        result?.text
    } catch (e: Exception) {
        null
    }
}

/**
 * Parses raw Kaspa QR code string payload into (Address, Amount?)
 * Supports formats:
 * - "kaspa:qq8l8xy3h967q94z48yv6z2g48q32z7y72g48q32z7q"
 * - "kaspa:qq8l8xy3h967q94z48yv6z2g48q32z7y72g48q32z7q?amount=15.5"
 * - "kaspatest:qp8l8xy3h967q94z48yv6z2g48q32z7y72g48q32z7"
 */
fun parseKaspaQrPayload(raw: String): Pair<String, Double?> {
    val clean = raw.trim()
    if (!clean.contains("?")) {
        return Pair(clean, null)
    }
    val parts = clean.split("?", limit = 2)
    val address = parts[0]
    val query = parts.getOrNull(1) ?: return Pair(address, null)
    var amount: Double? = null
    val queryParams = query.split("&")
    for (param in queryParams) {
        val kv = param.split("=", limit = 2)
        if (kv.size == 2 && kv[0].lowercase() == "amount") {
            amount = kv[1].toDoubleOrNull()
        }
    }
    return Pair(address, amount)
}

/**
 * Validates that the scanned QR code payload is strictly a Kaspa wallet address or URI.
 */
fun isKaspaAddressOrUri(raw: String): Boolean {
    val clean = raw.trim().lowercase()
    val addr = if (clean.contains("?")) clean.substringBefore("?") else clean
    return addr.startsWith("kaspa:") ||
            addr.startsWith("kaspatest:") ||
            addr.startsWith("kaspadev:") ||
            addr.startsWith("kaspasim:") ||
            (addr.length in 45..85 && addr.startsWith("q"))
}

private fun triggerHapticFeedback(context: Context) {
    try {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(70)
        }
    } catch (_: Exception) {
    }
}
