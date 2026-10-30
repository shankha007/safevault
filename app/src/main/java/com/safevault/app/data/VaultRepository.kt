package com.safevault.app.data

import com.safevault.app.crypto.BiometricKeys
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
import kotlin.math.min
import kotlin.math.pow

sealed interface VaultState {
    data object NoVault : VaultState
    data object Locked : VaultState
    data class Unlocked(val items: List<VaultItem>) : VaultState
}

sealed interface UnlockResult {
    data object Success : UnlockResult
    data class WrongPassword(val attemptsLeftBeforeWipe: Int?) : UnlockResult
    data class LockedOut(val untilMillis: Long) : UnlockResult
    data object Wiped : UnlockResult
}

/**
 * The single owner of vault state. Holds the decrypted keys only while unlocked and wipes
 * them on [lock]. All disk and crypto work runs on Dispatchers.Default / IO.
 */
class VaultRepository(
    private val storage: VaultStorage,
    private val clock: () -> Long = System::currentTimeMillis,
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
            openSession(created.masterKey, k, VaultData())
        }
    }

    suspend fun unlockWithPassword(password: CharArray): UnlockResult = withContext(Dispatchers.Default) {
        mutex.withLock {
            val meta = requireNotNull(_meta.value)
            val now = clock()
            if (meta.lockoutUntil > now) return@withLock UnlockResult.LockedOut(meta.lockoutUntil)

            val master = VaultCodec.unwrapMaster(password, meta)
            if (master == null) {
                val failures = meta.failedAttempts + 1
                if (meta.settings.wipeAfterFailures && failures >= MAX_FAILED_ATTEMPTS) {
                    wipeLocked()
                    return@withLock UnlockResult.Wiped
                }
                val updated = meta.copy(failedAttempts = failures, lockoutUntil = lockoutFor(failures, now))
                saveMeta(updated)
                return@withLock if (updated.lockoutUntil > now) {
                    UnlockResult.LockedOut(updated.lockoutUntil)
                } else {
                    UnlockResult.WrongPassword(
                        if (meta.settings.wipeAfterFailures) MAX_FAILED_ATTEMPTS - failures else null,
                    )
                }
            }
            openFromDisk(master)
            if (meta.failedAttempts != 0 || meta.lockoutUntil != 0L) {
                saveMeta(meta.copy(failedAttempts = 0, lockoutUntil = 0))
            }
            UnlockResult.Success
        }
    }

    /** Cipher for the fingerprint prompt, or null if fingerprint unlock isn't usable now. */
    fun biometricUnlockCipher(): Cipher? {
        val wrapped = _meta.value?.biometricWrappedKey ?: return null
        val cipher = BiometricKeys.decryptCipher(wrapped)
        if (cipher == null) {
            // Key was invalidated (fingerprints changed). Turn the feature off.
            _meta.value?.let { saveMeta(it.copy(biometricWrappedKey = null)) }
        }
        return cipher
    }

    suspend fun unlockWithBiometric(cipher: Cipher) = withContext(Dispatchers.Default) {
        mutex.withLock {
            val meta = requireNotNull(_meta.value)
            val master = BiometricKeys.unwrap(cipher, requireNotNull(meta.biometricWrappedKey))
            openFromDisk(master)
            if (meta.failedAttempts != 0) saveMeta(meta.copy(failedAttempts = 0, lockoutUntil = 0))
        }
    }

    fun lock() {
        keys?.wipe()
        masterKey?.fill(0)
        keys = null
        masterKey = null
        if (_state.value is VaultState.Unlocked) _state.value = VaultState.Locked
    }

    /** Checks the master password without changing state (used before sensitive actions). */
    suspend fun verifyPassword(password: CharArray): Boolean = withContext(Dispatchers.Default) {
        val meta = _meta.value ?: return@withContext false
        VaultCodec.unwrapMaster(password, meta)?.also { it.fill(0) } != null
    }

    private fun lockoutFor(failures: Int, now: Long): Long {
        // First 4 mistakes are free (typos). After that: 30 s, 1 min, 2 min ... capped at 1 hour.
        if (failures < 5) return 0
        val seconds = min(30.0 * 2.0.pow(failures - 5), 3600.0)
        return now + (seconds * 1000).toLong()
    }

    private fun openFromDisk(master: ByteArray) {
        val k = VaultKeys.fromMaster(master)
        val data = VaultCodec.decryptData(k, storage.readData())
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

    /** Returns false if [current] is wrong. */
    suspend fun changePassword(current: CharArray, new: CharArray): Boolean = withContext(Dispatchers.Default) {
        mutex.withLock {
            val meta = requireNotNull(_meta.value)
            val master = VaultCodec.unwrapMaster(current, meta) ?: return@withLock false
            try {
                saveMeta(VaultCodec.rewrap(meta, master, new))
            } finally {
                master.fill(0)
            }
            true
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

    fun readBackup(input: InputStream): BackupFile =
        VaultCodec.parseBackup(input.use { String(it.readBytes(), Charsets.UTF_8) })

    /**
     * First-time restore: the backup becomes this phone's vault, and its password becomes the
     * master password. Returns false if the password is wrong.
     */
    suspend fun restoreAsNewVault(backup: BackupFile, password: CharArray): Boolean = withContext(Dispatchers.Default) {
        mutex.withLock {
            val opened = VaultCodec.openBackup(backup, password) ?: return@withLock false
            val meta = VaultMeta(kdf = backup.kdf, salt = backup.salt, wrappedKey = backup.wrappedKey)
            storage.writeData(backup.data.fromB64())
            storage.writeMeta(meta)
            _meta.value = meta
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
        storage.wipe()
        _meta.value = null
        _state.value = VaultState.NoVault
    }
}
