package com.amniscient.price.ui.settings

import com.amniscient.price.R
import com.amniscient.price.tr
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

class SettingsViewModel(val repository: PriceRepository, val settings: SettingsStore, private val location: com.amniscient.price.data.LocationService) : ViewModel() {
    suspend fun downloadCommunity(): Result<com.amniscient.price.data.CommunityResult> = runCatching {
        val stores = repository.allStores.first()
        val center = location.takeIf { it.hasPermission() }?.current() ?: stores.firstOrNull { it.id == settings.currentStoreId.value }?.position ?: stores.firstOrNull { it.position != null && it.externalId == null }?.position
            ?: error(tr(R.string.s_turn_on_location_or_pin))
        val (pack, generated) = com.amniscient.price.data.CommunityPrices.fetch(center, settings.communityRadiusKm.value)
        val count = repository.importCommunity(pack)
        settings.setCommunityUpdated(generated.take(10))
        com.amniscient.price.data.CommunityResult(pack.stores.size, count, generated)
    }
    suspend fun removeCommunity() {
        repository.removeCommunity()
        settings.setCommunityUpdated(null)
    }
    suspend fun export(context: Context) {
        val csv = repository.exportCsv()
        val file = withContext(Dispatchers.IO) {
            File(context.cacheDir, "exports").apply { mkdirs() }
                .resolve("amni-price-${LocalDate.now()}.csv")
                .apply { writeText(csv) }
        }
        val uri = FileProvider.getUriForFile(context, tr(R.string.s_x_files, context.packageName), file)
        val share = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, tr(R.string.s_amni_price_export))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(share, tr(R.string.s_share_price_list)))
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
    val vm = appViewModel { SettingsViewModel(it.repository, it.settings, it.location) }
    val communityRadius by vm.settings.communityRadiusKm.collectAsStateWithLifecycle()
    val communityUpdated by vm.settings.communityUpdated.collectAsStateWithLifecycle()
    val communityCount by vm.repository.communityPriceCount.collectAsStateWithLifecycle(0)
    var communityBusy by remember { mutableStateOf(false) }
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
            snackbar.showSnackbar(result.fold({ tr(R.string.s_imported_x_prices, it) }, { tr(R.string.s_import_failed_x, it.message) }))
        }
    }

    Column(Modifier.fillMaxSize()) {
        AmniTopBar(title = tr(R.string.s_settings), onBack = onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            SectionHeader(tr(R.string.s_appearance))
            Panel {
                Column(Modifier.padding(16.dp)) {
                    Text(tr(R.string.s_theme), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Segmented(ThemeMode.entries, theme, { it.label }, vm.settings::setTheme)
                }
            }

            SectionHeader(tr(R.string.s_units))
            Panel {
                Column(Modifier.padding(16.dp)) {
                    Text(tr(R.string.s_unit_prices), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        tr(R.string.s_how_price_per_size_is),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Segmented(UnitSystem.entries, units, { it.label.substringBefore(" (") }, vm.settings::setUnits)
                }
            }

            SectionHeader(tr(R.string.s_scanning))
            Panel {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr(R.string.s_haptic_feedback), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            tr(R.string.s_a_light_tick_when_a),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = haptics, onCheckedChange = vm.settings::setHaptics)
                }
            }

            SectionHeader(tr(R.string.s_community_prices))
            Panel {
                Column(Modifier.padding(16.dp)) {
                    Text(tr(R.string.s_prices_near_you_from_open), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        tr(R.string.s_download_recent_shelf_and_receipt) +
                            tr(R.string.s_they_fill_in_the_map),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Segmented(listOf(10, 25, 50, 100), communityRadius, { tr(R.string.s_x_km, it) }, vm.settings::setCommunityRadius)
                }
                HorizontalDivider(color = Amni.palette.hairline)
                NavRow(
                    Icons.Default.FileDownload,
                    if (communityBusy) tr(R.string.s_downloading) else if (communityCount > 0) tr(R.string.s_update_community_prices) else tr(R.string.s_download_community_prices),
                    if (communityCount > 0) tr(R.string.s_x_prices_data_from_x, communityCount, communityUpdated ?: tr(R.string.s_open_prices)) else tr(R.string.s_within_x_km_of_you, communityRadius),
                ) {
                    if (!communityBusy) scope.launch {
                        communityBusy = true
                        val result = vm.downloadCommunity()
                        communityBusy = false
                        snackbar.showSnackbar(result.fold({ if (it.prices == 0) tr(R.string.s_no_community_prices_within_x, communityRadius) else tr(R.string.s_added_x_prices_from_x, it.prices, it.stores) }, { it.message ?: tr(R.string.s_download_failed) }))
                    }
                }
                if (communityCount > 0) {
                    HorizontalDivider(color = Amni.palette.hairline)
                    NavRow(Icons.Default.DeleteForever, tr(R.string.s_remove_community_prices), tr(R.string.s_keeps_everything_you_scanned_yourself)) {
                        scope.launch {
                            vm.removeCommunity()
                            snackbar.showSnackbar(tr(R.string.s_community_prices_removed))
                        }
                    }
                }
                HorizontalDivider(color = Amni.palette.hairline)
                NavRow(Icons.Default.Public, tr(R.string.s_data_open_prices_odbl), tr(R.string.s_open_food_facts_contributors_tap)) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(com.amniscient.price.data.CommunityPrices.LICENSE_URL)))
                }
            }
            SectionHeader(tr(R.string.s_data))
            Panel {
                NavRow(Icons.Default.Storefront, tr(R.string.s_manage_stores), tr(R.string.s_rename_delete_choose_where_you), onClick = onManageStores)
                HorizontalDivider(color = Amni.palette.hairline)
                NavRow(Icons.Default.FileDownload, tr(R.string.s_export_csv), tr(R.string.s_back_up_or_share_every)) {
                    scope.launch { vm.export(context) }
                }
                HorizontalDivider(color = Amni.palette.hairline)
                NavRow(Icons.Default.FileUpload, tr(R.string.s_import_csv), tr(R.string.s_merge_a_price_list_from)) {
                    importer.launch(arrayOf("text/*", "text/csv", "application/csv", "application/octet-stream"))
                }
                HorizontalDivider(color = Amni.palette.hairline)
                if (BuildConfig.DEBUG) {
                    NavRow(Icons.Default.Science, tr(R.string.s_load_sample_data), tr(R.string.s_debug_builds_only_four_stores)) {
                        scope.launch {
                            vm.loadSample()
                            snackbar.showSnackbar(tr(R.string.s_sample_data_loaded))
                        }
                    }
                    HorizontalDivider(color = Amni.palette.hairline)
                }
                NavRow(Icons.Default.DeleteForever, tr(R.string.s_erase_all_data), tr(R.string.s_remove_every_store_product_price), tint = MaterialTheme.colorScheme.error) {
                    confirmClear = true
                }
            }

            SectionHeader(tr(R.string.s_privacy))
            Panel {
                Row(Modifier.padding(16.dp)) {
                    Icon(Icons.Default.Shield, null, tint = Amni.palette.brass)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        tr(R.string.s_text_recognition_and_barcode_scanning) +
                            tr(R.string.s_there_are_no_accounts_ads) +
                            tr(R.string.s_downloading_community_prices_requests_th) +
                            tr(R.string.s_the_price_map_downloads_map),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            SectionHeader(tr(R.string.s_about))
            Panel {
                Column(Modifier.padding(16.dp)) {
                    BrandMark()
                    Spacer(Modifier.height(8.dp))
                    Text(tr(R.string.s_version_x, BuildConfig.VERSION_NAME), style = AmniText.priceSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        tr(R.string.s_consumer_side_price_intelligence_retaile),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                HorizontalDivider(color = Amni.palette.hairline)
                NavRow(Icons.Default.Public, "amni-scient.com", tr(R.string.s_made_by_amniscient_llc)) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://amni-scient.com")))
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Amni.palette.panel,
            title = { Text(tr(R.string.s_erase_everything)) },
            text = { Text(tr(R.string.s_all_stores_products_prices_and)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        vm.clearAll()
                        snackbar.showSnackbar(tr(R.string.s_all_data_erased))
                    }
                }) { Text(tr(R.string.s_erase), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(tr(R.string.s_cancel)) } },
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

