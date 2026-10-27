package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.model.UnlockMode

@Composable
fun UnlockModeSelector(selected: UnlockMode, enabled: Boolean = true, onSelect: (UnlockMode) -> Unit) {
    Column(Modifier.fillMaxWidth().selectableGroup()) {
        Text("Unlock method", style = MaterialTheme.typography.titleMedium)
        UnlockMode.entries.forEach { mode ->
            Row(Modifier.fillMaxWidth().selectable(selected == mode, enabled = enabled,
                role = Role.RadioButton, onClick = { onSelect(mode) }).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected == mode, onClick = null, enabled = enabled)
                Spacer(Modifier.width(8.dp))
                Text(mode.label)
            }
        }
    }
}
