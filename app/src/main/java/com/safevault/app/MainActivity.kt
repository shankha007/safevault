package com.safevault.app

import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.fragment.app.FragmentActivity
import com.safevault.app.ui.AppRoot
import com.safevault.app.ui.LocalActivity
import com.safevault.app.ui.theme.SafeVaultTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Block screenshots, screen recording, casting, and the preview in the recent-apps list.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as SafeVaultApp
        setContent {
            CompositionLocalProvider(LocalActivity provides this) {
                IncognitoKeyboard {
                    SafeVaultTheme { AppRoot(app.repository) }
                }
            }
        }
        // Ignore taps that pass through another app's overlay (tapjacking, D-037). Dialogs are
        // separate windows and do the same through IgnoreObscuredTouches.
        window.decorView.filterTouchesWhenObscured = true
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        (application as SafeVaultApp).autoLock.onUserInteraction()
        return super.dispatchTouchEvent(ev)
    }
}

/**
 * Asks the keyboard not to learn anything typed in this app (IME_FLAG_NO_PERSONALIZED_LEARNING,
 * the flag behind Gboard's incognito mode), so names, notes and usernames don't end up in its
 * suggestions or its cloud-synced dictionary (D-037). Covers every text field, dialogs included.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun IncognitoKeyboard(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(
        interceptor = { request, nextHandler ->
            val incognito = object : PlatformTextInputMethodRequest {
                override fun createInputConnection(outAttributes: EditorInfo): InputConnection {
                    val connection = request.createInputConnection(outAttributes)
                    outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                    return connection
                }
            }
            nextHandler.startInputMethod(incognito)
        },
        content = content,
    )
}
