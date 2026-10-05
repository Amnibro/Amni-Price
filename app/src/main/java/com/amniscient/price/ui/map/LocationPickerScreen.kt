package com.amniscient.price.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.amniscient.price.data.LocationService
import com.amniscient.price.data.PriceRepository
import com.amniscient.price.data.StoreEntity
import com.amniscient.price.domain.Geo
import com.amniscient.price.domain.LatLng
import com.amniscient.price.ui.components.AmniTopBar
import com.amniscient.price.ui.components.Eyebrow
import com.amniscient.price.ui.components.LocalMapTilesOnline
import com.amniscient.price.ui.components.LocalSnackbar
import com.amniscient.price.ui.components.appViewModel
import com.amniscient.price.ui.components.rememberLocationPermission
import com.amniscient.price.map.MapTiles
import com.amniscient.price.ui.theme.Amni
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LocationPickerViewModel(
    private val repository: PriceRepository,
    private val location: LocationService,
    private val storeId: Long,
) : ViewModel() {
    val store: StateFlow<StoreEntity?> = repository.allStores.map { list -> list.firstOrNull { it.id == storeId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var region by mutableStateOf("")

    /** Where to open the map: the store, else roughly where your other stores are. */
    suspend fun startPosition(): LatLng? {
        val all = repository.allStores.first()
        val s = all.firstOrNull { it.id == storeId }
        s?.region?.let { region = it }
        return s?.position ?: all.mapNotNull { it.position }.takeIf { it.isNotEmpty() }?.let(Geo::centroid)
    }

    fun hasPermission() = location.hasPermission()
    suspend fun here(): LatLng? = location.current()

    suspend fun save(position: LatLng) {
        val name = region.trim().ifEmpty { location.regionFor(position) }
        repository.setStoreLocation(storeId, position, name)
    }

    fun clear() = viewModelScope.launch { repository.setStoreLocation(storeId, null, null) }
}

@Composable
fun LocationPickerScreen(storeId: Long, onDone: () -> Unit) {
    val vm = appViewModel { LocationPickerViewModel(it.repository, it.location, storeId) }
    val store by vm.store.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val controller = remember { PickerController() }
    var start by remember { mutableStateOf<LatLng?>(null) }
    var ready by remember { mutableStateOf(false) }
    val askLocation = rememberLocationPermission { granted ->
        if (granted) scope.launch { vm.here()?.let { controller.moveTo(it) } }
    }

    LaunchedEffect(Unit) {
        start = vm.startPosition()
        ready = true
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        AmniTopBar(title = "Set location", eyebrow = store?.displayName, onBack = onDone)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (ready) {
                LocationPickerMap(start, controller, Modifier.fillMaxSize(), tilesOnline = LocalMapTilesOnline.current)
            }
            // Fixed crosshair: the map moves under it.
            Box(Modifier.align(Alignment.Center), contentAlignment = Alignment.Center) {
                Box(Modifier.size(44.dp).border(2.dp, Amni.palette.brass, CircleShape))
                Box(Modifier.size(6.dp).background(Amni.palette.brass, CircleShape))
            }
            FilledTonalIconButton(
                onClick = {
                    if (vm.hasPermission()) scope.launch { vm.here()?.let { controller.moveTo(it) } } else askLocation()
                },
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Amni.palette.panel),
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).offset(y = (-16).dp),
            ) { Icon(Icons.Default.MyLocation, "Go to my location") }
            Text(
                MapTiles.attribution,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomEnd).background(Amni.palette.panel.copy(alpha = 0.85f)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Text(
                "Drag the map so the ring sits on the store",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp)
                    .background(Amni.palette.panel.copy(alpha = 0.92f), MaterialTheme.shapes.small)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Eyebrow("Area")
            OutlinedTextField(
                value = vm.region,
                onValueChange = { vm.region = it },
                placeholder = { Text("Town or neighborhood (filled in automatically)") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (store?.position != null) {
                    OutlinedButton(onClick = { vm.clear(); onDone() }, shape = MaterialTheme.shapes.small) { Text("Remove") }
                }
                Button(
                    onClick = {
                        val center = controller.center ?: return@Button
                        scope.launch {
                            vm.save(center)
                            snackbar.showSnackbar("Location saved")
                            onDone()
                        }
                    },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Save location") }
            }
        }
    }
}

