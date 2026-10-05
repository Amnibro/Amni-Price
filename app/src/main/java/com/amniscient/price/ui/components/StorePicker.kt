package com.amniscient.price.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import com.amniscient.price.AmniPriceApp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.amniscient.price.data.StoreEntity
import com.amniscient.price.ui.theme.Amni

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorePicker(
    stores: List<StoreEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onAddStore: (name: String, location: String?, pinHere: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Store",
) {
    var expanded by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    val selected = stores.firstOrNull { it.id == selectedId }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.displayName ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("Choose a store") },
            leadingIcon = { Icon(Icons.Default.Storefront, null, tint = Amni.palette.brass) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            stores.forEach { store ->
                DropdownMenuItem(
                    text = { Text(store.name + (store.location?.let { " · $it" } ?: "")) },
                    onClick = { onSelect(store.id); expanded = false },
                )
            }
            DropdownMenuItem(
                text = { Text("Add new store…") },
                leadingIcon = { Icon(Icons.Default.Add, null) },
                onClick = { expanded = false; showAdd = true },
            )
        }
    }

    if (showAdd) {
        StoreDialog(
            title = "New store",
            onDismiss = { showAdd = false },
            onConfirm = { name, location, pinHere -> onAddStore(name, location, pinHere); showAdd = false },
            offerPinHere = true,
        )
    }
}

@Composable
fun StoreDialog(
    title: String,
    initialName: String = "",
    initialLocation: String = "",
    onDismiss: () -> Unit,
    onConfirm: (name: String, location: String?, pinHere: Boolean) -> Unit,
    offerPinHere: Boolean = false,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialName) }
    var location by remember { mutableStateOf(initialLocation) }
    // Default on when we already have permission: you usually add a store while standing in it.
    var pinHere by remember {
        mutableStateOf(offerPinHere && (context.applicationContext as AmniPriceApp).container.location.hasPermission())
    }
    val askLocation = rememberLocationPermission { granted -> pinHere = granted }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = Amni.palette.panel,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location (optional)") },
                    placeholder = { Text("e.g. Main St") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                if (offerPinHere) {
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PinDrop, null, tint = Amni.palette.brass)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Pin to where I am", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Puts the store on the price map. Stays on this phone.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = pinHere, onCheckedChange = { on ->
                            if (on && !(context.applicationContext as AmniPriceApp).container.location.hasPermission()) askLocation() else pinHere = on
                        })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim(), location.trim().ifEmpty { null }, pinHere) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
