package com.amniscient.price.ui.home

import com.amniscient.price.R
import com.amniscient.price.tr
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.amniscient.price.data.Insights
import com.amniscient.price.data.PriceRepository
import com.amniscient.price.data.PriceRow
import com.amniscient.price.data.ShoppingPlan
import com.amniscient.price.domain.Money
import com.amniscient.price.ui.components.AmniTopBar
import com.amniscient.price.ui.components.Eyebrow
import com.amniscient.price.ui.components.Panel
import com.amniscient.price.ui.components.PriceText
import com.amniscient.price.ui.components.SectionHeader
import com.amniscient.price.ui.components.StatTile
import com.amniscient.price.ui.components.TrendBadge
import com.amniscient.price.ui.components.appViewModel
import com.amniscient.price.ui.components.formatPercent
import com.amniscient.price.ui.components.relativeTime
import com.amniscient.price.ui.theme.Amni
import com.amniscient.price.ui.theme.AmniText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(repository: PriceRepository) : ViewModel() {
    val insights: StateFlow<Insights?> =
        repository.insights.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val recent: StateFlow<List<PriceRow>> =
        repository.recentPrices.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val plan: StateFlow<ShoppingPlan?> =
        repository.shoppingPlan.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun HomeScreen(
    onScanShelf: () -> Unit,
    onScanReceipt: () -> Unit,
    onOpenList: () -> Unit,
    onOpenProduct: (Long) -> Unit,
    onOpenStore: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCompare: () -> Unit,
) {
    val vm = appViewModel { HomeViewModel(it.repository) }
    val insights by vm.insights.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val plan by vm.plan.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        AmniTopBar(
            title = tr(R.string.s_amni_price),
            brand = true,
            actions = { IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, tr(R.string.s_settings)) } },
        )
        val i = insights ?: return@Column
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)) {
            item { Hero(i, onScanShelf) }

            item {
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickAction(Icons.Default.DocumentScanner, tr(R.string.s_scan_tag), Modifier.weight(1f), onScanShelf)
                    QuickAction(Icons.AutoMirrored.Filled.ReceiptLong, tr(R.string.s_receipt), Modifier.weight(1f), onScanReceipt)
                    QuickAction(Icons.Default.Checklist, tr(R.string.s_list), Modifier.weight(1f), onOpenList)
                }
            }

            if (i.priceCount > 0) {
                item {
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile(tr(R.string.s_products), i.productCount.toString(), Modifier.weight(1f))
                        StatTile(tr(R.string.s_stores), i.storeCount.toString(), Modifier.weight(1f))
                        StatTile(tr(R.string.s_prices), i.priceCount.toString(), Modifier.weight(1f))
                    }
                }
            }

            i.topStore?.let { top ->
                item {
                    SectionHeader(tr(R.string.s_cheapest_store), action = {
                        TextButton(onClick = onOpenCompare) { Text(tr(R.string.s_ranking)) }
                    })
                    Panel(onClick = { onOpenStore(top.store.id) }, highlighted = true) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EmojiEvents, null, tint = Amni.palette.brass)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(top.store.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    tr(R.string.s_lowest_price_on_x_of, top.score.cheapestCount, top.score.comparedProducts),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            plan?.takeIf { it.plan.pricedCount > 0 }?.let { p ->
                item {
                    SectionHeader(tr(R.string.s_shopping_list))
                    Panel(onClick = onOpenList) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                val stores = p.plan.best?.storeIds?.mapNotNull { p.storeNames[it] }.orEmpty()
                                Text(
                                    tr(R.string.s_x_items, p.plan.pricedCount) + stores.joinToString(" + "),
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (p.plan.savingsVsHighest > 0) {
                                    Text(
                                        tr(R.string.s_saves_x_vs_the_highest, Money.format(p.plan.savingsVsHighest)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Amni.palette.deal,
                                    )
                                }
                            }
                            PriceText(p.plan.best?.totalCents)
                        }
                    }
                }
            }

            if (i.changes.isNotEmpty()) {
                item {
                    SectionHeader(tr(R.string.s_price_watch))
                    i.inflationPercent?.let { pct ->
                        Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = Amni.palette.brass, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                tr(R.string.s_your_prices_are) + (if (pct >= 0) tr(R.string.s_up) else tr(R.string.s_down)) + formatPercent(kotlin.math.abs(pct)) +
                                    tr(R.string.s_on_average_across_x_changes, i.changes.size),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                item {
                    Panel {
                        i.changes.take(5).forEachIndexed { idx, nc ->
                            if (idx > 0) HorizontalDivider(color = Amni.palette.hairline)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenProduct(nc.change.productId) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(nc.productName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${nc.storeName} · ${Money.format(nc.change.previousCents)} → ${Money.format(nc.change.currentCents)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TrendBadge(nc.change.percent)
                            }
                        }
                    }
                }
            }

            if (i.channels.isNotEmpty()) {
                item {
                    SectionHeader(tr(R.string.s_apps_delivery_markup))
                    Panel {
                        i.channels.forEachIndexed { idx, c ->
                            if (idx > 0) HorizontalDivider(color = Amni.palette.hairline)
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(com.amniscient.price.data.PriceChannel.valueOf(c.channel).label, style = MaterialTheme.typography.titleSmall)
                                    Text(tr(R.string.s_vs_the_in_store_price, c.items) + if (c.items == 1) "" else "s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text((if (c.averagePercent >= 0) "+" else "") + formatPercent(c.averagePercent), style = AmniText.price, color = if (c.averagePercent > 0) Amni.palette.rise else Amni.palette.deal)
                            }
                        }
                    }
                }
            }
            if (recent.isNotEmpty()) {
                item { SectionHeader(tr(R.string.s_recently_logged)) }
                item {
                    Panel {
                        recent.take(8).forEachIndexed { idx, row ->
                            if (idx > 0) HorizontalDivider(color = Amni.palette.hairline)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenProduct(row.price.productId) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(row.productName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${row.storeName} · " + (if (row.price.channel != com.amniscient.price.data.PriceChannel.IN_STORE) row.price.channel.label + " · " else "") + relativeTime(row.price.observedAt),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                PriceText(row.price.priceCents, style = AmniText.priceSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hero(i: Insights, onScan: () -> Unit) {
    Panel {
        Column(Modifier.padding(20.dp)) {
            if (i.priceCount == 0) {
                Eyebrow(tr(R.string.s_price_intelligence_for_shoppers), color = Amni.palette.brass)
                Spacer(Modifier.height(8.dp))
                Text(tr(R.string.s_find_out_who_s_really), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    tr(R.string.s_scan_shelf_tags_and_receipts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onScan, shape = MaterialTheme.shapes.small) {
                    Icon(Icons.Default.DocumentScanner, null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr(R.string.s_scan_your_first_price))
                }
            } else {
                Eyebrow(tr(R.string.s_price_gap_you_re_tracking), color = Amni.palette.brass)
                Spacer(Modifier.height(4.dp))
                Text(Money.format(i.potentialSavingsCents), style = AmniText.hero, color = Amni.palette.deal)
                Text(
                    if (i.potentialSavingsCents > 0) {
                        tr(R.string.s_difference_between_the_highest_and)
                    } else {
                        tr(R.string.s_scan_the_same_products_at)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Panel(modifier, onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(6.dp))
            Eyebrow(label, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
