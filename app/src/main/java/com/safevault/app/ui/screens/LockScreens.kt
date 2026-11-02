package com.safevault.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.safevault.app.SafeVaultApp
import com.safevault.app.data.BackupFile
import com.safevault.app.data.MAX_FAILED_ATTEMPTS
import com.safevault.app.data.UnlockResult
import com.safevault.app.data.VaultRepository
import com.safevault.app.security.DeviceIntegrity
import com.safevault.app.ui.components.BrandMark
import com.safevault.app.ui.AuthOutcome
import com.safevault.app.ui.LocalActivity
import com.safevault.app.ui.biometricPrompt
import com.safevault.app.ui.components.PasswordField
import com.safevault.app.ui.components.PasswordPromptDialog
import com.safevault.app.ui.components.StrengthMeter
import com.safevault.app.util.PasswordStrength
import com.safevault.app.util.Strength
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun LockScaffold(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
    }
}

/** Logo and app name, so the first and most-seen screens carry the SafeVault brand. */
@Composable
private fun ShieldBadge() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BrandMark(size = 80)
        Text(
            "SafeVault",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun RootWarning() {
    if (!remember { DeviceIntegrity.looksRooted() }) return
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WarningAmber, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.size(8.dp))
            Text(
                "This phone appears to be rooted. Other apps with root access could read your vault while it's open.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------
// First run: create a master password, or restore a backup.
// ---------------------------------------------------------------------------------------

@Composable
fun SetupScreen(repo: VaultRepository) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val app = context.applicationContext as SafeVaultApp
    var pw by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var understood by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var backup by remember { mutableStateOf<BackupFile?>(null) }
    val confirmFocus = remember { FocusRequester() }

    val strength = PasswordStrength.evaluate(pw)
    val problem = when {
        pw.length < 8 -> "Use at least 8 characters."
        strength.score < Strength.FAIR.score -> "This password is too easy to guess. Try a longer phrase."
        confirm.isNotEmpty() && confirm != pw -> "The passwords don't match."
        else -> null
    }
    val canCreate = problem == null && confirm == pw && understood && !busy

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        app.autoLock.externalActivityDone()
        if (uri == null) return@rememberLauncherForActivityResult
        error = try {
            backup = context.contentResolver.openInputStream(uri)!!.let { repo.readBackup(it) }
            null
        } catch (e: Exception) {
            e.message ?: "Couldn't read that file."
        }
    }

    LockScaffold {
        ShieldBadge()
        Text("Create your vault", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Everything is stored only on this phone, encrypted with your master password. " +
                "The app has no internet access.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PasswordField(
            value = pw,
            onValueChange = { pw = it; error = null },
            label = "Master password",
            imeAction = ImeAction.Next,
            onImeAction = { confirmFocus.requestFocus() },
        )
        StrengthMeter(pw)
        PasswordField(
            value = confirm,
            onValueChange = { confirm = it; error = null },
            label = "Confirm master password",
            focusRequester = confirmFocus,
            isError = confirm.isNotEmpty() && confirm != pw,
        )
        if (pw.isNotEmpty() && problem != null) {
            Text(problem, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = understood, role = Role.Checkbox, onValueChange = { understood = it }),
        ) {
            Checkbox(checked = understood, onCheckedChange = null)
            Text(
                "I understand that if I forget this password, nobody can recover my vault.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                busy = true
                scope.launch {
                    repo.createVault(pw.toCharArray())
                    pw = ""; confirm = ""
                }
            },
            enabled = canCreate,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Create vault")
        }
        TextButton(onClick = {
            app.autoLock.expectExternalActivity()
            picker.launch(arrayOf("*/*"))
        }) { Text("Restore from a backup file") }
    }

    backup?.let { b ->
        PasswordPromptDialog(
            title = "Restore backup",
            message = "Enter the master password that was in use when this backup was made.",
            confirmText = "Restore",
            onSubmit = { p ->
                if (repo.restoreAsNewVault(b, p)) { backup = null; null } else "That password doesn't open this backup."
            },
            onDismiss = { backup = null },
        )
    }
}

// ---------------------------------------------------------------------------------------
// Unlock
// ---------------------------------------------------------------------------------------

@Composable
fun UnlockScreen(repo: VaultRepository) {
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val meta = repo.meta.value
    var pw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var lockedUntil by remember { mutableLongStateOf(meta?.lockoutUntil ?: 0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val biometricOn = meta?.biometricWrappedKey != null

    LaunchedEffect(lockedUntil) {
        while (lockedUntil > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(1000)
        }
        now = System.currentTimeMillis()
    }
    val lockedOut = lockedUntil > now

    fun tryBiometric() {
        scope.launch {
            val cipher = repo.biometricUnlockCipher()
            if (cipher == null) {
                message = "Fingerprint unlock was turned off because the fingerprints on this phone changed. Use your master password."
                return@launch
            }
            when (val r = activity.biometricPrompt(title = "Unlock SafeVault", cipher = cipher)) {
                is AuthOutcome.Success -> {
                    busy = true
                    runCatching { repo.unlockWithBiometric(r.cipher!!) }
                        .onFailure { message = "Fingerprint unlock didn't work. Use your master password." }
                    busy = false
                }
                is AuthOutcome.Failed -> message = r.message
                else -> Unit
            }
        }
    }

    fun tryPassword() {
        if (pw.isEmpty() || busy || lockedOut) return
        busy = true
        scope.launch {
            val chars = pw.toCharArray()
            when (val r = repo.unlockWithPassword(chars)) {
                UnlockResult.Success -> pw = ""
                is UnlockResult.WrongPassword -> {
                    message = if (r.attemptsLeftBeforeWipe != null) {
                        "Wrong password. ${r.attemptsLeftBeforeWipe} attempts left before the vault is erased."
                    } else {
                        "Wrong password."
                    }
                    pw = ""
                }
                is UnlockResult.LockedOut -> {
                    lockedUntil = r.untilMillis
                    message = "Too many wrong attempts."
                    pw = ""
                }
                UnlockResult.Wiped -> Unit
            }
            chars.fill(' ')
            busy = false
        }
    }

    LaunchedEffect(Unit) { if (biometricOn && !lockedOut) tryBiometric() }

    LockScaffold {
        Spacer(Modifier.height(24.dp))
        ShieldBadge()
        Text("Vault locked", style = MaterialTheme.typography.headlineSmall)
        RootWarning()
        if (biometricOn) {
            FilledTonalIconButton(onClick = { tryBiometric() }, modifier = Modifier.size(72.dp), enabled = !lockedOut) {
                Icon(Icons.Outlined.Fingerprint, contentDescription = "Unlock with fingerprint", modifier = Modifier.size(40.dp))
            }
            Text("Touch to unlock, or use your master password", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PasswordField(
            value = pw,
            onValueChange = { pw = it; message = null },
            label = "Master password",
            onImeAction = { tryPassword() },
            isError = message != null && !lockedOut,
        )
        if (lockedOut) {
            val secs = ((lockedUntil - now) / 1000).coerceAtLeast(1)
            Text(
                "Too many wrong attempts. Try again in ${if (secs >= 60) "${secs / 60} min ${secs % 60} s" else "$secs s"}.",
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        } else {
            message?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
        }
        Button(
            onClick = { tryPassword() },
            enabled = pw.isNotEmpty() && !busy && !lockedOut,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Unlock")
        }
        if (meta?.settings?.wipeAfterFailures == true) {
            Text(
                "The vault is erased after $MAX_FAILED_ATTEMPTS wrong passwords in a row.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
