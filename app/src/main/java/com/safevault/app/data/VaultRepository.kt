package com.safevault.app.data

import android.os.SystemClock
import com.safevault.app.crypto.BiometricKeys
import com.safevault.app.crypto.CryptoException
import com.safevault.app.crypto.KdfParams
import com.safevault.app.crypto.VaultKeys
import com.safevault.app.crypto.fromB64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.crypto.Cipher

sealed interface VaultState {
    data object NoVault : VaultState
    data object Locked : VaultState
    data class Unlocked(val items: List<VaultItem>) : VaultState
}

sealed interface UnlockResult {
    data object Success : UnlockResult
    data class WrongPassword(val attemptsLeftBeforeWipe: Int?) : UnlockResult
    data class LockedOut(val remainingMs: Long) : UnlockResult
    data object Wiped : UnlockResult
}

/**
 * The single owner of vault state. Holds the decrypted keys only while unlocked and wipes
 * them on [lock]. All disk and crypto work runs on Dispatchers.Default / IO.
 */
class VaultRepository(
    private val storage: VaultStorage,
    private val clock: () -> Long = System::currentTimeMillis,
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
    /** Android's boot count (Settings.Global.BOOT_COUNT), to notice reboots during a lockout. */
    private val bootCount: () -> Int = { 0 },
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<VaultState>(if (storage.exists()) VaultState.Locked else VaultState.NoVault)
    val state: StateFlow<VaultState> = _state.asStateFlow()

    private val _meta = MutableStateFlow(if (storage.exists()) storage.readMeta() else null)
    val meta: StateFlow<VaultMeta?> = _meta.asStateFlow()

    val settings: VaultSettings get() = _meta.value?.settings ?: VaultSettings()

    private var masterKey: ByteArray? = null
    private var keys: VaultKeys? = null

    val isUnlocked: Boolean get() = keys != null

    // ---- create / unlock / lock ------------------------------------------------------

    suspend fun createVault(password: CharArray) = withContext(Dispatchers.Default) {
        mutex.withLock {
            val created = VaultCodec.createVault(password)
            val k = VaultKeys.fromMaster(created.masterKey)
            storage.writeData(VaultCodec.encryptData(k, VaultData()))
            storage.writeMeta(created.meta)
            _meta.value = created.meta
            BiometricKeys.ensureReauthKey()
            openSession(created.masterKey, k, VaultData())
        }
    }

    suspend fun unlockWithPassword(password: CharArray): UnlockResult = withContext(Dispatchers.Default) {
        mutex.withLock {
            val (master, result) = checkPasswordLocked(password)
            if (master != null) openFromDisk(master)
            result
        }
    }

    /** Cipher for the fingerprint prompt, or null if fingerprint unlock isn't usable now. */
    fun biometricUnlockCipher(): Cipher? {
        val wrapped = _meta.value?.biometricWrappedKey ?: return null
        val cipher = BiometricKeys.decryptCipher(wrapped)
        if (cipher == null) {
            // Key was invalidated (fingerprints changed) or is unusable. Turn the feature off.
            _meta.value?.let { saveMeta(it.copy(biometricWrappedKey = null)) }
        }
        return cipher
    }

    suspend fun unlockWithBiometric(cipher: Cipher) = withContext(Dispatchers.Default) {
        mutex.withLock {
            val meta = requireNotNull(_meta.value)
            val master = BiometricKeys.unwrap(cipher, requireNotNull(meta.biometricWrappedKey))
            openFromDisk(master)
            if (meta.failedAttempts != 0 || meta.lockoutMs != 0L) saveMeta(Lockout.clear(meta))
        }
    }

    fun lock() {
        keys?.wipe()
        masterKey?.fill(0)
        keys = null
        masterKey = null
        if (_state.value is VaultState.Unlocked) _state.value = VaultState.Locked
    }

    /**
     * Checks the master password without changing state (used before sensitive actions).
     * Wrong answers count towards the same lockout and auto-erase as the unlock screen (D-034).
     */
    suspend fun verifyPassword(password: CharArray): UnlockResult = withContext(Dispatchers.Default) {
        mutex.withLock {
            val (master, result) = checkPasswordLocked(password)
            master?.fill(0)
            result
        }
    }

    /** Milliseconds until another password may be tried; 0 = now. */
    suspend fun lockoutRemainingMs(): Long = withContext(Dispatchers.IO) { mutex.withLock { lockoutRemainingLocked() } }

    private fun lockoutRemainingLocked(): Long {
        val meta = _meta.value ?: return 0
        val (ms, updated) = Lockout.remaining(meta, bootCount(), elapsed())
        updated?.let { saveMeta(it) }
        return ms
    }

    /**
     * Every master-password check goes through here (caller holds [mutex]). Returns the master
     * key on success; otherwise counts the failure, which may lock out or erase the vault.
     */
    private fun checkPasswordLocked(password: CharArray): Pair<ByteArray?, UnlockResult> {
        val remaining = lockoutRemainingLocked()
        if (remaining > 0) return null to UnlockResult.LockedOut(remaining)
        val meta = requireNotNull(_meta.value)
        val master = VaultCodec.unwrapMaster(password, meta)
        if (master != null) {
            if (meta.failedAttempts != 0 || meta.lockoutMs != 0L) saveMeta(Lockout.clear(meta))
            // The password is proven here, so this is the safe moment to (re)create the key
            // behind the "confirm it's you" fingerprint check (D-036).
            BiometricKeys.ensureReauthKey()
            return master to UnlockResult.Success
        }
        val updated = Lockout.fail(meta, bootCount(), elapsed())
        if (meta.settings.wipeAfterFailures && updated.failedAttempts >= MAX_FAILED_ATTEMPTS) {
            wipeLocked()
            return null to UnlockResult.Wiped
        }
        saveMeta(updated)
        return null to if (updated.lockoutMs > 0) {
            UnlockResult.LockedOut(updated.lockoutMs)
        } else {
            UnlockResult.WrongPassword(
                if (meta.settings.wipeAfterFailures) MAX_FAILED_ATTEMPTS - updated.failedAttempts else null,
            )
        }
    }

    private fun openFromDisk(master: ByteArray) {
        val k = VaultKeys.fromMaster(master)
        val data = try {
            VaultCodec.decryptData(k, storage.readData())
        } catch (e: CryptoException) {
            // A password change crashed after writing the new meta but before swapping in
            // the new data: the staged file is the one this key opens. Finish the swap.
            val pending = storage.readPendingData() ?: throw e
            VaultCodec.decryptData(k, pending).also { storage.commitPendingData() }
        }
        // A change that crashed before its commit point left a staged file nobody can use.
        storage.discardPendingData()
        openSession(master, k, data)
    }

    private fun openSession(master: ByteArray, k: VaultKeys, data: VaultData) {
        masterKey = master
        keys = k
        _state.value = VaultState.Unlocked(sortItems(data.items))
    }

    // ---- items ----------------------------------------------------------------------

    fun newItemId(): String = UUID.randomUUID().toString()

    fun item(id: String): VaultItem? = (state.value as? VaultState.Unlocked)?.items?.firstOrNull { it.id == id }

    fun seal(value: String, itemId: String, field: String): Sealed? =
        if (value.isEmpty()) null else requireKeys().sealField(value, Field.context(itemId, field))

    fun reveal(sealed: Sealed?, itemId: String, field: String): String =
        sealed?.let { requireKeys().openField(it, Field.context(itemId, field)) } ?: ""

    suspend fun upsert(item: VaultItem) = mutate { items ->
        val existing = items.indexOfFirst { it.id == item.id }
        if (existing >= 0) items.toMutableList().also { it[existing] = item } else items + item
    }

    suspend fun delete(id: String) = mutate { items -> items.filterNot { it.id == id } }

    suspend fun toggleFavorite(id: String) = mutate { items ->
        items.map { if (it.id == id) it.copy(favorite = !it.favorite, updatedAt = clock()) else it }
    }

    private suspend fun mutate(change: (List<VaultItem>) -> List<VaultItem>) = withContext(Dispatchers.Default) {
        mutex.withLock {
            val current = (state.value as? VaultState.Unlocked)?.items ?: error("Vault is locked")
            val next = change(current)
            storage.writeData(VaultCodec.encryptData(requireKeys(), VaultData(next)))
            _state.value = VaultState.Unlocked(sortItems(next))
        }
    }

    private fun sortItems(items: List<VaultItem>) = items.sortedBy { it.name.lowercase() }

    private fun requireKeys() = keys ?: error("Vault is locked")

    // ---- settings, biometrics, password change ---------------------------------------

    fun updateSettings(change: (VaultSettings) -> VaultSettings) {
        val meta = _meta.value ?: return
        saveMeta(meta.copy(settings = change(meta.settings)))
    }

    val biometricEnabled: Boolean get() = _meta.value?.biometricWrappedKey != null

    fun biometricEnrollCipher(): Cipher = BiometricKeys.encryptCipher()

    /** Called after a successful fingerprint prompt with the encrypt cipher. */
    fun enableBiometric(cipher: Cipher) {
        val master = requireNotNull(masterKey) { "Vault is locked" }
        val meta = requireNotNull(_meta.value)
        saveMeta(meta.copy(biometricWrappedKey = BiometricKeys.wrap(cipher, master)))
    }

    fun disableBiometric() {
        BiometricKeys.deleteKey()
        _meta.value?.let { saveMeta(it.copy(biometricWrappedKey = null)) }
    }

    /**
     * Checks [current] like any password check (wrong answers count, D-034). Replaces the master
     * key (see VaultCodec.rotateMaster) and turns fingerprint unlock off. Write order is
     * crash-safe: see VaultStorage.
     */
    suspend fun changePassword(current: CharArray, new: CharArray): UnlockResult = withContext(Dispatchers.Default) {
        mutex.withLock {
            val items = (state.value as? VaultState.Unlocked)?.items ?: error("Vault is locked")
            val (oldMaster, result) = checkPasswordLocked(current)
            if (oldMaster == null) return@withLock result
            val meta = requireNotNull(_meta.value)
            val oldKeys = VaultKeys.fromMaster(oldMaster)
            try {
                val r = VaultCodec.rotateMaster(meta, items, oldKeys, new)
                storage.writePendingData(r.encryptedData)
                saveMeta(r.meta) // commit point
                storage.commitPendingData()
                BiometricKeys.deleteKey()
                // Swap the session to the new keys without passing through Locked.
                keys?.wipe()
                masterKey?.fill(0)
                openSession(r.masterKey, r.keys, VaultData(r.items))
            } finally {
                oldKeys.wipe()
                oldMaster.fill(0)
            }
            UnlockResult.Success
        }
    }

    private fun saveMeta(meta: VaultMeta) {
        storage.writeMeta(meta)
        _meta.value = meta
    }

    // ---- backup / restore / wipe ----------------------------------------------------

    /** Writes an encrypted backup. It opens with the master password in use right now. */
    suspend fun exportBackup(out: OutputStream) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val meta = requireNotNull(_meta.value)
            val text = VaultCodec.makeBackup(meta, storage.readData(), clock())
            out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }
    }

    /** Throws IllegalArgumentException with a message for the user if the file is unusable. */
    suspend fun readBackup(input: InputStream): BackupFile = withContext(Dispatchers.IO) {
        VaultCodec.parseBackup(input.use { VaultCodec.readBackupText(it) })
    }

    /**
     * First-time restore: the backup becomes this phone's vault, and its password becomes the
     * master password. Returns false if the password is wrong.
     *
     * The key is wrapped again with this app's own Argon2 settings, not the ones written in
     * the file, so a crafted backup can't leave the vault on weak settings (D-035).
     */
    suspend fun restoreAsNewVault(backup: BackupFile, password: CharArray): Boolean = withContext(Dispatchers.Default) {
        mutex.withLock {
            val opened = VaultCodec.openBackup(backup, password) ?: return@withLock false
            val meta = VaultCodec.rewrap(VaultMeta(kdf = KdfParams(), salt = "", wrappedKey = ""), opened.masterKey, password)
            storage.discardPendingData()
            storage.writeData(backup.data.fromB64())
            storage.writeMeta(meta)
            _meta.value = meta
            BiometricKeys.ensureReauthKey()
            openSession(opened.masterKey, VaultKeys.fromMaster(opened.masterKey), opened.data)
            true
        }
    }

    /**
     * Merges a backup into the open vault. Secrets are re-encrypted under this vault's key.
     * Returns null if the password is wrong.
     */
    suspend fun importBackup(backup: BackupFile, password: CharArray): VaultCodec.MergeResult? =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val opened = VaultCodec.openBackup(backup, password) ?: return@withLock null
                val from = VaultKeys.fromMaster(opened.masterKey)
                try {
                    val to = requireKeys()
                    val incoming = opened.data.items.map { VaultCodec.resealItem(it, from, to) }
                    val current = (state.value as VaultState.Unlocked).items
                    val result = VaultCodec.merge(current, incoming)
                    storage.writeData(VaultCodec.encryptData(to, VaultData(result.items)))
                    _state.value = VaultState.Unlocked(sortItems(result.items))
                    result
                } finally {
                    from.wipe()
                    opened.masterKey.fill(0)
                }
            }
        }

    suspend fun deleteVault() = withContext(Dispatchers.IO) { mutex.withLock { wipeLocked() } }

    private fun wipeLocked() {
        lock()
        BiometricKeys.deleteKey()
        BiometricKeys.deleteReauthKey()
        storage.wipe()
        _meta.value = null
        _state.value = VaultState.NoVault
    }
}
