package com.amniscient.price.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amniscient.price.domain.Money
import com.amniscient.price.ui.components.StorePicker
import com.amniscient.price.ui.components.appViewModel
import com.amniscient.price.ui.theme.DealColor
import com.google.mlkit.vision.common.InputImage

@Composable
fun ScanScreen(
    onShelfCaptured: () -> Unit,
    onReceiptCaptured: () -> Unit,
    onOpenProduct: (Long) -> Unit,
) {
    val vm = appViewModel { ScanViewModel(it.repository, it.settings, it.vision, it.scanSession) }
    val state by vm.state.collectAsStateWithLifecycle()
    val stores by vm.stores.collectAsStateWithLifecycle()
    val currentStoreId by vm.currentStoreId.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching { InputImage.fromFilePath(context, uri) }
                .onSuccess { vm.processReceipt(it, onReady = onReceiptCaptured) }
        }
    }

    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        StorePicker(
            stores = stores,
            selectedId = currentStoreId,
            onSelect = vm::selectStore,
            onAddStore = vm::addStore,
            label = "Shopping at",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            ScanMode.entries.forEachIndexed { i, mode ->
                SegmentedButton(
                    selected = state.mode == mode,
                    onClick = { vm.setMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, ScanMode.entries.size),
                ) { Text(if (mode == ScanMode.SHELF) "Shelf tag" else "Receipt") }
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black),
        ) {
            if (hasCamera) {
                CameraPreview(
                    analyzer = vm.analyzer,
                    imageCapture = imageCapture,
                    onCamera = { camera = it },
                    modifier = Modifier.fillMaxSize(),
                )
                ViewFinder(state.mode)
            } else {
                PermissionPrompt(onRequest = { permission.launch(Manifest.permission.CAMERA) })
            }

            if (state.mode == ScanMode.SHELF) {
                LiveResultCard(
                    state = state,
                    canQuickSave = vm.canQuickSave,
                    onQuickSave = vm::quickSave,
                    onClear = vm::clearLive,
                    onOpenProduct = onOpenProduct,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                )
            } else {
                Text(
                    "Lay the receipt flat and fit it in the frame",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                )
            }

            if (state.processing) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            state.message?.let { msg ->
                Snackbar(
                    modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                    action = { TextButton(onClick = vm::dismissMessage) { Text("OK") } },
                ) { Text(msg) }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = state.mode == ScanMode.RECEIPT,
            ) { Icon(Icons.Default.PhotoLibrary, "Pick receipt photo") }

            ShutterButton(enabled = hasCamera && !state.processing) {
                when (state.mode) {
                    ScanMode.SHELF -> {
                        vm.captureShelf()
                        onShelfCaptured()
                    }
                    ScanMode.RECEIPT -> takeReceiptPhoto(context, imageCapture, vm, onReceiptCaptured)
                }
            }

            FilledTonalIconButton(
                onClick = {
                    torchOn = !torchOn
                    camera?.cameraControl?.enableTorch(torchOn)
                },
                enabled = camera?.cameraInfo?.hasFlashUnit() == true,
            ) { Icon(if (torchOn) Icons.Default.FlashOn else Icons.Default.FlashOff, "Flashlight") }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
private fun takeReceiptPhoto(
    context: android.content.Context,
    imageCapture: ImageCapture,
    vm: ScanViewModel,
    onReady: () -> Unit,
) {
    imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val media = image.image
                if (media == null) {
                    image.close()
                    return
                }
                val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
                vm.processReceipt(input, onReady = onReady, onFinally = { image.close() })
            }

            override fun onError(exception: ImageCaptureException) = Unit
        },
    )
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(76.dp)
            .clip(CircleShape)
            .border(4.dp, MaterialTheme.colorScheme.primary, CircleShape)
            .padding(8.dp)
            .clip(CircleShape)
            .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.CameraAlt, "Capture", tint = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
private fun ViewFinder(mode: ScanMode) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val shape = RoundedCornerShape(16.dp)
        val frame = if (mode == ScanMode.SHELF) {
            Modifier.fillMaxWidth(0.8f).height(180.dp)
        } else {
            Modifier.fillMaxWidth(0.85f).fillMaxSize(0.85f)
        }
        Box(frame.border(2.dp, Color.White.copy(alpha = 0.8f), shape))
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Camera access lets you scan price tags and receipts.", color = Color.White, textAlign = TextAlign.Center)
        Text(
            "Everything is processed on your phone. Nothing is uploaded.",
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text("Allow camera") }
    }
}

@Composable
private fun LiveResultCard(
    state: ScanUiState,
    canQuickSave: Boolean,
    onQuickSave: () -> Unit,
    onClear: () -> Unit,
    onOpenProduct: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val live = state.live
    val empty = live.priceCents == null && live.productName.isBlank() && live.barcode.isBlank()
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
    ) {
        if (empty) {
            Text(
                "Point at a price tag or barcode",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Card
        }
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        live.productName.ifBlank { "Unknown product" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (live.barcode.isNotBlank()) {
                            Icon(Icons.Default.QrCode, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(live.barcode, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.width(8.dp))
                        }
                        if (live.sizeText.isNotBlank()) Text(live.sizeText, style = MaterialTheme.typography.bodySmall)
                        if (live.onSale) {
                            Spacer(Modifier.width(8.dp))
                            Text("SALE", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Text(
                    live.priceCents?.let(Money::format) ?: "—",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onClear) { Icon(Icons.Default.Close, "Clear") }
            }
            state.known?.let { known ->
                val cheapest = known.cheapestCents
                AssistChip(
                    onClick = { onOpenProduct(known.product.id) },
                    label = {
                        Text(
                            if (cheapest != null) {
                                "Best known: ${Money.format(cheapest)} at ${known.cheapestStore} · ${known.storeCount} stores"
                            } else {
                                "Seen before. Tap for details"
                            },
                            color = if (cheapest != null && live.priceCents != null && cheapest < live.priceCents) {
                                DealColor
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    },
                )
            }
            if (canQuickSave) {
                TextButton(onClick = onQuickSave) {
                    Icon(Icons.Default.Check, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Quick save")
                }
            }
        }
    }
}
