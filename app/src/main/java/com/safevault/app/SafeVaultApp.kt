package com.safevault.app

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.ProcessLifecycleOwner
import com.safevault.app.data.VaultRepository
import com.safevault.app.data.VaultStorage
import com.safevault.app.security.AutoLock
import com.safevault.app.security.SecureClipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Creates the app-wide singletons (simple manual dependency injection). */
class SafeVaultApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var repository: VaultRepository
        private set
    lateinit var autoLock: AutoLock
        private set
    lateinit var clipboard: SecureClipboard
        private set

    override fun onCreate() {
        super.onCreate()
        // noBackupFilesDir is never included in any Android backup, on any version.
        repository = VaultRepository(
            VaultStorage(noBackupFilesDir),
            bootCount = { Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, 0) },
        )
        autoLock = AutoLock(repository, appScope)
        clipboard = SecureClipboard(this, appScope)
        ProcessLifecycleOwner.get().lifecycle.addObserver(autoLock)
    }
}
