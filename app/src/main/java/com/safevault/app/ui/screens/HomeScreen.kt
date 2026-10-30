package com.safevault.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.safevault.app.crypto.BiometricKeys
import com.safevault.app.data.ItemType
import com.safevault.app.data.VaultItem
import com.safevault.app.data.VaultRepository
import com.safevault.app.ui.AuthOutcome
import com.safevault.app.ui.LocalActivity
import com.safevault.app.ui.biometricPrompt
import com.safevault.app.ui.components.ItemAvatar
import kotlinx.coroutines.launch

private enum class Filter(val label: String) { ALL("All"), FAVORITES("Favourites"), LOGINS("Logins"), CARDS("Cards") }

fun subtitleOf(item: VaultItem): String = when (item.type) {
    ItemType.LOGIN -> item.login?.username?.ifEmpty { item.login.url } ?: ""
    ItemType.CARD -> listOfNotNull(
        item.card?.bank?.takeIf { it.isNotEmpty() },
        item.card?.last4?.takeIf { it.isNotEmpty() }?.let { "•••• $it" },
    ).joinToString("  ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repo: VaultRepository,
    items: List<VaultItem>,
    onOpen: (String) -> Unit,
    onAdd: (ItemType) -> Unit,
    onSettings: () -> Unit,
    onGenerator: () -> Unit,
) {
    // Search text and filter are not secret, so they may survive configuration changes.
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var showAddSheet by remember { mutableStateOf(false) }

    val visible = remember(items, query, filter) {
        val q = query.trim().lowercase()
        items.filter { item ->
            val matchesFilter = when (filter) {
                Filter.ALL -> true
                Filter.FAVORITES -> item.favorite
                Filter.LOGINS -> item.type == ItemType.LOGIN
                Filter.CARDS -> item.type == ItemType.CARD
            }
            matchesFilter && (
                q.isEmpty() || item.name.lowercase().contains(q) ||
                    item.login?.username?.lowercase()?.contains(q) == true ||
                    item.login?.url?.lowercase()?.contains(q) == true ||
                    item.card?.bank?.lowercase()?.contains(q) == true ||
                    item.card?.last4?.contains(q) == true
                )
        }.sortedByDescending { it.favorite }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your vault") },
                actions = {
                    IconButton(onClick = onGenerator) { Icon(Icons.Outlined.Password, "Password generator") }
                    IconButton(onClick = { repo.lock() }) { Icon(Icons.Outlined.Lock, "Lock now") }
                    IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddSheet = true },
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("Add") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(if (items.size == 1) "Search 1 item" else "Search ${items.size} items") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Clear, "Clear search") }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Filter.entries.forEach { f ->
                        FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                    }
                }
            }
            item { BiometricSuggestion(repo) }

            if (items.isEmpty()) {
                item { EmptyVault(onAdd) }
            } else if (visible.isEmpty()) {
                item {
                    Text(
                        "Nothing matches \"$query\".",
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(visible, key = { it.id }) { item ->
                ListItem(
                    headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        val sub = subtitleOf(item)
                        if (sub.isNotEmpty()) Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    leadingContent = { ItemAvatar(item) },
                    trailingContent = {
                        if (item.favorite) Icon(Icons.Rounded.Star, "Favourite", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    },
                    modifier = Modifier.clickable { onOpen(item.id) },
                )
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            Text("Add to vault", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            ListItem(
                headlineContent = { Text("Login") },
                supportingContent = { Text("Website or app username and password") },
                leadingContent = { Icon(Icons.Outlined.Key, null) },
                modifier = Modifier.clickable { showAddSheet = false; onAdd(ItemType.LOGIN) },
            )
            ListItem(
                headlineContent = { Text("Card") },
                supportingContent = { Text("Debit or credit card, with CVV and PIN") },
                leadingContent = { Icon(Icons.Outlined.CreditCard, null) },
                modifier = Modifier.clickable { showAddSheet = false; onAdd(ItemType.CARD) },
            )
            Spacer(Modifier.size(32.dp))
        }
    }
}

@Composable
private fun EmptyVault(onAdd: (ItemType) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Add your first item", style = MaterialTheme.typography.titleMedium)
        Text(
            "Save a website login or a bank card. Everything stays encrypted on this phone.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onAdd(ItemType.LOGIN) }) { Text("Add login") }
            TextButton(onClick = { onAdd(ItemType.CARD) }) { Text("Add card") }
        }
    }
}

/** One-time card suggesting fingerprint unlock, shown until enabled or dismissed. */
@Composable
private fun BiometricSuggestion(repo: VaultRepository) {
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val meta = repo.meta.value ?: return
    var hidden by remember { mutableStateOf(false) }
    if (hidden || meta.biometricWrappedKey != null || meta.settings.biometricPromptDismissed) return
    if (!remember { BiometricKeys.isAvailable(activity) }) return

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Fingerprint, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Unlock with your fingerprint", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Row {
                    TextButton(onClick = {
                        scope.launch {
                            val cipher = repo.biometricEnrollCipher()
                            val r = activity.biometricPrompt("Turn on fingerprint unlock", cipher = cipher, negativeText = "Cancel")
                            if (r is AuthOutcome.Success) { repo.enableBiometric(r.cipher!!); hidden = true }
                        }
                    }) { Text("Turn on") }
                    TextButton(onClick = {
                        repo.updateSettings { it.copy(biometricPromptDismissed = true) }
                        hidden = true
                    }) { Text("Not now") }
                }
            }
        }
    }
}
