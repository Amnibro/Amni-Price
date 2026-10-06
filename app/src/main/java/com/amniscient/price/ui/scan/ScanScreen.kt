package com.amniscient.price.ui.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amniscient.price.domain.Money
import com.amniscient.price.domain.PriceInsights
import com.amniscient.price.domain.Observation
import com.amniscient.price.ui.components.Eyebrow
import com.amniscient.price.ui.components.LocalSnackbar
import com.amniscient.price.ui.components.LocalUiPrefs
import com.amniscient.price.ui.components.Panel
import com.amniscient.price.ui.components.PriceText
import com.amniscient.price.ui.components.StorePicker
import com.amniscient.price.ui.components.Tag
import com.amniscient.price.ui.components.TrendBadge
import com.amniscient.price.ui.components.appViewModel
import com.amniscient.price.ui.theme.Amni
import com.amniscient.price.ui.theme.AmniText
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun ScanScreen(
    onShelfCaptured: () -> Unit,
    onReceiptCaptured: () -> Unit,
    onOpenProduct: (Long) -> Unit,
) {
    val vm = appViewModel { ScanViewModel(it.repository, it.settings, it.vision, it.scanSession, it.location) }
    val state by vm.state.collectAsStateWithLifecycle()
    val stores by vm.stores.collectAsStateWithLifecycle()
    val currentStoreId by vm.currentStoreId.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val prefs = LocalUiPrefs.current
    val snackbar = LocalSnackbar.current

    LaunchedEffect(Unit) {
        vm.consumeModeRequest()
        vm.detectNearbyStore()
    }

    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    LaunchedEffect(Unit) { if (!hasCamera) permission.launch(Manifest.permission.CAMERA) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            vm.processReceipt({ it.readText(uri) }, onReady = onReceiptCaptured)
        }
    }

    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }

    // A light tick when something new is recognized, so you can keep your eyes on the shelf.
    LaunchedEffect(state.detectionTick) {
        if (state.detectionTick > 0 && prefs.haptics) {
            view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP,
            )
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.dismissMessage()
        }
    }
    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            delay(900)
            focusPoint = null
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        // Controls sit on graphite so the camera feed stays the hero.
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                StorePicker(
                    stores = stores,
                    selectedId = currentStoreId,
                    onSelect = vm::selectStore,
                    onAddStore = vm::addStore,
                    label = "Shopping at",
                    modifier = Modifier.weight(1f),
                )
                if (state.savedThisSession > 0) {
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${state.savedThisSession}", style = AmniText.price, color = Amni.palette.deal)
                        Eyebrow("saved")
                    }
                }
            }
            state.suggestedStore?.let { s ->
                Row(
                    Modifier
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp)
                        .fillMaxWidth()
                        .background(Amni.palette.brassDim, MaterialTheme.shapes.small)
                        .clickable(onClick = vm::acceptSuggestion)
                        .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.NearMe, null, tint = Amni.palette.brass, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("You're at ${s.displayName}? Tap to switch", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = vm::dismissSuggestion) { Icon(Icons.Default.Close, "Dismiss", Modifier.size(18.dp)) }
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp, 10.dp)) {
                ScanMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = state.mode == mode,
                        onClick = { vm.setMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, ScanMode.entries.size, MaterialTheme.shapes.small),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                            inactiveContainerColor = Color.Transparent,
                            activeBorderColor = MaterialTheme.colorScheme.primary,
                            inactiveBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                        icon = {},
                    ) { Text(mode.label, style = AmniText.eyebrow) }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (hasCamera) {
                CameraPreview(
                    analyzer = vm.analyzer,
                    imageCapture = imageCapture,
                    onCamera = { camera = it },
                    onTapFocus = { x, y -> focusPoint = Offset(x, y) },
                    modifier = Modifier.fillMaxSize(),
                )
                ViewFinder(state.mode)
                focusPoint?.let { FocusRing(it) }
            } else {
                PermissionPrompt(onRequest = { permission.launch(Manifest.permission.CAMERA) })
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = state.mode == ScanMode.SHELF && hasCamera,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            ) {
                LiveResultCard(
                    state = state,
                    canQuickSave = vm.canQuickSave(state, currentStoreId),
                    hasStore = currentStoreId != null,
                    onQuickSave = vm::quickSave,
                    onEdit = { vm.captureShelf(); onShelfCaptured() },
                    onClear = vm::clearLive,
                    onOpenProduct = onOpenProduct,
                )
            }
            if (state.mode != ScanMode.SHELF && hasCamera) {
                Text(
                    if (state.mode == ScanMode.MENU) "Fit the menu, or pick a delivery-app screenshot" else "Lay the receipt flat and fit it inside the frame",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp)
                        .background(Color.Black.copy(alpha = 0.55f), MaterialTheme.shapes.small)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            if (state.processing) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Amni.palette.brass, strokeWidth = 2.dp)
                        Spacer(Modifier.height(12.dp))
                        Eyebrow(if (state.mode == ScanMode.MENU) "Reading menu" else "Reading receipt", color = Color.White)
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 32.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val sideColors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Amni.palette.panel2)
            FilledTonalIconButton(
                onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = state.mode != ScanMode.SHELF,
                colors = sideColors,
            ) { Icon(Icons.Default.PhotoLibrary, "Pick photo or screenshot") }

            ShutterButton(enabled = hasCamera && !state.processing) {
                when (state.mode) {
                    ScanMode.SHELF -> {
                        vm.captureShelf()
                        onShelfCaptured()
                    }
                    ScanMode.RECEIPT, ScanMode.MENU -> takeReceiptPhoto(context, imageCapture, vm, onReceiptCaptured)
                }
            }

            FilledTonalIconButton(
                onClick = {
                    torchOn = !torchOn
                    camera?.cameraControl?.enableTorch(torchOn)
                },
                enabled = camera?.cameraInfo?.hasFlashUnit() == true,
                colors = sideColors,
            ) { Icon(if (torchOn) Icons.Default.FlashOn else Icons.Default.FlashOff, "Flashlight") }
        }
    }
}

private fun takeReceiptPhoto(context: Context, imageCapture: ImageCapture, vm: ScanViewModel, onReady: () -> Unit) {
    imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                vm.processReceipt({ it.readText(image) }, onReady = onReady, onFinally = { image.close() })
            }

            override fun onError(exception: ImageCaptureException) = Unit
        },
    )
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    val ring = if (enabled) Amni.palette.brass else MaterialTheme.colorScheme.outline
    Box(
        Modifier
            .size(72.dp)
            .border(2.dp, ring, CircleShape)
            .padding(6.dp)
            .clip(CircleShape)
            .background(if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
            .clickable(enabled = enabled, onClick = onClick),
    )
}

/** Brass corner brackets: the shelf frame is wide and short, the receipt frame tall. */
@Composable
private fun ViewFinder(mode: ScanMode) {
    val brass = Amni.palette.brass
    val heightFraction by animateFloatAsState(if (mode == ScanMode.SHELF) 0.34f else 0.86f, tween(300), label = "frame")
    Box(Modifier.fillMaxSize(), contentAlignment = if (mode == ScanMode.SHELF) Alignment.Center else Alignment.TopCenter) {
        Canvas(
            Modifier
                .fillMaxWidth(if (mode == ScanMode.SHELF) 0.84f else 0.88f)
                .fillMaxHeight(heightFraction)
                .padding(top = if (mode == ScanMode.SHELF) 0.dp else 16.dp),
        ) {
            val len = 28.dp.toPx()
            val stroke = 3.dp.toPx()
            val w = size.width
            val h = size.height
            fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                drawLine(brass, Offset(x, y), Offset(x + dx * len, y), stroke, StrokeCap.Square)
                drawLine(brass, Offset(x, y), Offset(x, y + dy * len), stroke, StrokeCap.Square)
            }
            corner(0f, 0f, 1f, 1f)
            corner(w, 0f, -1f, 1f)
            corner(0f, h, 1f, -1f)
            corner(w, h, -1f, -1f)
        }
    }
}

@Composable
private fun FocusRing(at: Offset) {
    val density = LocalDensity.current
    val sizePx = with(density) { 56.dp.toPx() }
    Box(
        Modifier
            .offset { IntOffset((at.x - sizePx / 2).roundToInt(), (at.y - sizePx / 2).roundToInt()) }
            .size(56.dp)
            .border(1.5.dp, Color.White, CircleShape),
    )
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Eyebrow("Camera", color = Amni.palette.brass)
        Spacer(Modifier.height(8.dp))
        Text(
            "Point, and the price is read for you.",
            color = Color.White, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Images are processed on your phone and never uploaded.",
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRequest, shape = MaterialTheme.shapes.small) { Text("Allow camera") }
    }
}

@Composable
private fun LiveResultCard(
    state: ScanUiState,
    canQuickSave: Boolean,
    hasStore: Boolean,
    onQuickSave: () -> Unit,
    onEdit: () -> Unit,
    onClear: () -> Unit,
    onOpenProduct: (Long) -> Unit,
) {
    val live = state.live
    val empty = live.priceCents == null && live.productName.isBlank() && live.barcode.isBlank()
    Panel {
        if (empty) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp, color = Amni.palette.brass)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (hasStore) "Point at a price tag or barcode" else "Pick the store you're in, then point at a price tag",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@Panel
        }
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        live.productName.ifBlank { "Unknown product" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (live.barcode.isNotBlank()) {
                            Icon(Icons.Default.QrCode, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(live.barcode, style = AmniText.priceSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (live.sizeText.isNotBlank()) Tag(live.sizeText)
                        if (live.onSale) Tag("Sale", color = Amni.palette.brass)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    PriceText(live.priceCents, style = AmniText.priceLarge)
                    val price = live.priceCents
                    val last = state.lastHere
                    if (price != null && last != null) {
                        PriceInsights.changeAgainst(
                            Observation(last.productId, last.storeId, last.priceCents, last.observedAt), price,
                        )?.let { TrendBadge(it) }
                    }
                }
                IconButton(onClick = onClear) { Icon(Icons.Default.Close, "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            state.known?.let { known ->
                val cheapest = known.cheapestCents
                if (cheapest != null) {
                    val better = live.priceCents != null && cheapest < live.priceCents
                    Row(
                        Modifier
                            .padding(top = 8.dp, end = 8.dp)
                            .fillMaxWidth()
                            .clickable { onOpenProduct(known.product.id) }
                            .background(if (better) Amni.palette.brassDim else Color.Transparent, MaterialTheme.shapes.small)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Eyebrow(if (better) "Cheaper at" else "Best known", Modifier.weight(1f))
                        Text("${known.cheapestStore} ", style = MaterialTheme.typography.bodySmall)
                        PriceText(cheapest, style = AmniText.priceSmall, color = if (better) Amni.palette.brass else Amni.palette.deal)
                    }
                }
            }

            Row(Modifier.padding(top = 10.dp, end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Review")
                }
                Button(onClick = onQuickSave, enabled = canQuickSave, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save")
                }
            }
        }
    }
}
