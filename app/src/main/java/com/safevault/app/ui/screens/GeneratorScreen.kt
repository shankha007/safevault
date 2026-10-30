package com.safevault.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safevault.app.SafeVaultApp
import com.safevault.app.data.VaultRepository
import com.safevault.app.ui.components.StrengthMeter
import com.safevault.app.util.GeneratorOptions
import com.safevault.app.util.PasswordGenerator
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Password generator. When [onUse] is set (opened from the editor) it offers "Use password". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen(repo: VaultRepository, onBack: () -> Unit, onUse: ((String) -> Unit)?) {
    val app = LocalContext.current.applicationContext as SafeVaultApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var options by remember { mutableStateOf(GeneratorOptions()) }
    var password by remember { mutableStateOf(PasswordGenerator.generate(options)) }
    LaunchedEffect(options) { password = PasswordGenerator.generate(options) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Password generator") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(password, fontFamily = FontFamily.Monospace, fontSize = 20.sp, lineHeight = 28.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { password = PasswordGenerator.generate(options) }) { Icon(Icons.Outlined.Refresh, "Generate another") }
                        IconButton(onClick = {
                            app.clipboard.copy("Password", password, repo.settings.clipboardClearSeconds)
                            scope.launch { snackbar.showSnackbar("Copied. Clipboard clears in ${repo.settings.clipboardClearSeconds} s.") }
                        }) { Icon(Icons.Outlined.ContentCopy, "Copy") }
                    }
                }
            }
            StrengthMeter(password)

            Text("Length: ${options.length}", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = options.length.toFloat(),
                onValueChange = { options = options.copy(length = it.roundToInt()) },
                valueRange = 8f..64f,
                steps = 64 - 8 - 1,
            )
            OptionSwitch("Capital letters (A-Z)", options.upper) { options = options.copy(upper = it) }
            OptionSwitch("Small letters (a-z)", options.lower) { options = options.copy(lower = it) }
            OptionSwitch("Numbers (0-9)", options.digits) { options = options.copy(digits = it) }
            OptionSwitch("Symbols (!@#$…)", options.symbols) { options = options.copy(symbols = it) }
            OptionSwitch("Avoid look-alike characters (l, 1, O, 0)", options.avoidAmbiguous) { options = options.copy(avoidAmbiguous = it) }

            if (onUse != null) {
                Button(onClick = { onUse(password) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Use this password") }
            } else {
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            }
        }
    }
}

@Composable
private fun OptionSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
