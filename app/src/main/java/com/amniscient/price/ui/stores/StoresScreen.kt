package com.amniscient.price.ui.stores

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.amniscient.price.data.PriceRepository
import com.amniscient.price.data.SettingsStore
import com.amniscient.price.data.StoreEntity
import com.amniscient.price.ui.compare.EmptyHint
import com.amniscient.price.ui.components.StoreDialog
import com.amniscient.price.ui.components.appViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

    fun export(context: Context) = viewModelScope.launch {
        val csv = repository.exportCsv()
        val file = withContext(Dispatchers.IO) {
            File(context.cacheDir, "exports").apply { mkdirs() }
                .resolve("amni-price-export.csv")
                .apply { writeText(csv) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val share = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(share, "Share price list"))
    }

    fun import(context: Context, uri: android.net.Uri) = viewModelScope.launch {
        val result = runCatching {
            val text = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            repository.importCsv(text)
        }
        val msg = result.fold({ "Imported $it prices" }, { "Import failed: ${it.message}" })
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoresScreen() {
    val vm = appViewModel { StoresViewModel(it.repository, it.settings) }
    val stores by vm.stores.collectAsStateWithLifecycle()
    val currentId by vm.currentStoreId.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<StoreEntity?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(context, uri)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Stores") },
                actions = {
                    IconButton(onClick = { importer.launch(arrayOf("text/*", "application/csv", "text/csv")) }) {
                        Icon(Icons.Default.FileUpload, "Import CSV")
                    }
                    IconButton(onClick = { vm.export(context) }) { Icon(Icons.Default.FileDownload, "Export CSV") }
                },
            )
            if (stores.isEmpty()) {
                EmptyHint("Add the stores you shop at.\nTap a store to make it your current one while scanning.")
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(stores, key = { it.id }) { store ->
                        ListItem(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            headlineContent = { Text(store.name) },
                            supportingContent = store.location?.let { { Text(it) } },
                            leadingContent = {
                                IconButton(onClick = { vm.setCurrent(store.id) }) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Set as current store",
                                        tint = if (store.id == currentId) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                    )
                                }
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { editing = store }) { Icon(Icons.Default.Edit, "Edit") }
                                    IconButton(onClick = { vm.delete(store) }) { Icon(Icons.Default.Delete, "Delete") }
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
        FloatingActionButton(onClick = { adding = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Default.Add, "Add store")
        }
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
}
