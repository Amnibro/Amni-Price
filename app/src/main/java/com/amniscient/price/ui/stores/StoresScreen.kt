package com.amniscient.price.ui.stores

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

class StoresViewModel(private val repository: PriceRepository, private val settings: SettingsStore) : ViewModel() {
    val stores: StateFlow<List<StoreEntity>> =
        repository.allStores.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val currentStoreId = settings.currentStoreId

    fun add(name: String, location: String?) = viewModelScope.launch { repository.addStore(name, location) }
    fun update(store: StoreEntity) = viewModelScope.launch { repository.updateStore(store) }
    fun delete(store: StoreEntity) = viewModelScope.launch {
        if (settings.currentStoreId.value == store.id) settings.setCurrentStore(null)
        repository.deleteStore(store)
    }
    fun setCurrent(id: Long) = settings.setCurrentStore(id)
}

@Composable
fun StoresScreen(onBack: () -> Unit, onOpenStore: (Long) -> Unit) {
    val vm = appViewModel { StoresViewModel(it.repository, it.settings) }
    val stores by vm.stores.collectAsStateWithLifecycle()
    val currentId by vm.currentStoreId.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<StoreEntity?>(null) }
    var deleting by remember { mutableStateOf<StoreEntity?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AmniTopBar(title = "Stores", eyebrow = "${stores.size} saved", onBack = onBack)
            if (stores.isEmpty()) {
                EmptyState(
                    Icons.Default.Storefront,
                    "No stores yet",
                    "Add the stores you shop at. The selected one is used while scanning so each item is one tap.",
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
                                            contentDescription = "Shopping here",
                                            tint = if (store.id == currentId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(store.name, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            listOfNotNull(store.location, if (store.id == currentId) "Shopping here" else null).joinToString(" · ").ifEmpty { "Tap for details" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(onClick = { editing = store }) { Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    IconButton(onClick = { deleting = store }) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
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
            text = { Text("Add store") },
            shape = MaterialTheme.shapes.small,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (adding) {
        StoreDialog(title = "New store", onDismiss = { adding = false }, onConfirm = { n, l -> vm.add(n, l); adding = false })
    }
    editing?.let { store ->
        StoreDialog(
            title = "Edit store",
            initialName = store.name,
            initialLocation = store.location.orEmpty(),
            onDismiss = { editing = null },
            onConfirm = { n, l -> vm.update(store.copy(name = n, location = l)); editing = null },
        )
    }
    deleting?.let { store ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Amni.palette.panel,
            title = { Text("Delete ${store.name}?") },
            text = { Text("Every price logged at this store is removed too. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(store); deleting = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}
