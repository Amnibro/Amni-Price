package com.amniscient.price.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.amniscient.price.data.LocationService
import com.amniscient.price.data.MapData
import com.amniscient.price.data.MapPoint
import com.amniscient.price.data.PriceRepository
import com.amniscient.price.data.ProductSummary
import com.amniscient.price.domain.Geo
import com.amniscient.price.domain.LatLng
import com.amniscient.price.domain.Money
import com.amniscient.price.domain.RegionStat
import com.amniscient.price.domain.RegionalPrices
import com.amniscient.price.domain.normalizeName
import com.amniscient.price.map.MapTiles
import com.amniscient.price.scan.ScanSession
import com.amniscient.price.ui.components.AmniTopBar
import com.amniscient.price.ui.components.EmptyState
import com.amniscient.price.ui.components.Eyebrow
import com.amniscient.price.ui.components.LocalMapTilesOnline
import com.amniscient.price.ui.components.LocalUiPrefs
import com.amniscient.price.ui.components.Panel
import com.amniscient.price.ui.components.SectionHeader
import com.amniscient.price.ui.components.appViewModel
import com.amniscient.price.ui.components.formatPercent
import com.amniscient.price.ui.components.rememberLocationPermission
import com.amniscient.price.ui.theme.Amni
import com.amniscient.price.ui.theme.AmniText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModel(
    private val repository: PriceRepository,
    private val location: LocationService,
    session: ScanSession,
) : ViewModel() {
    /** null = the "Overall" basket view (store price index); otherwise one product. */
    val productId = MutableStateFlow(session.mapProductId.also { session.mapProductId = null })
    val here = MutableStateFlow<LatLng?>(null)
    val selectedStore = MutableStateFlow<Long?>(null)
    val showRegions = MutableStateFlow(true)

    val products: StateFlow<List<ProductSummary>> =
        repository.productSummaries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val data: StateFlow<MapData?> =
        productId.flatMapLatest { id -> if (id == null) repository.basketMap else repository.productMap(id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun hasLocationPermission() = location.hasPermission()

    fun locate() = viewModelScope.launch { location.current()?.let { here.value = it } }

    fun choose(product: Long?) {
        productId.value = product
        selectedStore.value = null
    }
}

@Composable
fun MapScreen(
    onOpenStore: (Long) -> Unit,
    onManageStores: () -> Unit,
) {
    val vm = appViewModel { MapViewModel(it.repository, it.location, it.scanSession) }
    val productId by vm.productId.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val here by vm.here.collectAsStateWithLifecycle()
    val selected by vm.selectedStore.collectAsStateWithLifecycle()
    val showRegions by vm.showRegions.collectAsStateWithLifecycle()
    val imperial = LocalUiPrefs.current.imperialUnits
    var picking by remember { mutableStateOf(false) }
    val askLocation = rememberLocationPermission { granted -> if (granted) vm.locate() }

    LaunchedEffect(Unit) { if (vm.hasLocationPermission()) vm.locate() }

    val product = products.firstOrNull { it.product.id == productId }
    val basket = productId == null
    val format: (Double) -> String = if (basket) {
        { v -> if (v <= 1.005) "BEST" else "+" + formatPercent((v - 1) * 100) }
    } else {
        { v -> Money.format(v.toLong()) }
    }

    Column(Modifier.fillMaxSize()) {
        AmniTopBar(
            title = "Price map",
            eyebrow = if (basket) "Overall · store price index" else product?.product?.name ?: "Product",
            actions = {
                IconButton(onClick = { vm.showRegions.value = !showRegions }) {
                    Icon(if (showRegions) Icons.Default.Layers else Icons.Default.LayersClear, "Toggle regions")
                }
                IconButton(onClick = { if (vm.hasLocationPermission()) vm.locate() else askLocation() }) {
                    Icon(Icons.Default.MyLocation, "My location", tint = if (here != null) Amni.palette.brass else MaterialTheme.colorScheme.onSurface)
                }
            },
        )
        ProductSelector(product, basket, onClick = { picking = true })

        val d = data
        if (d == null) return@Column
        if (d.located.isEmpty()) {
            EmptyState(
                Icons.Default.Map,
                if (d.unlocated.isEmpty()) "Nothing to map yet" else "Put your stores on the map",
                if (d.unlocated.isEmpty()) {
                    "Log prices at a few stores, then give each store a location to compare prices by area."
                } else {
                    "${d.unlocated.size} stores have prices but no location. Open a store and tap Set location, or pin new stores while you're in them."
                },
                actionLabel = "Manage stores",
                onAction = onManageStores,
            )
            return@Column
        }

        val pins = d.located.map { MapPin(it.store.id, it.store.position!!, format(it.value), it.value) }
        val regions = if (showRegions && d.regions.size > 1) {
            d.regions.map { MapRegion(it, it.label) }
        } else {
            emptyList()
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            PriceMapView(
                pins = pins,
                regions = regions,
                here = here,
                selectedStoreId = selected,
                onSelect = { vm.selectedStore.value = it },
                fitKey = productId ?: -1L,
                tilesOnline = LocalMapTilesOnline.current,
                modifier = Modifier.fillMaxSize(),
            )
            Legend(Modifier.align(Alignment.TopStart).padding(10.dp))
            Text(
                MapTiles.attribution,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(Amni.palette.panel.copy(alpha = 0.85f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        HorizontalDivider(color = Amni.palette.hairline)
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 330.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            val sel = d.located.firstOrNull { it.store.id == selected }
            if (sel != null) {
                item { SelectedStore(sel, d, here, imperial, format, onOpenStore) }
            }
            if (d.regions.size > 1) {
                item {
                    SectionHeader(if (basket) "Cheapest areas overall" else "Price by area")
                    RegionTable(d.regions, d, basket, format)
                }
            }
            item {
                SectionHeader(if (here != null) "Cheapest near you" else "Cheapest stores")
                NearbyTable(d, here, imperial, format, onSelect = { vm.selectedStore.value = it })
                if (here == null) {
                    TextButton(onClick = { if (vm.hasLocationPermission()) vm.locate() else askLocation() }) {
                        Icon(Icons.Default.MyLocation, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Show distances from me")
                    }
                }
            }
            if (d.unlocated.isNotEmpty()) {
                item {
                    Text(
                        "${d.unlocated.size} more store${if (d.unlocated.size == 1) "" else "s"} with prices aren't on the map yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp).clickable(onClick = onManageStores),
                    )
                }
            }
        }
    }

    if (picking) {
        ProductPickerDialog(
            products = products.filter { it.storeCount > 0 },
            onDismiss = { picking = false },
            onPick = { vm.choose(it); picking = false },
        )
    }
}

@Composable
private fun ProductSelector(product: ProductSummary?, basket: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .border(1.dp, Amni.palette.hairline, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, null, tint = Amni.palette.brass, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Eyebrow(if (basket) "Comparing" else "Find the lowest price for")
            Text(
                if (basket) "All products (whole basket)" else product?.product?.name ?: "Choose a product",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Default.UnfoldMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Legend(modifier: Modifier) {
    val p = Amni.palette
    Column(
        modifier
            .background(p.panel.copy(alpha = 0.92f), MaterialTheme.shapes.small)
            .border(1.dp, p.hairline, MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LegendRow(p.deal, filled = true, "Cheapest")
        LegendRow(p.ink, filled = false, "Within 10%")
        LegendRow(p.rise, filled = false, "Higher")
    }
}

@Composable
private fun LegendRow(color: Color, filled: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = 16.dp, height = 10.dp)
                .background(if (filled) color else Color.Transparent, MaterialTheme.shapes.extraSmall)
                .border(1.5.dp, color, MaterialTheme.shapes.extraSmall),
        )
        Spacer(Modifier.width(6.dp))
        Text(label.uppercase(), style = AmniText.eyebrow, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SelectedStore(
    point: MapPoint,
    data: MapData,
    here: LatLng?,
    imperial: Boolean,
    format: (Double) -> String,
    onOpenStore: (Long) -> Unit,
) {
    val cheapest = data.cheapest ?: point.value
    val band = RegionalPrices.band(point.value, cheapest)
    Spacer(Modifier.height(12.dp))
    Panel(onClick = { onOpenStore(point.store.id) }, highlighted = true) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(point.store.displayName, style = MaterialTheme.typography.titleMedium)
                val sub = listOfNotNull(
                    point.store.region,
                    here?.let { Geo.formatDistance(Geo.distanceMeters(it, point.store.position!!), imperial) },
                ).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(format(point.value), style = AmniText.price, color = bandColor(band))
                if (point.priceCents != null && point.value > cheapest) {
                    Text("+${Money.format((point.value - cheapest).toLong())} vs cheapest", style = AmniText.priceSmall, color = Amni.palette.rise)
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RegionTable(regions: List<RegionStat>, data: MapData, basket: Boolean, format: (Double) -> String) {
    val names = data.located.associate { it.store.id to it.store.displayName }
    Panel {
        regions.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(color = Amni.palette.hairline)
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(30.dp).border(1.dp, if (i == 0) Amni.palette.brass else Amni.palette.hairline, MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center,
                ) { Text("${i + 1}", style = AmniText.priceSmall, color = if (i == 0) Amni.palette.brass else MaterialTheme.colorScheme.onSurface) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${r.storeIds.size} store${if (r.storeIds.size == 1) "" else "s"} · best: ${names[r.cheapestStoreId] ?: "—"}" +
                            if (basket) "" else " ${format(r.min)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    // Basket mode compares indexes, so one number (vs. the cheapest area) says it all.
                    if (!basket) Text(format(r.average), style = AmniText.priceSmall)
                    Text(
                        if (i == 0) "CHEAPEST AREA" else "+" + formatPercent(r.premiumPercent),
                        style = if (i == 0) AmniText.eyebrow else AmniText.priceSmall,
                        color = if (i == 0) Amni.palette.deal else Amni.palette.rise,
                    )
                }
            }
        }
    }
    Text(
        if (basket) {
            "How much more a typical basket costs in each area than in the cheapest one, from every product compared at 2+ stores."
        } else {
            "Average latest price across the stores in each area, and how it compares with the cheapest area."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun NearbyTable(data: MapData, here: LatLng?, imperial: Boolean, format: (Double) -> String, onSelect: (Long) -> Unit) {
    val cheapest = data.cheapest ?: return
    val options = RegionalPrices.cheapestNearby(data.geoValues(), here).take(8)
    val byId = data.located.associateBy { it.store.id }
    Panel {
        options.forEachIndexed { i, o ->
            val point = byId[o.store.storeId] ?: return@forEachIndexed
            if (i > 0) HorizontalDivider(color = Amni.palette.hairline)
            Row(
                Modifier.fillMaxWidth().clickable { onSelect(point.store.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(point.store.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(point.store.region, o.distanceMeters?.let { Geo.formatDistance(it, imperial) }).joinToString(" · ").ifEmpty { " " },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(format(point.value), style = AmniText.price, color = bandColor(RegionalPrices.band(point.value, cheapest)))
            }
        }
    }
}

@Composable
private fun bandColor(band: RegionalPrices.PriceBand): Color = when (band) {
    RegionalPrices.PriceBand.CHEAPEST -> Amni.palette.deal
    RegionalPrices.PriceBand.NEAR -> MaterialTheme.colorScheme.onSurface
    RegionalPrices.PriceBand.HIGHER -> Amni.palette.rise
}

@Composable
private fun ProductPickerDialog(products: List<ProductSummary>, onDismiss: () -> Unit, onPick: (Long?) -> Unit) {
    var query by remember { mutableStateOf("") }
    val q = normalizeName(query)
    val shown = products.filter { q.isEmpty() || it.product.normalizedName.contains(q) }.sortedBy { it.product.name.lowercase() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Amni.palette.panel,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text("Compare on the map") },
        text = {
            Column {
                OutlinedTextField(
                    query, { query = it },
                    placeholder = { Text("Search products") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true, shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    item {
                        PickerRow("All products", "Whole-basket store price index", onClick = { onPick(null) })
                        HorizontalDivider(color = Amni.palette.hairline)
                    }
                    items(shown, key = { it.product.id }) { s ->
                        PickerRow(
                            s.product.name,
                            "${s.storeCount} store${if (s.storeCount == 1) "" else "s"}" + (s.cheapestCents?.let { " · from ${Money.format(it)}" } ?: ""),
                            onClick = { onPick(s.product.id) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun PickerRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

