package com.amniscient.price.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amniscient.price.domain.Money
import com.amniscient.price.ui.theme.Amni
import com.amniscient.price.ui.theme.AmniText
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** The brand mark: a small accent square followed by condensed caps, as on amni-scient.com app bars. */
@Composable
fun BrandMark(modifier: Modifier = Modifier, text: String = "Amni-Price") {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(10.dp))
        Text(text.uppercase(Locale.ROOT), style = AmniText.barTitle, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Flat app bar with a hairline underneath. Title in condensed caps; optional brass eyebrow above. */
@Composable
fun AmniTopBar(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    onBack: (() -> Unit)? = null,
    brand: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(start = if (onBack != null) 4.dp else 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            }
            Column(Modifier.weight(1f)) {
                if (eyebrow != null) {
                    Text(eyebrow.uppercase(Locale.ROOT), style = AmniText.eyebrow, color = Amni.palette.brass)
                }
                if (brand) {
                    BrandMark(text = title)
                } else {
                    Text(
                        title.uppercase(Locale.ROOT),
                        style = AmniText.barTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            actions()
        }
        HorizontalDivider(color = Amni.palette.hairline)
    }
}

/** Section label: quiet condensed caps, never an accent shout. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(Locale.ROOT), style = AmniText.eyebrow, color = color, modifier = modifier)
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Eyebrow(title, Modifier.weight(1f))
        action?.invoke()
    }
}

/** A surface panel: hairline border, 4dp radius, no shadow. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else Amni.palette.hairline
    Surface(
        modifier = modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = MaterialTheme.shapes.medium,
        color = Amni.palette.panel,
        border = BorderStroke(1.dp, border),
    ) {
        Column(content = content)
    }
}

@Composable
fun PriceText(
    cents: Long?,
    modifier: Modifier = Modifier,
    style: TextStyle = AmniText.price,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(cents?.let(Money::format) ?: "—", style = style, color = color, modifier = modifier, maxLines = 1)
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Panel(modifier) {
        Column(Modifier.padding(14.dp)) {
            Eyebrow(label)
            Spacer(Modifier.height(6.dp))
            Text(value, style = AmniText.price, color = valueColor, maxLines = 1)
        }
    }
}

/** ↑ 8% / ↓ 3%. Always icon + number, never color alone. */
@Composable
fun TrendBadge(percent: Double, modifier: Modifier = Modifier) {
    val up = percent > 0
    val color = if (up) Amni.palette.rise else Amni.palette.deal
    Row(
        modifier
            .border(1.dp, color.copy(alpha = 0.45f), MaterialTheme.shapes.small)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (up) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, null, tint = color, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(2.dp))
        Text(formatPercent(abs(percent)), style = AmniText.priceSmall, color = color)
    }
}

fun formatPercent(value: Double): String {
    val r = (value * 10).roundToInt() / 10.0
    return if (r >= 10 || r == r.toInt().toDouble()) "${r.roundToInt()}%" else "$r%"
}

/** A small tag: condensed caps in a hairline box. */
@Composable
fun Tag(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(Locale.ROOT),
        style = AmniText.eyebrow,
        color = color,
        modifier = modifier
            .border(1.dp, color.copy(alpha = 0.4f), MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(56.dp).border(1.dp, Amni.palette.hairline, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = Amni.palette.brass) }
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (actionLabel != null && onAction != null) {
                OutlinedButton(onClick = onAction, shape = MaterialTheme.shapes.small) { Text(actionLabel) }
            }
        }
    }
}
