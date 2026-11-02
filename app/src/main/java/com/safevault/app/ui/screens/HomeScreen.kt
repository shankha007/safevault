package com.safevault.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import com.safevault.app.ui.components.BrandMark
import com.safevault.app.ui.components.brandBrush
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

    // Light status-bar icons while the dark brand header sits behind them.
    val activity = LocalActivity.current
    val view = LocalView.current
    DisposableEffect(Unit) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        val before = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = false
        onDispose { controller.isAppearanceLightStatusBars = before }
    }

    Scaffold(
        // The header draws behind the status bar itself, so the scaffold adds no insets.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddSheet = true },
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("Add") },
                modifier = Modifier.navigationBarsPadding(),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            VaultHeader(items, onGenerator = onGenerator, onLock = { repo.lock() }, onSettings = onSettings)
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 12.dp,
                    bottom = 96.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
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
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
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
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
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
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { onOpen(item.id) },
                        )
                    }
                }
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

/**
 * Branded top of the Home screen: logo, app name and a count of what's in the vault, on the
 * brand gradient. Fixed above the list so the name is always in view.
 */
@Composable
private fun VaultHeader(items: List<VaultItem>, onGenerator: () -> Unit, onLock: () -> Unit, onSettings: () -> Unit) {
    val onBrand = Color.White
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(brandBrush()),
    ) {
        Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(size = 44, onGradient = true)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "SafeVault",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = onBrand,
                        maxLines = 1,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Lock, null, tint = onBrand.copy(alpha = 0.75f), modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Encrypted · Offline",
                            style = MaterialTheme.typography.labelMedium,
                            color = onBrand.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onGenerator) { Icon(Icons.Outlined.Password, "Password generator", tint = onBrand) }
                IconButton(onClick = onLock) { Icon(Icons.Outlined.Lock, "Lock now", tint = onBrand) }
                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings", tint = onBrand) }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill(items.count { it.type == ItemType.LOGIN }, "Logins", Modifier.weight(1f))
                StatPill(items.count { it.type == ItemType.CARD }, "Cards", Modifier.weight(1f))
                StatPill(items.count { it.favorite }, "Favourites", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatPill(count: Int, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text("$count", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
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
