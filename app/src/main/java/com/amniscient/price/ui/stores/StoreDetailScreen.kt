package com.amniscient.price.ui.stores

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.rememberCoroutineScope
import com.amniscient.price.data.LocationService
import com.amniscient.price.ui.components.LocalSnackbar
import com.amniscient.price.ui.components.rememberLocationPermission
import kotlinx.coroutines.launch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.amniscient.price.data.PriceRepository
import com.amniscient.price.data.SettingsStore
import com.amniscient.price.data.StoreDetail
import com.amniscient.price.domain.Money
import com.amniscient.price.ui.components.AmniTopBar
import com.amniscient.price.ui.components.Eyebrow
import com.amniscient.price.ui.components.Panel
import com.amniscient.price.ui.components.PriceText
import com.amniscient.price.ui.components.SectionHeader
import com.amniscient.price.ui.components.StatTile
import com.amniscient.price.ui.components.appViewModel
import com.amniscient.price.ui.components.relativeTime
import com.amniscient.price.ui.theme.Amni
import com.amniscient.price.ui.theme.AmniText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlin.math.roundToInt

class StoreDetailViewModel(
    private val repository: PriceRepository,
    private val settings: SettingsStore,
    private val location: LocationService,
    private val storeId: Long,
) : ViewModel() {
    val detail: StateFlow<StoreDetail?> =
        repository.storeDetail(storeId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val currentStoreId = settings.currentStoreId
    fun setCurrent(id: Long) = settings.setCurrentStore(id)
    fun hasLocationPermission() = location.hasPermission()

    /** Pins the store where you're standing. Returns false if no location fix was available. */
    suspend fun pinHere(): Boolean {
        val (here, region) = location.hereWithRegion() ?: return false
        repository.setStoreLocation(storeId, here, region)
        return true
    }
}

@Composable
fun StoreDetailScreen(storeId: Long, onBack: () -> Unit, onOpenProduct: (Long) -> Unit, onSetLocation: (Long) -> Unit) {
    val vm = appViewModel { StoreDetailViewModel(it.repository, it.settings, it.location, storeId) }
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val pin: () -> Unit = {
        scope.launch { snackbar.showSnackbar(if (vm.pinHere()) "Pinned to your location" else "Couldn't get a location fix") }
    }
    val askLocation = rememberLocationPermission { granted -> if (granted) pin() }
    val detail by vm.detail.collectAsStateWithLifecycle()
    val current by vm.currentStoreId.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        AmniTopBar(title = detail?.store?.name ?: "Store", eyebrow = detail?.store?.location ?: "Store", onBack = onBack)
        val d = detail ?: return@Column
        val cheaperHere = d.items.count { it.cheapestElsewhere != null && it.priceCents <= it.cheapestElsewhere }
        val compared = d.items.count { it.cheapestElsewhere != null }

        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Rank", d.rank?.let { "#$it" } ?: "—", Modifier.weight(1f), valueColor = if (d.rank == 1) Amni.palette.brass else MaterialTheme.colorScheme.onSurface)
                    val premium = d.score?.let { ((it.index - 1) * 100).roundToInt() }
                    StatTile(
                        "Vs cheapest",
                        when {
                            premium == null -> "—"
                            premium <= 0 -> "Best"
                            else -> "+$premium%"
                        },
                        Modifier.weight(1f),
                        valueColor = when {
                            premium == null -> MaterialTheme.colorScheme.onSurface
                            premium <= 0 -> Amni.palette.deal
                            else -> Amni.palette.rise
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Items", d.items.size.toString(), Modifier.weight(1f))
                    StatTile("Visits", d.visits.toString(), Modifier.weight(1f))
                }
                if (compared > 0) {
                    Text(
                        "Cheapest or tied on $cheaperHere of $compared items you've also seen elsewhere.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (current == d.store.id) {
                    OutlinedButton(onClick = {}, enabled = false, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                        Text("You're shopping here")
                    }
                } else {
                    Button(onClick = { vm.setCurrent(d.store.id) }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                        Text("I'm shopping here")
                    }
                }
            }
            item {
                SectionHeader("Location")
                Panel {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Place, null, tint = Amni.palette.brass)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            val pos = d.store.position
                            Text(
                                if (pos == null) "Not on the price map yet" else d.store.region ?: "On the map",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                if (pos == null) "Add a location to compare prices by area." else "%.4f, %.4f".format(pos.lat, pos.lng),
                                style = if (pos == null) MaterialTheme.typography.bodySmall else AmniText.priceSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider(color = Amni.palette.hairline)
                    Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { if (vm.hasLocationPermission()) pin() else askLocation() }) {
                            Icon(Icons.Default.MyLocation, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("I'm here now")
                        }
                        TextButton(onClick = { onSetLocation(d.store.id) }) {
                            Icon(Icons.Default.Map, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Pick on map")
                        }
                    }
                }
            }
            if (d.items.isNotEmpty()) {
                item {
                    SectionHeader("Prices here")
                    Panel {
                        d.items.forEachIndexed { i, item ->
                            if (i > 0) HorizontalDivider(color = Amni.palette.hairline)
                            Row(
                                Modifier.fillMaxWidth().clickable { onOpenProduct(item.product.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.product.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(relativeTime(item.observedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    val elsewhere = item.cheapestElsewhere
                                    PriceText(
                                        item.priceCents,
                                        color = if (elsewhere != null && item.priceCents <= elsewhere) Amni.palette.deal else MaterialTheme.colorScheme.onSurface,
                                    )
                                    when {
                                        elsewhere == null -> Eyebrow("only here")
                                        item.priceCents < elsewhere -> Text("−${Money.format(elsewhere - item.priceCents)} vs others", style = AmniText.priceSmall, color = Amni.palette.deal)
                                        item.priceCents > elsewhere -> Text("+${Money.format(item.priceCents - elsewhere)} vs best", style = AmniText.priceSmall, color = Amni.palette.rise)
                                        else -> Eyebrow("tied")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
