package com.amniscient.price.ui.onboarding

import com.amniscient.price.R
import com.amniscient.price.tr
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.amniscient.price.ui.components.BrandMark
import com.amniscient.price.ui.components.Eyebrow
import com.amniscient.price.ui.theme.Amni
import kotlinx.coroutines.launch

private data class Page(val icon: ImageVector, val title: String, val body: String)

private val pages = listOf(
    Page(
        Icons.Default.DocumentScanner,
        tr(R.string.s_point_at_any_price_tag),
        tr(R.string.s_the_camera_reads_the_price),
    ),
    Page(
        Icons.AutoMirrored.Filled.ReceiptLong,
        tr(R.string.s_or_snap_the_receipt),
        tr(R.string.s_every_line_item_is_pulled),
    ),
    Page(
        Icons.Default.Insights,
        tr(R.string.s_see_who_s_really_cheapest),
        tr(R.string.s_amni_price_ranks_your_stores),
    ),
    Page(
        Icons.Default.Shield,
        tr(R.string.s_your_data_stays_yours),
        tr(R.string.s_retailers_price_with_algorithms_this),
    ),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(modifier = Modifier.weight(1f))
            if (!last) TextButton(onClick = onDone) { Text(tr(R.string.s_skip)) }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { index ->
            val page = pages[index]
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Box(
                    Modifier.size(72.dp).border(1.dp, Amni.palette.brass.copy(alpha = 0.5f), MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center,
                ) { Icon(page.icon, null, tint = Amni.palette.brass, modifier = Modifier.size(32.dp)) }
                Spacer(Modifier.height(28.dp))
                Eyebrow("%02d / %02d".format(index + 1, pages.size), color = Amni.palette.brass)
                Spacer(Modifier.height(8.dp))
                Text(page.title, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Text(page.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pages.indices.forEach { i ->
                    val active = i == pager.currentPage
                    val w by animateDpAsState(if (active) 22.dp else 8.dp, label = tr(R.string.s_dot))
                    val c by animateColorAsState(if (active) MaterialTheme.colorScheme.primary else Amni.palette.hairline, label = tr(R.string.s_dotcolor))
                    Box(Modifier.height(4.dp).width(w).background(c, MaterialTheme.shapes.extraSmall))
                }
            }
            Button(
                onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.height(48.dp),
            ) { Text(if (last) tr(R.string.s_start_saving) else tr(R.string.s_next)) }
        }
        Spacer(Modifier.fillMaxWidth().height(8.dp))
    }
}
