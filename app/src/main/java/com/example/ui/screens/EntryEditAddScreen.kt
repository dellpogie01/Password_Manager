package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crypto.CryptoManager
import com.example.model.VaultEntry
import com.example.viewmodel.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditAddScreen(
    viewModel: VaultViewModel,
    entryToEdit: VaultEntry? = null,
    onBack: () -> Unit
) {
    var title by remember { mutableStateOf(entryToEdit?.title ?: "") }
    var username by remember { mutableStateOf(entryToEdit?.username ?: "") }
    var password by remember { mutableStateOf(entryToEdit?.password ?: "") }
    var url by remember { mutableStateOf(entryToEdit?.url ?: "") }
    var category by remember { mutableStateOf(entryToEdit?.category ?: "Personal") }
    var notes by remember { mutableStateOf(entryToEdit?.notes ?: "") }
    var isFavorite by remember { mutableStateOf(entryToEdit?.isFavorite ?: false) }

    var showGeneratorDialog by remember { mutableStateOf(false) }
    var genLength by remember { mutableStateOf(16f) }
    var genUpper by remember { mutableStateOf(true) }
    var genLower by remember { mutableStateOf(true) }
    var genNumbers by remember { mutableStateOf(true) }
    var genSymbols by remember { mutableStateOf(true) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    val categories = listOf("Personal", "Work", "Banking", "Social", "Other")
    var categoryExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (entryToEdit == null) "Add Vault Entry" else "Edit Vault Entry") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (title.isBlank()) {
                                errorMessage = "Title cannot be empty."
                                return@TextButton
                            }
                            if (password.isBlank()) {
                                errorMessage = "Password cannot be empty."
                                return@TextButton
                            }
                            val newEntry = VaultEntry(
                                id = entryToEdit?.id ?: 0L,
                                title = title,
                                username = username,
                                password = password,
                                url = url,
                                category = category,
                                notes = notes,
                                isFavorite = isFavorite
                            )
                            if (entryToEdit == null) {
                                viewModel.addEntry(newEntry)
                            } else {
                                viewModel.updateEntry(newEntry)
                            }
                            onBack()
                        }
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; errorMessage = null },
                label = { Text("Title *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username / Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMessage = null },
                label = { Text("Password *") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { showGeneratorDialog = true }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Generate Password")
                    }
                }
            )

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Website URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Category dropdown
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = !categoryExpanded }
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
                            onClick = {
                                category = cat
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxLines = 5
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isFavorite,
                    onCheckedChange = { isFavorite = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mark as Favorite")
            }

            if (errorMessage != null) {
                Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showGeneratorDialog) {
        AlertDialog(
            onDismissRequest = { showGeneratorDialog = false },
            title = { Text("Generate Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Length: ${genLength.toInt()}")
                    Slider(
                        value = genLength,
                        onValueChange = { genLength = it },
                        valueRange = 8f..32f,
                        steps = 23
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = genUpper, onCheckedChange = { genUpper = it })
                        Text("Uppercase (A-Z)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = genLower, onCheckedChange = { genLower = it })
                        Text("Lowercase (a-z)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = genNumbers, onCheckedChange = { genNumbers = it })
                        Text("Numbers (0-9)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = genSymbols, onCheckedChange = { genSymbols = it })
                        Text("Symbols (!@#$)")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        password = CryptoManager.generatePassword(
                            length = genLength.toInt(),
                            useUpper = genUpper,
                            useLower = genLower,
                            useNumbers = genNumbers,
                            useSymbols = genSymbols
                        )
                        showGeneratorDialog = false
                    }
                ) {
                    Text("Use Password")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGeneratorDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
