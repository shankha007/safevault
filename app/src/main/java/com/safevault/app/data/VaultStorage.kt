package com.safevault.app.data

import android.util.AtomicFile
import java.io.File

/**
 * The two files that make up a vault, kept in the app's private no-backup directory:
 *  - vault.meta : plain JSON (VaultMeta) — salt, KDF settings, wrapped keys, settings
 *  - vault.dat  : AES-256-GCM encrypted VaultData
 *
 * AtomicFile writes to a temp file and renames, so a crash mid-save can't corrupt the vault.
 *
 * vault.dat.next exists only during a master-key change, which has to replace both files:
 * the new data is staged there, then vault.meta is written (the commit point), then the
 * staged file is renamed over vault.dat. VaultRepository finishes or discards a change that
 * a crash interrupted, so the two files never end up under different keys.
 */
class VaultStorage(dir: File) {
    private val metaFile = AtomicFile(File(dir, "vault.meta"))
    private val dataFile = AtomicFile(File(dir, "vault.dat"))
    private val pendingFile = AtomicFile(File(dir, "vault.dat.next"))

    fun exists(): Boolean = metaFile.baseFile.exists() && dataFile.baseFile.exists()

    fun readMeta(): VaultMeta =
        VaultCodec.json.decodeFromString(VaultMeta.serializer(), String(metaFile.readFully(), Charsets.UTF_8))

    fun writeMeta(meta: VaultMeta) =
        write(metaFile, VaultCodec.json.encodeToString(VaultMeta.serializer(), meta).toByteArray(Charsets.UTF_8))

    fun readData(): ByteArray = dataFile.readFully()

    fun writeData(blob: ByteArray) = write(dataFile, blob)

    fun writePendingData(blob: ByteArray) = write(pendingFile, blob)

    fun readPendingData(): ByteArray? = if (pendingFile.baseFile.exists()) pendingFile.readFully() else null

    /** Makes the staged data the vault data (a single rename). */
    fun commitPendingData() {
        check(pendingFile.baseFile.renameTo(dataFile.baseFile)) { "Couldn't replace the vault file" }
    }

    fun discardPendingData() {
        pendingFile.delete()
    }

    /** Overwrites the files with zeros before deleting them. */
    fun wipe() {
        for (f in listOf(metaFile, dataFile, pendingFile)) {
            val base = f.baseFile
            if (base.exists()) runCatching { base.writeBytes(ByteArray(base.length().toInt())) }
            f.delete()
        }
    }

    private fun write(file: AtomicFile, bytes: ByteArray) {
        val out = file.startWrite()
        try {
            out.write(bytes)
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }
}
