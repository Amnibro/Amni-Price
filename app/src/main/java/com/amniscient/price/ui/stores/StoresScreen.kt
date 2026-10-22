package com.amniscient.price.ui.stores

import com.amniscient.price.R
import com.amniscient.price.tr
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.amniscient.price.data.LocationService
import com.amniscient.price.data.PriceRepository
import com.amniscient.price.data.SettingsStore
import com.amniscient.price.data.StoreEntity
import com.amniscient.price.ui.components.AmniTopBar
import com.amniscient.price.ui.components.EmptyState
import com.amniscient.price.ui.components.Panel
import com.amniscient.price.ui.components.StoreDialog
import com.amniscient.price.ui.components.appViewModel
import com.amniscient.price.ui.theme.Amni
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StoresViewModel(
    private val repository: PriceRepository,
    private val settings: SettingsStore,
    private val location: LocationService,
) : ViewModel() {
    val stores: StateFlow<List<StoreEntity>> =
        repository.allStores.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val currentStoreId = settings.currentStoreId

    fun add(name: String, branch: String?, pinHere: Boolean) = viewModelScope.launch {
        val pin = if (pinHere) location.hereWithRegion() else null
        repository.addStore(name, branch, pin?.first, pin?.second)
    }
    fun update(store: StoreEntity) = viewModelScope.launch { repository.updateStore(store) }
    fun delete(store: StoreEntity) = viewModelScope.launch {
        if (settings.currentStoreId.value == store.id) settings.setCurrentStore(null)
        repository.deleteStore(store)
    }
    fun setCurrent(id: Long) = settings.setCurrentStore(id)
}

@Composable
fun StoresScreen(onBack: () -> Unit, onOpenStore: (Long) -> Unit) {
    val vm = appViewModel { StoresViewModel(it.repository, it.settings, it.location) }
    val stores by vm.stores.collectAsStateWithLifecycle()
    val currentId by vm.currentStoreId.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<StoreEntity?>(null) }
    var deleting by remember { mutableStateOf<StoreEntity?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AmniTopBar(title = tr(R.string.s_stores), eyebrow = tr(R.string.s_x_saved, stores.size), onBack = onBack)
            if (stores.isEmpty()) {
                EmptyState(
                    Icons.Default.Storefront,
                    tr(R.string.s_no_stores_yet),
                    tr(R.string.s_add_the_stores_you_shop),
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp)) {
                    item {
                        Panel {
                            stores.forEachIndexed { i, store ->
                                if (i > 0) HorizontalDivider(color = Amni.palette.hairline)
                                Row(
                                    Modifier.fillMaxWidth().clickable { onOpenStore(store.id) }.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    IconButton(onClick = { vm.setCurrent(store.id) }) {
                                        Icon(
                                            if (store.id == currentId) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = tr(R.string.s_shopping_here),
                                            tint = if (store.id == currentId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(store.name, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            listOfNotNull(
                                                if (store.kind == com.amniscient.price.data.StoreKind.RESTAURANT) tr(R.string.s_restaurant) else null,
                                                store.location,
                                                store.region,
                                                if (store.position == null) tr(R.string.s_no_map_location) else null,
                                                if (store.id == currentId) tr(R.string.s_shopping_here) else null,
                                            ).joinToString(" · ").ifEmpty { tr(R.string.s_tap_for_details) },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(onClick = { vm.update(store.copy(kind = if (store.kind == com.amniscient.price.data.StoreKind.RESTAURANT) com.amniscient.price.data.StoreKind.GROCERY else com.amniscient.price.data.StoreKind.RESTAURANT)) }) {
                                        Icon(if (store.kind == com.amniscient.price.data.StoreKind.RESTAURANT) Icons.Default.Restaurant else Icons.Default.Storefront, if (store.kind == com.amniscient.price.data.StoreKind.RESTAURANT) tr(R.string.s_restaurant_tap_to_mark_as) else tr(R.string.s_store_tap_to_mark_as), tint = if (store.kind == com.amniscient.price.data.StoreKind.RESTAURANT) Amni.palette.brass else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { editing = store }) { Icon(Icons.Default.Edit, tr(R.string.s_edit), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    IconButton(onClick = { deleting = store }) { Icon(Icons.Default.Delete, tr(R.string.s_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text(tr(R.string.s_add_store)) },
            shape = MaterialTheme.shapes.small,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (adding) {
        StoreDialog(
            title = tr(R.string.s_new_store),
            onDismiss = { adding = false },
            onConfirm = { n, l, pin -> vm.add(n, l, pin); adding = false },
            offerPinHere = true,
        )
    }
    editing?.let { store ->
        StoreDialog(
            title = tr(R.string.s_edit_store),
            initialName = store.name,
            initialLocation = store.location.orEmpty(),
            onDismiss = { editing = null },
            onConfirm = { n, l, _ -> vm.update(store.copy(name = n, location = l)); editing = null },
        )
    }
    deleting?.let { store ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Amni.palette.panel,
            title = { Text(tr(R.string.s_delete_x, store.name)) },
            text = { Text(tr(R.string.s_every_price_logged_at_this)) },
            confirmButton = {
                TextButton(onClick = { vm.delete(store); deleting = null }) { Text(tr(R.string.s_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(tr(R.string.s_cancel)) } },
        )
    }
}
