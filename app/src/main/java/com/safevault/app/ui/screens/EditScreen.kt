package com.safevault.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.safevault.app.data.CardData
import com.safevault.app.data.CardKind
import com.safevault.app.data.Field
import com.safevault.app.data.ItemType
import com.safevault.app.data.LoginData
import com.safevault.app.data.VaultItem
import com.safevault.app.data.VaultRepository
import com.safevault.app.ui.components.ConfirmDialog
import com.safevault.app.ui.components.PasswordField
import com.safevault.app.ui.components.StrengthMeter
import com.safevault.app.util.CardUtils
import kotlinx.coroutines.launch

/**
 * Editable copy of an item. Secrets are decrypted into these plain fields only while the
 * editor is open; on save they are sealed again before anything is written.
 * Kept in `remember` (not rememberSaveable) on purpose: saved state can be written to disk.
 */
private class Draft(repo: VaultRepository, existing: VaultItem?, type: ItemType, val id: String) {
    var name by mutableStateOf(existing?.name ?: "")
    var favorite by mutableStateOf(existing?.favorite ?: false)
    var notes by mutableStateOf(repo.reveal(existing?.notes, id, Field.NOTES))
    // login
    var username by mutableStateOf(existing?.login?.username ?: "")
    var password by mutableStateOf(repo.reveal(existing?.login?.password, id, Field.PASSWORD))
    var url by mutableStateOf(existing?.login?.url ?: "")
    // card
    var bank by mutableStateOf(existing?.card?.bank ?: "")
    var holder by mutableStateOf(existing?.card?.holderName ?: "")
    var number by mutableStateOf(repo.reveal(existing?.card?.number, id, Field.CARD_NUMBER))
    var kind by mutableStateOf(existing?.card?.kind ?: CardKind.DEBIT)
    // Digits only ("1227"); the slash is drawn by ExpiryTransformation and added back on save.
    var expiry by mutableStateOf(CardUtils.expiryToDigits(existing?.card?.expiry ?: ""))
    var cvv by mutableStateOf(repo.reveal(existing?.card?.cvv, id, Field.CVV))
    var pin by mutableStateOf(repo.reveal(existing?.card?.pin, id, Field.PIN))

    val type = existing?.type ?: type
    private val initial = snapshot()

    private fun snapshot() = listOf(name, favorite, notes, username, password, url, bank, holder, number, kind, expiry, cvv, pin).hashCode()
    val changed get() = snapshot() != initial

    fun clear() {
        password = ""; number = ""; cvv = ""; pin = ""; notes = ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    repo: VaultRepository,
    existing: VaultItem?,
    newType: ItemType,
    onDone: (savedId: String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val draft = remember(existing?.id) { Draft(repo, existing, newType, existing?.id ?: repo.newItemId()) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    // The generator is drawn on top of the editor (not a separate screen) so the draft survives.
    var showGenerator by remember { mutableStateOf(false) }

    if (showGenerator) {
        BackHandler { showGenerator = false }
        GeneratorScreen(
            repo = repo,
            onBack = { showGenerator = false },
            onUse = { pw -> draft.password = pw; showGenerator = false },
        )
        return
    }

    val cardNumberDigits = draft.number.filter { it.isDigit() }
    val numberWarning = cardNumberDigits.isNotEmpty() && cardNumberDigits.length >= 12 && !CardUtils.luhnValid(cardNumberDigits)
    val expiryText = CardUtils.expiryFromDigits(draft.expiry)
    val expiryWarning = draft.expiry.isNotEmpty() && !CardUtils.expiryValid(expiryText)
    val canSave = draft.name.isNotBlank() && !saving

    fun close() {
        if (draft.changed) {
            confirmDiscard = true
        } else {
            draft.clear()
            onDone(null)
        }
    }
    BackHandler { close() }

    fun save() {
        saving = true
        val now = System.currentTimeMillis()
        val id = draft.id
        val item = VaultItem(
            id = id,
            type = draft.type,
            name = draft.name.trim(),
            favorite = draft.favorite,
            notes = repo.seal(draft.notes.trim(), id, Field.NOTES),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            login = if (draft.type == ItemType.LOGIN) LoginData(
                username = draft.username.trim(),
                password = repo.seal(draft.password, id, Field.PASSWORD),
                url = draft.url.trim(),
            ) else null,
            card = if (draft.type == ItemType.CARD) CardData(
                bank = draft.bank.trim(),
                holderName = draft.holder.trim(),
                number = repo.seal(cardNumberDigits, id, Field.CARD_NUMBER),
                last4 = cardNumberDigits.takeLast(4),
                network = CardUtils.detectNetwork(cardNumberDigits),
                kind = draft.kind,
                expiry = expiryText,
                cvv = repo.seal(draft.cvv, id, Field.CVV),
                pin = repo.seal(draft.pin, id, Field.PIN),
            ) else null,
        )
        scope.launch {
            repo.upsert(item)
            draft.clear()
            onDone(id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            existing != null -> "Edit ${existing.name}"
                            draft.type == ItemType.CARD -> "New card"
                            else -> "New login"
                        },
                    )
                },
                navigationIcon = { IconButton(onClick = { close() }) { Icon(Icons.Outlined.Close, "Close") } },
                actions = { TextButton(onClick = { save() }, enabled = canSave) { Text("Save") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val textOpts = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.Sentences)
            OutlinedTextField(
                value = draft.name, onValueChange = { draft.name = it },
                label = { Text(if (draft.type == ItemType.CARD) "Name (for example, HDFC debit card)" else "Name (for example, Gmail)") },
                singleLine = true, keyboardOptions = textOpts, modifier = Modifier.fillMaxWidth(),
            )

            if (draft.type == ItemType.LOGIN) {
                OutlinedTextField(
                    value = draft.username, onValueChange = { draft.username = it },
                    label = { Text("Username or email") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                PasswordField(
                    value = draft.password, onValueChange = { draft.password = it }, label = "Password",
                    trailingExtra = {
                        IconButton(onClick = { showGenerator = true }) { Icon(Icons.Outlined.AutoAwesome, "Generate a password") }
                    },
                )
                StrengthMeter(draft.password)
                OutlinedTextField(
                    value = draft.url, onValueChange = { draft.url = it },
                    label = { Text("Website") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    CardKind.entries.forEachIndexed { i, k ->
                        SegmentedButton(
                            selected = draft.kind == k,
                            onClick = { draft.kind = k },
                            shape = SegmentedButtonDefaults.itemShape(i, CardKind.entries.size),
                        ) { Text(if (k == CardKind.DEBIT) "Debit" else "Credit") }
                    }
                }
                OutlinedTextField(
                    value = draft.bank, onValueChange = { draft.bank = it },
                    label = { Text("Bank") }, singleLine = true, keyboardOptions = textOpts, modifier = Modifier.fillMaxWidth(),
                )
                PasswordField(
                    value = draft.number,
                    onValueChange = { v -> draft.number = v.filter { it.isDigit() }.take(19) },
                    label = "Card number",
                    keyboardType = KeyboardType.Number,
                    isError = numberWarning,
                    supportingText = when {
                        numberWarning -> "This number doesn't look right. Check for typos."
                        cardNumberDigits.isNotEmpty() -> CardUtils.detectNetwork(cardNumberDigits).ifEmpty { null }
                        else -> null
                    },
                )
                OutlinedTextField(
                    value = draft.holder, onValueChange = { draft.holder = it },
                    label = { Text("Name on card") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = draft.expiry,
                        onValueChange = { v -> draft.expiry = CardUtils.sanitizeExpiryDigits(v) },
                        label = { Text("Expiry (MM/YY)") }, singleLine = true, isError = expiryWarning,
                        visualTransformation = ExpiryTransformation,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, autoCorrectEnabled = false),
                        modifier = Modifier.weight(1f),
                    )
                    PasswordField(
                        value = draft.cvv, onValueChange = { v -> draft.cvv = v.filter { it.isDigit() }.take(4) },
                        label = "CVV", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f),
                    )
                }
                PasswordField(
                    value = draft.pin, onValueChange = { v -> draft.pin = v.filter { it.isDigit() }.take(6) },
                    label = "ATM / card PIN", keyboardType = KeyboardType.Number,
                )
            }

            OutlinedTextField(
                value = draft.notes, onValueChange = { draft.notes = it },
                label = { Text("Notes (encrypted)") }, minLines = 3,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Favourite", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = draft.favorite, onCheckedChange = { draft.favorite = it })
            }
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard changes?",
            message = "What you've typed won't be saved.",
            confirmText = "Discard",
            destructive = true,
            onConfirm = { confirmDiscard = false; draft.clear(); onDone(null) },
            onDismiss = { confirmDiscard = false },
        )
    }
}

/**
 * Shows the stored digits "MMYY" as "MM/YY". The slash exists only on screen, so the cursor
 * always moves with the digits. (Writing the slash into the value itself left the cursor
 * before it, and the year digits came out reversed: typing 1 2 2 7 gave "12/72".)
 */
private object ExpiryTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val d = text.text
        if (d.length < 2) return TransformedText(text, OffsetMapping.Identity)
        val shown = d.take(2) + "/" + d.drop(2)
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = if (offset < 2) offset else offset + 1
            override fun transformedToOriginal(offset: Int) = if (offset <= 2) offset else minOf(offset - 1, d.length)
        }
        return TransformedText(AnnotatedString(shown), mapping)
    }
}
