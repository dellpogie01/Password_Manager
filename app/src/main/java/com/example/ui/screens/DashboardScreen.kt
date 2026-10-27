package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.VaultEntry
import com.example.viewmodel.VaultViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    viewModel: VaultViewModel,
    onLockClicked: () -> Unit,
    onSettingsClicked: () -> Unit,
    onAddEntryClicked: () -> Unit,
    onEditEntryClicked: (VaultEntry) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries by viewModel.entries.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    var revealedPasswords = remember { mutableStateMapOf<Long, Boolean>() }
    var entryForFinalConfirmation by remember { mutableStateOf<VaultEntry?>(null) }
    var entryToDelete by remember { mutableStateOf<VaultEntry?>(null) }

    var deleting by remember { mutableStateOf(false) }

    val categories = listOf("All", "Personal", "Work", "Banking", "Social", "Other")

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()

        scope.launch {
            delay(30_000)
            try {
                val currentClip = clipboard.primaryClip
                if (currentClip != null && currentClip.itemCount > 0 && currentClip.getItemAt(0).text == text) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("Empty", ""))
                    Toast.makeText(context, "Clipboard cleared for security", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = "Vault", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Password Manager", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = onLockClicked) {
                        Icon(imageVector = Icons.Default.LockReset, contentDescription = "Lock Now")
                    }
                    IconButton(onClick = onSettingsClicked) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddEntryClicked,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Entry")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search vault entries...") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { viewModel.setSelectedCategory(cat) },
                        label = { Text(cat) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = "Empty",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No vault entries found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        val isRevealed = revealedPasswords[entry.id] == true
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onEditEntryClicked(entry) },
                                    onLongClick = { entryToDelete = entry },
                                    onLongClickLabel = "Delete entry"
                                ),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = entry.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                viewModel.updateEntry(entry.copy(isFavorite = !entry.isFavorite))
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (entry.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                                                contentDescription = "Favorite",
                                                tint = if (entry.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                            Text(entry.category, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                }

                                if (entry.username.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = entry.username,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        IconButton(
                                            onClick = { copyToClipboard("Username", entry.username) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy Username", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isRevealed) entry.password else "••••••••••••",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row {
                                        IconButton(
                                            onClick = { revealedPasswords[entry.id] = !isRevealed },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = "Reveal",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { copyToClipboard("Password", entry.password) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy Password", modifier = Modifier.size(16.dp))
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

    if (entryToDelete != null) {
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Delete Entry") },
            text = { Text("Are you sure you want to delete '${entryToDelete?.title}'? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        entryForFinalConfirmation = entryToDelete
                        entryToDelete = null
                    }
                ) {
                    Text("Confirm", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
    entryForFinalConfirmation?.let { entry ->
        AlertDialog(
            onDismissRequest = { if (!deleting) entryForFinalConfirmation = null },
            title = { Text("Permanently Delete Entry?") },
            text = { Text("Disclaimer: '${entry.title}' and all its saved credentials will be permanently deleted from this vault. This action cannot be undone, and the app cannot recover the deleted entry. Confirm again to proceed.") },
            confirmButton = {
                TextButton(enabled = !deleting, onClick = {
                    deleting = true
                    scope.launch {
                        val deleted = viewModel.deleteEntry(entry.id)
                        deleting = false
                        if (deleted) {
                            revealedPasswords.remove(entry.id)
                            entryForFinalConfirmation = null
                            Toast.makeText(context, "Entry deleted", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Unable to delete entry. Please try again.", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text(if (deleting) "Deleting..." else "Confirm Deletion", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(enabled = !deleting, onClick = { entryForFinalConfirmation = null }) { Text("Cancel") }
            }
        )
    }
}
