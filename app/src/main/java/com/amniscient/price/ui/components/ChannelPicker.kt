package com.amniscient.price.ui.components
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amniscient.price.data.PriceChannel
@Composable
fun ChannelPicker(selected: PriceChannel, onSelect: (PriceChannel) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Eyebrow("Where you saw it")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PriceChannel.entries.forEach { c -> FilterChip(selected = c == selected, onClick = { onSelect(c) }, label = { Text(c.label) }) }
        }
    }
}
