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
import androidx.compose.material3.Button
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

class StoreDetailViewModel(repository: PriceRepository, private val settings: SettingsStore, storeId: Long) : ViewModel() {
    val detail: StateFlow<StoreDetail?> =
        repository.storeDetail(storeId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val currentStoreId = settings.currentStoreId
    fun setCurrent(id: Long) = settings.setCurrentStore(id)
}

@Composable
fun StoreDetailScreen(storeId: Long, onBack: () -> Unit, onOpenProduct: (Long) -> Unit) {
    val vm = appViewModel { StoreDetailViewModel(it.repository, it.settings, storeId) }
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
