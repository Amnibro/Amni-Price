package com.amniscient.price.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.foundation.text.KeyboardOptions
import com.amniscient.price.data.StoreEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorePicker(
    stores: List<StoreEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onAddStore: (name: String, location: String?) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Store",
) {
    var expanded by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    val selected = stores.firstOrNull { it.id == selectedId }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("Choose a store") },
            leadingIcon = { Icon(Icons.Default.Storefront, null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            singleLine = true,
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
            onConfirm = { name, location -> onAddStore(name, location); showAdd = false },
        )
    }
}

@Composable
fun StoreDialog(
    title: String,
    initialName: String = "",
    initialLocation: String = "",
    onDismiss: () -> Unit,
    onConfirm: (name: String, location: String?) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var location by remember { mutableStateOf(initialLocation) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim(), location.trim().ifEmpty { null }) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
