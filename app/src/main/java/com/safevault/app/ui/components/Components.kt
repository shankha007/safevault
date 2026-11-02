package com.safevault.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import com.safevault.app.R
import com.safevault.app.data.ItemType
import com.safevault.app.data.VaultItem
import com.safevault.app.ui.theme.BrandColors
import com.safevault.app.ui.theme.StatusColors
import com.safevault.app.util.Brand
import com.safevault.app.util.BrandMatcher
import com.safevault.app.util.PasswordStrength
import com.safevault.app.util.Strength
import kotlinx.coroutines.launch

/** Secret input: masked by default, keyboard told not to autocorrect or learn it. */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Password,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    isError: Boolean = false,
    supportingText: String? = null,
    focusRequester: FocusRequester? = null,
    trailingExtra: @Composable (() -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (keyboardType == KeyboardType.Number) KeyboardType.NumberPassword else keyboardType,
            autoCorrectEnabled = false,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        textStyle = if (visible) MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyLarge,
        trailingIcon = {
            Row {
                trailingExtra?.invoke()
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (visible) "Hide" else "Show",
                    )
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
    )
}

fun strengthColor(s: Strength): Color = when (s) {
    Strength.VERY_WEAK, Strength.WEAK -> StatusColors.weak
    Strength.FAIR -> StatusColors.fair
    Strength.STRONG, Strength.VERY_STRONG -> StatusColors.strong
}

@Composable
fun StrengthMeter(password: String, modifier: Modifier = Modifier) {
    if (password.isEmpty()) return
    val strength = PasswordStrength.evaluate(password)
    val progress by animateFloatAsState((strength.score + 1) / 5f, label = "strength")
    Column(modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { progress },
            color = strengthColor(strength),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            strokeCap = StrokeCap.Round,
            modifier = Modifier.fillMaxWidth().height(6.dp),
        )
        Text(
            strength.label,
            style = MaterialTheme.typography.labelMedium,
            color = strengthColor(strength),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private val avatarPalette = listOf(
    Color(0xFF1E4FD8), Color(0xFFD9483B), Color(0xFF0F7A57), Color(0xFF7B4FD6),
    Color(0xFFC2620E), Color(0xFF0E7C93), Color(0xFFB0306A), Color(0xFF55606E),
)

/** The brand behind an item, if the bundled catalogue knows it (D-028). */
fun brandOf(item: VaultItem): Brand? = when (item.type) {
    ItemType.LOGIN -> BrandMatcher.forLogin(item.name, item.login?.url ?: "")
    ItemType.CARD -> BrandMatcher.forCard(item.name, item.card?.bank ?: "", item.card?.network ?: "")
}

/** Logos are parsed once per brand and reused. */
private val brandVectors = HashMap<String, ImageVector>()

private fun brandVector(brand: Brand): ImageVector = brandVectors.getOrPut(brand.slug) {
    ImageVector.Builder(brand.slug, 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes(brand.path), fill = SolidColor(Color.White))
        .build()
}

@Composable
fun ItemAvatar(item: VaultItem, size: Int = 40) {
    val brand = remember(item.name, item.login?.url, item.card?.bank, item.card?.network) { brandOf(item) }
    if (brand != null) {
        BrandAvatar(brand, item, size)
        return
    }
    val color = avatarPalette[(item.name.lowercase().hashCode() and 0x7fffffff) % avatarPalette.size]
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 4).dp))
            .background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        if (item.type == ItemType.CARD) {
            Icon(Icons.Outlined.CreditCard, contentDescription = null, tint = color, modifier = Modifier.size((size / 2).dp))
        } else {
            Text(
                item.name.trim().take(1).uppercase().ifEmpty { "?" },
                color = color,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size * 0.42).sp,
            )
        }
    }
}

/**
 * Brand tile, styled like an app icon: the brand colour with the logo in white, or in black
 * for very light brand colours (Snapchat yellow). Brands without a free logo show their
 * first letter instead.
 */
@Composable
private fun BrandAvatar(brand: Brand, item: VaultItem, size: Int) {
    val bg = Color(brand.color)
    val fg = if (bg.luminance() > 0.6f) Color.Black else Color.White
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 4).dp))
            .background(bg)
            // Keeps black tiles (X, GitHub, Apple) visible on the dark theme's surface.
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape((size / 4).dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (brand.path != null) {
            Icon(brandVector(brand), contentDescription = brand.title, tint = fg, modifier = Modifier.size((size * 0.55).dp))
        } else {
            Text(
                brand.title.take(1),
                color = fg,
                fontWeight = FontWeight.Bold,
                fontSize = (size * 0.45).sp,
            )
        }
    }
}

/** A read-only labelled value in a rounded box, with optional reveal and copy buttons. */
@Composable
fun FieldRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    masked: Boolean = false,
    monospace: Boolean = false,
    onToggleReveal: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailingNote: String? = null,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (masked) "••••••••" else value,
                    style = MaterialTheme.typography.bodyLarge.let {
                        if (monospace && !masked) it.copy(fontFamily = FontFamily.Monospace) else it
                    },
                    maxLines = if (label == "Notes") Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (trailingNote != null) {
                Text(trailingNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
            }
            if (onToggleReveal != null) {
                IconButton(onClick = onToggleReveal) {
                    Icon(
                        if (masked) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        contentDescription = if (masked) "Show $label" else "Hide $label",
                    )
                }
            }
            if (onCopy != null) {
                IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy $label") }
            }
            if (onToggleReveal == null && onCopy == null) Spacer(Modifier.width(12.dp))
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Asks for a password. [onSubmit] returns an error message to show, or null to close.
 * The work runs while a progress bar shows, because Argon2 takes about a second.
 */
@Composable
fun PasswordPromptDialog(
    title: String,
    message: String?,
    confirmText: String,
    onSubmit: suspend (CharArray) -> String?,
    onDismiss: () -> Unit,
) {
    var pw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val submit = {
        if (pw.isNotEmpty() && !busy) {
            busy = true
            scope.launch {
                error = onSubmit(pw.toCharArray())
                busy = false
            }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (message != null) Text(message)
                PasswordField(
                    value = pw,
                    onValueChange = { pw = it; error = null },
                    label = "Master password",
                    isError = error != null,
                    supportingText = error,
                    onImeAction = { submit() },
                )
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { submit() }, enabled = !busy) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

/** The SafeVault brand gradient for the current light/dark mode. */
@Composable
fun brandBrush(): Brush =
    Brush.linearGradient(if (isSystemInDarkTheme()) BrandColors.gradientDark else BrandColors.gradientLight)

/**
 * The app's logo: the launcher icon's shield-and-keyhole on the brand gradient.
 * [onGradient] draws a frosted tile instead, for use on top of the gradient itself.
 */
@Composable
fun BrandMark(size: Int = 48, onGradient: Boolean = false) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.3).dp))
            .let { if (onGradient) it.background(Color.White.copy(alpha = 0.16f)) else it.background(brandBrush()) },
        contentAlignment = Alignment.Center,
    ) {
        // The vector is a 108dp adaptive-icon foreground; scaling it up a little fills the tile.
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.requiredSize((size * 1.35).dp))
    }
}
