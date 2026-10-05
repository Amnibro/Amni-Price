package com.amniscient.price.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amniscient.price.BuildConfig
import com.amniscient.price.data.PriceRepository
import com.amniscient.price.data.SettingsStore
import com.amniscient.price.data.ThemeMode
import com.amniscient.price.data.UnitSystem
import com.amniscient.price.ui.components.AmniTopBar
import com.amniscient.price.ui.components.BrandMark
import com.amniscient.price.ui.components.LocalSnackbar
import com.amniscient.price.ui.components.Panel
import com.amniscient.price.ui.components.SectionHeader
import com.amniscient.price.ui.components.appViewModel
import com.amniscient.price.ui.theme.Amni
import com.amniscient.price.ui.theme.AmniText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

class SettingsViewModel(val repository: PriceRepository, val settings: SettingsStore) : ViewModel() {
    suspend fun export(context: Context) {
        val csv = repository.exportCsv()
        val file = withContext(Dispatchers.IO) {
            File(context.cacheDir, "exports").apply { mkdirs() }
                .resolve("amni-price-${LocalDate.now()}.csv")
                .apply { writeText(csv) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val share = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Amni-Price export")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(share, "Share price list"))
    }

    suspend fun import(context: Context, uri: Uri): Result<Int> = runCatching {
        val text = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
        repository.importCsv(text)
    }

    suspend fun loadSample() = com.amniscient.price.data.DemoSeeder.seed(repository)

    suspend fun clearAll() {
        repository.clearAll()
        settings.setCurrentStore(null)
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onManageStores: () -> Unit) {
    val vm = appViewModel { SettingsViewModel(it.repository, it.settings) }
    val theme by vm.settings.theme.collectAsStateWithLifecycle()
    val units by vm.settings.units.collectAsStateWithLifecycle()
    val haptics by vm.settings.haptics.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = vm.import(context, uri)
            snackbar.showSnackbar(result.fold({ "Imported $it prices" }, { "Import failed: ${it.message}" }))
        }
    }

    Column(Modifier.fillMaxSize()) {
        AmniTopBar(title = "Settings", onBack = onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            SectionHeader("Appearance")
            Panel {
                Column(Modifier.padding(16.dp)) {
                    Text("Theme", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Segmented(ThemeMode.entries, theme, { it.label }, vm.settings::setTheme)
                }
            }

            SectionHeader("Units")
            Panel {
                Column(Modifier.padding(16.dp)) {
                    Text("Unit prices", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "How price per size is shown when comparing package sizes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Segmented(UnitSystem.entries, units, { it.label.substringBefore(" (") }, vm.settings::setUnits)
                }
            }

            SectionHeader("Scanning")
            Panel {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Haptic feedback", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "A light tick when a price or barcode is recognized.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = haptics, onCheckedChange = vm.settings::setHaptics)
                }
            }

            SectionHeader("Data")
            Panel {
                NavRow(Icons.Default.Storefront, "Manage stores", "Rename, delete, choose where you're shopping", onClick = onManageStores)
                HorizontalDivider(color = Amni.palette.hairline)
                NavRow(Icons.Default.FileDownload, "Export CSV", "Back up or share every price you've logged") {
                    scope.launch { vm.export(context) }
                }
                HorizontalDivider(color = Amni.palette.hairline)
                NavRow(Icons.Default.FileUpload, "Import CSV", "Merge a price list from a friend or another phone") {
                    importer.launch(arrayOf("text/*", "text/csv", "application/csv", "application/octet-stream"))
                }
                HorizontalDivider(color = Amni.palette.hairline)
                if (BuildConfig.DEBUG) {
                    NavRow(Icons.Default.Science, "Load sample data", "Debug builds only: four stores, eight weeks of prices") {
                        scope.launch {
                            vm.loadSample()
                            snackbar.showSnackbar("Sample data loaded")
                        }
                    }
                    HorizontalDivider(color = Amni.palette.hairline)
                }
                NavRow(Icons.Default.DeleteForever, "Erase all data", "Remove every store, product, price and list item", tint = MaterialTheme.colorScheme.error) {
                    confirmClear = true
                }
            }

            SectionHeader("Privacy")
            Panel {
                Row(Modifier.padding(16.dp)) {
                    Icon(Icons.Default.Shield, null, tint = Amni.palette.brass)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Text recognition and barcode scanning run on this phone. Photos are never saved or uploaded, " +
                            "there are no accounts, ads or trackers, and your prices and location only leave the device when you export them. " +
                            "The price map downloads map images for the area you're viewing; nothing about your prices is sent.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            SectionHeader("About")
            Panel {
                Column(Modifier.padding(16.dp)) {
                    BrandMark()
                    Spacer(Modifier.height(8.dp))
                    Text("Version ${BuildConfig.VERSION_NAME}", style = AmniText.priceSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Consumer-side price intelligence. Retailers use algorithms to set prices; this is yours.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                HorizontalDivider(color = Amni.palette.hairline)
                NavRow(Icons.Default.Public, "amni-scient.com", "Made by Amniscient, LLC") {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://amni-scient.com")))
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Amni.palette.panel,
            title = { Text("Erase everything?") },
            text = { Text("All stores, products, prices and your shopping list will be deleted. Export first if you want a backup.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        vm.clearAll()
                        snackbar.showSnackbar("All data erased")
                    }
                }) { Text("Erase", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size, MaterialTheme.shapes.small),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary,
                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                    inactiveContainerColor = Color.Transparent,
                    activeBorderColor = MaterialTheme.colorScheme.primary,
                    inactiveBorderColor = MaterialTheme.colorScheme.outline,
                ),
                icon = {},
            ) { Text(label(option).uppercase(), style = AmniText.eyebrow) }
        }
    }
}

@Composable
private fun NavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (tint == MaterialTheme.colorScheme.onSurface) Amni.palette.brass else tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = tint)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

