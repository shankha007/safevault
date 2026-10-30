package com.safevault.app

import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
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
                SafeVaultTheme { AppRoot(app.repository) }
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        (application as SafeVaultApp).autoLock.onUserInteraction()
        return super.dispatchTouchEvent(ev)
    }
}
