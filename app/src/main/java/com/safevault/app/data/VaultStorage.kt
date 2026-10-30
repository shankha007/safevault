package com.safevault.app.data

import android.util.AtomicFile
import java.io.File

/**
 * The two files that make up a vault, kept in the app's private no-backup directory:
 *  - vault.meta : plain JSON (VaultMeta) — salt, KDF settings, wrapped keys, settings
 *  - vault.dat  : AES-256-GCM encrypted VaultData
 *
 * AtomicFile writes to a temp file and renames, so a crash mid-save can't corrupt the vault.
 */
class VaultStorage(dir: File) {
    private val metaFile = AtomicFile(File(dir, "vault.meta"))
    private val dataFile = AtomicFile(File(dir, "vault.dat"))

    fun exists(): Boolean = metaFile.baseFile.exists() && dataFile.baseFile.exists()

    fun readMeta(): VaultMeta =
        VaultCodec.json.decodeFromString(VaultMeta.serializer(), String(metaFile.readFully(), Charsets.UTF_8))

    fun writeMeta(meta: VaultMeta) =
        write(metaFile, VaultCodec.json.encodeToString(VaultMeta.serializer(), meta).toByteArray(Charsets.UTF_8))

    fun readData(): ByteArray = dataFile.readFully()

    fun writeData(blob: ByteArray) = write(dataFile, blob)

    /** Overwrites the files with zeros before deleting them. */
    fun wipe() {
        for (f in listOf(metaFile, dataFile)) {
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
