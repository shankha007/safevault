package com.safevault.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import com.safevault.app.SafeVaultApp
import com.safevault.app.crypto.BiometricKeys
import com.safevault.app.data.BackupFile
import com.safevault.app.data.MAX_FAILED_ATTEMPTS
import com.safevault.app.data.VaultRepository
import com.safevault.app.ui.AuthOutcome
import com.safevault.app.ui.LocalActivity
import com.safevault.app.ui.biometricPrompt
import com.safevault.app.ui.components.ConfirmDialog
import com.safevault.app.ui.components.PasswordField
import com.safevault.app.ui.components.PasswordPromptDialog
import com.safevault.app.ui.components.StrengthMeter
import com.safevault.app.util.PasswordStrength
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val autoLockChoices = listOf(0 to "Immediately", 30 to "After 30 seconds", 60 to "After 1 minute", 300 to "After 5 minutes")
private val clipboardChoices = listOf(15 to "15 seconds", 30 to "30 seconds", 60 to "1 minute", 120 to "2 minutes")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(repo: VaultRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as SafeVaultApp
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val meta by repo.meta.collectAsState()
    val settings = meta?.settings ?: return

    var picker by remember { mutableStateOf<String?>(null) } // "autolock" | "clipboard"
    var confirmWipeSetting by remember { mutableStateOf(false) }
    var changePassword by remember { mutableStateOf(false) }
    var deleteVault by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf<BackupFile?>(null) }
    val canBiometric = remember { BiometricKeys.isAvailable(activity) }

    fun toast(msg: String) = scope.launch { snackbar.showSnackbar(msg) }

    // Actions that weaken protection or let the vault leave the phone ask for the master
    // password even while unlocked, so someone holding the open phone can't do them (D-031).
    // Never the fingerprint: a finger the attacker enrolled must not be enough.
    var gate by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    fun withPassword(title: String, action: () -> Unit) {
        gate = title to action
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        app.autoLock.externalActivityDone()
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { repo.exportBackup(context.contentResolver.openOutputStream(uri, "wt")!!) }
                .onSuccess { toast("Backup saved. It opens with your current master password.") }
                .onFailure { toast("Couldn't save the backup.") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        app.autoLock.externalActivityDone()
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { repo.readBackup(context.contentResolver.openInputStream(uri)!!) }
            .onSuccess { importing = it }
            .onFailure { toast(it.message ?: "Couldn't read that file.") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Section("Security")
            if (canBiometric) {
                ListItem(
                    headlineContent = { Text("Unlock with fingerprint") },
                    supportingContent = { Text("Your master password still works too") },
                    leadingContent = { Icon(Icons.Outlined.Fingerprint, null) },
                    trailingContent = {
                        Switch(checked = meta?.biometricWrappedKey != null, onCheckedChange = { on ->
                            if (!on) {
                                repo.disableBiometric()
                            } else withPassword("Turn on fingerprint unlock") {
                                scope.launch {
                                    val r = activity.biometricPrompt("Turn on fingerprint unlock", cipher = repo.biometricEnrollCipher(), negativeText = "Cancel")
                                    if (r is AuthOutcome.Success) repo.enableBiometric(r.cipher!!)
                                }
                            }
                        })
                    },
                )
            }
            ListItem(
                headlineContent = { Text("Auto-lock") },
                supportingContent = { Text(autoLockChoices.first { it.first == settings.autoLockSeconds }.second + " after leaving the app") },
                leadingContent = { Icon(Icons.Outlined.Timer, null) },
                modifier = Modifier.clickable { picker = "autolock" },
            )
            ListItem(
                headlineContent = { Text("Clear copied passwords") },
                supportingContent = { Text("After " + clipboardChoices.first { it.first == settings.clipboardClearSeconds }.second) },
                leadingContent = { Icon(Icons.Outlined.ContentPaste, null) },
                modifier = Modifier.clickable { picker = "clipboard" },
            )
            ListItem(
                headlineContent = { Text("Confirm before showing PIN and CVV") },
                supportingContent = { Text("Ask for fingerprint or master password again") },
                leadingContent = { Icon(Icons.Outlined.Pin, null) },
                trailingContent = {
                    Switch(checked = settings.reauthForCardSecrets, onCheckedChange = { on ->
                        if (on) {
                            repo.updateSettings { it.copy(reauthForCardSecrets = true) }
                        } else withPassword("Stop asking before PIN and CVV") {
                            repo.updateSettings { it.copy(reauthForCardSecrets = false) }
                        }
                    })
                },
            )
            ListItem(
                headlineContent = { Text("Erase after $MAX_FAILED_ATTEMPTS wrong passwords") },
                supportingContent = { Text("Protects against guessing if your phone is stolen") },
                leadingContent = { Icon(Icons.Outlined.WarningAmber, null) },
                trailingContent = {
                    Switch(checked = settings.wipeAfterFailures, onCheckedChange = { on ->
                        if (on) {
                            confirmWipeSetting = true
                        } else withPassword("Turn off auto-erase") {
                            repo.updateSettings { it.copy(wipeAfterFailures = false) }
                        }
                    })
                },
            )
            ListItem(
                headlineContent = { Text("Change master password") },
                leadingContent = { Icon(Icons.Outlined.Key, null) },
                modifier = Modifier.clickable { changePassword = true },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("Backup")
            ListItem(
                headlineContent = { Text("Export encrypted backup") },
                supportingContent = { Text("Save a file you can keep on a PC or pen drive. It opens only with your master password.") },
                leadingContent = { Icon(Icons.Outlined.Upload, null) },
                modifier = Modifier.clickable {
                    withPassword("Export encrypted backup") {
                        app.autoLock.expectExternalActivity()
                        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        exportLauncher.launch("safevault-backup-$date.svault")
                    }
                },
            )
            ListItem(
                headlineContent = { Text("Import from backup") },
                supportingContent = { Text("Adds items from a backup file. Newer copies win.") },
                leadingContent = { Icon(Icons.Outlined.Download, null) },
                modifier = Modifier.clickable {
                    app.autoLock.expectExternalActivity()
                    importLauncher.launch(arrayOf("*/*"))
                },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("About")
            ListItem(
                headlineContent = { Text("No internet access") },
                supportingContent = { Text("SafeVault can't connect to the internet. Your data never leaves this phone unless you export a backup.") },
                leadingContent = { Icon(Icons.Outlined.WifiOff, null) },
            )
            ListItem(
                headlineContent = { Text("Delete vault", color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text("Erase everything from this phone") },
                leadingContent = { Icon(Icons.Outlined.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable { deleteVault = true },
            )
            val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() }
            Text(
                "SafeVault $version",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    when (picker) {
        "autolock" -> ChoiceDialog("Auto-lock", autoLockChoices, settings.autoLockSeconds,
            onPick = { v ->
                picker = null
                val apply = { repo.updateSettings { it.copy(autoLockSeconds = v) } }
                // Locking sooner is always fine; staying open longer needs the password.
                if (v <= settings.autoLockSeconds) apply() else withPassword("Lock later") { apply() }
            },
            onDismiss = { picker = null })
        "clipboard" -> ChoiceDialog("Clear copied passwords after", clipboardChoices, settings.clipboardClearSeconds,
            onPick = { v -> repo.updateSettings { it.copy(clipboardClearSeconds = v) }; picker = null },
            onDismiss = { picker = null })
    }

    if (confirmWipeSetting) {
        ConfirmDialog(
            title = "Turn on auto-erase?",
            message = "After $MAX_FAILED_ATTEMPTS wrong master passwords in a row, the vault is permanently erased from this phone. " +
                "Keep a recent backup so you can restore it.",
            confirmText = "Turn on",
            destructive = true,
            onConfirm = { repo.updateSettings { it.copy(wipeAfterFailures = true) }; confirmWipeSetting = false },
            onDismiss = { confirmWipeSetting = false },
        )
    }

    gate?.let { (title, action) ->
        PasswordPromptDialog(
            title = title,
            message = "Enter your master password to continue.",
            confirmText = "Continue",
            onSubmit = { pw ->
                val ok = repo.verifyPassword(pw)
                pw.fill(' ')
                if (ok) { gate = null; action(); null } else "That password isn't right."
            },
            onDismiss = { gate = null },
        )
    }

    if (changePassword) ChangePasswordDialog(repo, onDone = { changed ->
        changePassword = false
        if (changed) {
            toast(
                "Master password changed. Fingerprint unlock is off; turn it on again if you use it. " +
                    "Export a new backup: old ones open only with the old password.",
            )
        }
    })

    importing?.let { b ->
        PasswordPromptDialog(
            title = "Import backup",
            message = "Enter the master password that was in use when this backup was made.",
            confirmText = "Import",
            onSubmit = { pw ->
                val result = repo.importBackup(b, pw)
                if (result == null) {
                    "That password doesn't open this backup."
                } else {
                    importing = null
                    toast("Imported: ${result.added} new, ${result.updated} updated.")
                    null
                }
            },
            onDismiss = { importing = null },
        )
    }

    if (deleteVault) {
        PasswordPromptDialog(
            title = "Delete vault?",
            message = "Everything in SafeVault on this phone will be erased. This can't be undone. Enter your master password to confirm.",
            confirmText = "Delete everything",
            onSubmit = { pw ->
                if (repo.verifyPassword(pw)) { deleteVault = false; repo.deleteVault(); null } else "That password isn't right."
            },
            onDismiss = { deleteVault = false },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ChoiceDialog(title: String, choices: List<Pair<Int, String>>, current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                choices.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(value) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == current, onClick = { onPick(value) })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ChangePasswordDialog(repo: VaultRepository, onDone: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val problem = when {
        new.isEmpty() -> null
        else -> PasswordStrength.masterPasswordProblem(new)
    } ?: when {
        confirm.isNotEmpty() && confirm != new -> "The new passwords don't match."
        else -> null
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDone(false) },
        title = { Text("Change master password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField(current, { current = it; error = null }, "Current password", isError = error != null, supportingText = error)
                PasswordField(new, { new = it }, "New password")
                StrengthMeter(new)
                PasswordField(confirm, { confirm = it }, "Confirm new password", isError = confirm.isNotEmpty() && confirm != new)
                problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && current.isNotEmpty() && new.isNotEmpty() && confirm == new && problem == null,
                onClick = {
                    busy = true
                    scope.launch {
                        val ok = repo.changePassword(current.toCharArray(), new.toCharArray())
                        busy = false
                        if (ok) onDone(true) else error = "That isn't your current password."
                    }
                },
            ) { Text("Change") }
        },
        dismissButton = { TextButton(onClick = { onDone(false) }, enabled = !busy) { Text("Cancel") } },
    )
}
