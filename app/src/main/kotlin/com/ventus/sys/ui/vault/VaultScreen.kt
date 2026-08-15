package com.ventus.sys.ui.vault

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ventus.sys.ui.common.verdictColor

@Composable
fun VaultScreen(viewModel: VaultViewModel = hiltViewModel()) {
    val items by viewModel.uiState.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val sort by viewModel.sortOption.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "SIGNAL // VAULT", style = MaterialTheme.typography.headlineSmall)

        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.searchQuery.value = it },
                placeholder = { Text("Search track, artist…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        SortDropdown(current = sort, onSelect = { viewModel.sortOption.value = it })

        Text(
            text = "${items.size} track${if (items.size != 1) "s" else ""}",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "No matching records found.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn {
                items(items, key = { it.id }) { item -> VaultRow(item) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortDropdown(
    current: VaultSort,
    onSelect: (VaultSort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = current.label,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            // Missing menuAnchor() meant tapping this field did nothing at all -
            // readOnly=true gives it no input handler of its own, and the
            // trailing chevron is a bare Icon with no onClick either, so
            // nothing ever flipped `expanded`. MasterVaultScreen's
            // PlaylistFilter already had the correct pattern right next to
            // this bug.
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            VaultSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun VaultRow(item: VaultUiItem) {
    // Explicit Color.Transparent - Surface with no color param defaults to
    // MaterialTheme.colorScheme.surface, a visibly different shade from the
    // actual screen background - same fix as Signals/Master Vault's row
    // Surfaces.
    Surface(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), color = Color.Transparent) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.song, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "${item.artist} · ${item.camelot} · NRG ${item.energy} · VAL ${item.valence} · BPM ${item.bpm}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = "${item.score}%",
                style = MaterialTheme.typography.titleMedium,
                color = verdictColor(item.verdict),
            )
        }
    }
}
