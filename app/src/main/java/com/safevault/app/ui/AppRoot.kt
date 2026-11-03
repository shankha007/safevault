package com.safevault.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.safevault.app.data.ItemType
import com.safevault.app.data.VaultRepository
import com.safevault.app.data.VaultState
import com.safevault.app.ui.screens.DetailScreen
import com.safevault.app.ui.screens.EditScreen
import com.safevault.app.ui.screens.GeneratorScreen
import com.safevault.app.ui.screens.HomeScreen
import com.safevault.app.ui.screens.SettingsScreen
import com.safevault.app.ui.screens.SetupScreen
import com.safevault.app.ui.screens.UnlockScreen

/** In-memory navigation. Only item ids are kept here, never secret values. */
private sealed interface Screen {
    data object Home : Screen
    data class Detail(val id: String) : Screen
    data class Edit(val id: String?, val type: ItemType) : Screen
    data object Generator : Screen
    data object Settings : Screen
}

@Composable
fun AppRoot(repo: VaultRepository) {
    val state by repo.state.collectAsState()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = state::class,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "vault-state",
        ) { kind ->
            when (kind) {
                VaultState.NoVault::class -> SetupScreen(repo)
                VaultState.Locked::class -> UnlockScreen(repo)
                else -> UnlockedApp(repo)
            }
        }
    }
}

@Composable
private fun UnlockedApp(repo: VaultRepository) {
    val state by repo.state.collectAsState()
    val items = (state as? VaultState.Unlocked)?.items ?: return
    val reauth = remember { ReauthController(repo) }
    // A fresh back stack every time the vault is unlocked: after locking you always land on Home.
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }

    fun push(s: Screen) = stack.add(s)
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    BackHandler(enabled = stack.size > 1 && stack.last() !is Screen.Edit) { pop() }

    when (val screen = stack.last()) {
        Screen.Home -> HomeScreen(
            repo = repo,
            items = items,
            onOpen = { push(Screen.Detail(it)) },
            onAdd = { push(Screen.Edit(null, it)) },
            onSettings = { push(Screen.Settings) },
            onGenerator = { push(Screen.Generator) },
        )
        is Screen.Detail -> {
            val item = items.firstOrNull { it.id == screen.id }
            if (item == null) {
                pop()
            } else {
                DetailScreen(repo, reauth, item, onBack = { pop() }, onEdit = { push(Screen.Edit(item.id, item.type)) })
            }
        }
        is Screen.Edit -> EditScreen(
            repo = repo,
            reauth = reauth,
            existing = screen.id?.let { id -> items.firstOrNull { it.id == id } },
            newType = screen.type,
            onDone = { savedId ->
                pop()
                // After creating a new item, show it.
                if (screen.id == null && savedId != null) push(Screen.Detail(savedId))
            },
        )
        Screen.Generator -> GeneratorScreen(repo = repo, onBack = { pop() }, onUse = null)
        Screen.Settings -> SettingsScreen(repo, onBack = { pop() })
    }

    ReauthHost(reauth, repo)
}
