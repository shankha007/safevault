package com.safevault.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safevault.app.SafeVaultApp
import com.safevault.app.data.CardKind
import com.safevault.app.data.Field
import com.safevault.app.data.ItemType
import com.safevault.app.data.VaultItem
import com.safevault.app.data.VaultRepository
import com.safevault.app.ui.LocalActivity
import com.safevault.app.ui.ReauthController
import com.safevault.app.ui.components.ConfirmDialog
import com.safevault.app.ui.components.FieldRow
import com.safevault.app.ui.components.ItemAvatar
import com.safevault.app.ui.components.strengthColor
import com.safevault.app.util.CardUtils
import com.safevault.app.util.PasswordStrength
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private const val AUTO_HIDE_MS = 30_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    repo: VaultRepository,
    reauth: ReauthController,
    item: VaultItem,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as SafeVaultApp
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }

    // Decrypted values exist only in this map, only while shown, and are dropped after 30 s.
    val revealed = remember(item) { mutableStateMapOf<String, String>() }
    LaunchedEffect(revealed.size) {
        if (revealed.isNotEmpty()) {
            delay(AUTO_HIDE_MS)
            revealed.clear()
        }
    }

    val needsReauth = setOf(Field.CVV, Field.PIN)
    fun labelOf(field: String) = when (field) {
        Field.PASSWORD -> "password"
        Field.CARD_NUMBER -> "card number"
        Field.CVV -> "CVV"
        Field.PIN -> "PIN"
        else -> "notes"
    }

    fun sealedOf(field: String) = when (field) {
        Field.PASSWORD -> item.login?.password
        Field.CARD_NUMBER -> item.card?.number
        Field.CVV -> item.card?.cvv
        Field.PIN -> item.card?.pin
        else -> item.notes
    }

    fun withAuth(field: String, action: () -> Unit) {
        if (field !in needsReauth) return action()
        scope.launch { if (reauth.confirm(activity, labelOf(field))) action() }
    }

    fun toggle(field: String) {
        if (revealed.containsKey(field)) revealed.remove(field)
        else withAuth(field) { revealed[field] = repo.reveal(sealedOf(field), item.id, field) }
    }

    fun copySecret(field: String) = withAuth(field) {
        val value = repo.reveal(sealedOf(field), item.id, field)
        app.clipboard.copy(labelOf(field), value, repo.settings.clipboardClearSeconds)
        scope.launch { snackbar.showSnackbar("Copied. Clipboard clears in ${repo.settings.clipboardClearSeconds} s.") }
    }

    fun copyPlain(label: String, value: String) {
        app.clipboard.copy(label, value, repo.settings.clipboardClearSeconds)
        scope.launch { snackbar.showSnackbar("$label copied") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item.name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { scope.launch { repo.toggleFavorite(item.id) } }) {
                        if (item.favorite) Icon(Icons.Rounded.Star, "Remove from favourites", tint = MaterialTheme.colorScheme.primary)
                        else Icon(Icons.Outlined.StarOutline, "Add to favourites")
                    }
                    IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Edit") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (item.type) {
                ItemType.LOGIN -> {
                    val login = item.login ?: return@Column
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                        ItemAvatar(item, size = 56)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(item.name, style = MaterialTheme.typography.titleLarge)
                            Text("Login", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (login.username.isNotEmpty()) {
                        FieldRow("Username", login.username, onCopy = { copyPlain("Username", login.username) })
                    }
                    if (login.password != null) {
                        val shown = revealed[Field.PASSWORD]
                        FieldRow(
                            "Password", shown ?: "", masked = shown == null, monospace = true,
                            onToggleReveal = { toggle(Field.PASSWORD) },
                            onCopy = { copySecret(Field.PASSWORD) },
                        )
                        if (shown != null) {
                            val s = PasswordStrength.evaluate(shown)
                            Text("Strength: ${s.label}", color = strengthColor(s), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                    if (login.url.isNotEmpty()) FieldRow("Website", login.url, onCopy = { copyPlain("Website", login.url) })
                }

                ItemType.CARD -> {
                    val card = item.card ?: return@Column
                    CardFace(item, revealed[Field.CARD_NUMBER])
                    if (card.number != null) {
                        val shown = revealed[Field.CARD_NUMBER]
                        FieldRow(
                            "Card number", shown?.let { CardUtils.formatNumber(it) } ?: "", masked = shown == null, monospace = true,
                            onToggleReveal = { toggle(Field.CARD_NUMBER) },
                            onCopy = { copySecret(Field.CARD_NUMBER) },
                        )
                    }
                    if (card.holderName.isNotEmpty()) FieldRow("Name on card", card.holderName, onCopy = { copyPlain("Name", card.holderName) })
                    if (card.expiry.isNotEmpty()) FieldRow("Expiry", card.expiry, onCopy = { copyPlain("Expiry", card.expiry) })
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (card.cvv != null) {
                            val shown = revealed[Field.CVV]
                            FieldRow("CVV", shown ?: "", masked = shown == null, monospace = true,
                                onToggleReveal = { toggle(Field.CVV) }, modifier = Modifier.weight(1f))
                        }
                        if (card.pin != null) {
                            val shown = revealed[Field.PIN]
                            FieldRow("PIN", shown ?: "", masked = shown == null, monospace = true,
                                onToggleReveal = { toggle(Field.PIN) }, modifier = Modifier.weight(1f))
                        }
                    }
                    if ((card.cvv != null || card.pin != null) && repo.settings.reauthForCardSecrets) {
                        Text(
                            "PIN and CVV ask for your fingerprint or master password first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }

            if (item.notes != null) {
                val shown = revealed[Field.NOTES]
                FieldRow("Notes", shown ?: "", masked = shown == null, onToggleReveal = { toggle(Field.NOTES) })
            }

            Spacer(Modifier.height(8.dp))
            val df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            Text(
                "Last changed ${df.format(Date(item.updatedAt))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete ${item.name}?",
            message = "This removes it from your vault. Backups you've already exported still contain it.",
            confirmText = "Delete",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                scope.launch { repo.delete(item.id); onBack() }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun CardFace(item: VaultItem, revealedNumber: String?) {
    val card = item.card ?: return
    val bg = if (card.kind == CardKind.CREDIT) Color(0xFF26215C) else Color(0xFF1E4FD8)
    Surface(
        color = bg,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp).padding(bottom = 6.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row {
                Text(card.bank.ifEmpty { item.name }, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(card.network.ifEmpty { if (card.kind == CardKind.CREDIT) "Credit" else "Debit" }, color = Color.White.copy(alpha = 0.85f))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                revealedNumber?.let { CardUtils.formatNumber(it) } ?: "•••• •••• •••• ${card.last4.ifEmpty { "••••" }}",
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                letterSpacing = 1.sp,
            )
            Row {
                Text(card.holderName.uppercase(), color = Color.White.copy(alpha = 0.9f), modifier = Modifier.weight(1f))
                Text(card.expiry, color = Color.White.copy(alpha = 0.9f))
            }
        }
    }
}
