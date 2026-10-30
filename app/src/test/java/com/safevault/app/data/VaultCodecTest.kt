package com.safevault.app.data

import com.safevault.app.crypto.CryptoException
import com.safevault.app.crypto.KdfParams
import com.safevault.app.crypto.VaultKeys
import com.safevault.app.crypto.fromB64
import com.safevault.app.crypto.toB64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultCodecTest {
    // Small KDF settings keep the tests fast; the format is the same.
    private val fastKdf = KdfParams(memoryKiB = 1024, iterations = 1, parallelism = 1)
    private val password = "correct horse battery staple".toCharArray()

    private fun sampleItem(keys: VaultKeys, id: String = "item-1", updatedAt: Long = 1L): VaultItem {
        val ctx = Field.context(id, Field.PASSWORD)
        return VaultItem(
            id = id,
            type = ItemType.LOGIN,
            name = "Gmail",
            createdAt = 1L,
            updatedAt = updatedAt,
            login = LoginData(username = "me@example.com", password = keys.sealField("hunter2!", ctx)),
        )
    }

    @Test
    fun correctPasswordUnwrapsMasterKey() {
        val vault = VaultCodec.createVault(password, fastKdf)
        val master = VaultCodec.unwrapMaster(password, vault.meta)
        assertArrayEquals(vault.masterKey, master)
    }

    @Test
    fun wrongPasswordReturnsNull() {
        val vault = VaultCodec.createVault(password, fastKdf)
        assertNull(VaultCodec.unwrapMaster("wrong password".toCharArray(), vault.meta))
    }

    @Test
    fun dataRoundTripsAndSecretsStaySealed() {
        val vault = VaultCodec.createVault(password, fastKdf)
        val keys = VaultKeys.fromMaster(vault.masterKey)
        val data = VaultData(listOf(sampleItem(keys)))

        val blob = VaultCodec.encryptData(keys, data)
        val text = String(blob, Charsets.ISO_8859_1)
        assertFalse("vault file must not contain plaintext", text.contains("Gmail"))

        val decoded = VaultCodec.decryptData(keys, blob)
        assertEquals(data, decoded)
        // After the outer layer is removed, the password is still encrypted.
        val sealed = decoded.items[0].login!!.password!!
        assertFalse(sealed.contains("hunter2"))
        assertEquals("hunter2!", keys.openField(sealed, Field.context("item-1", Field.PASSWORD)))
    }

    @Test
    fun tamperedFileIsRejected() {
        val vault = VaultCodec.createVault(password, fastKdf)
        val keys = VaultKeys.fromMaster(vault.masterKey)
        val blob = VaultCodec.encryptData(keys, VaultData(listOf(sampleItem(keys))))
        blob[blob.size / 2] = (blob[blob.size / 2].toInt() xor 1).toByte()
        assertThrows(CryptoException::class.java) { VaultCodec.decryptData(keys, blob) }
    }

    @Test
    fun sealedFieldCannotBeMovedToAnotherField() {
        val keys = VaultKeys.fromMaster(ByteArray(32) { it.toByte() })
        val sealed = keys.sealField("1234", Field.context("a", Field.PIN))
        assertThrows(CryptoException::class.java) { keys.openField(sealed, Field.context("a", Field.CVV)) }
        assertThrows(CryptoException::class.java) { keys.openField(sealed, Field.context("b", Field.PIN)) }
    }

    @Test
    fun sameValueEncryptsDifferentlyEachTime() {
        val keys = VaultKeys.fromMaster(ByteArray(32))
        val ctx = Field.context("a", Field.PASSWORD)
        assertTrue(keys.sealField("x", ctx) != keys.sealField("x", ctx))
    }

    @Test
    fun changingPasswordKeepsSameMasterKey() {
        val vault = VaultCodec.createVault(password, fastKdf)
        val newPw = "a brand new password".toCharArray()
        val meta2 = VaultCodec.rewrap(vault.meta, vault.masterKey, newPw)
        assertNull(VaultCodec.unwrapMaster(password, meta2))
        assertArrayEquals(vault.masterKey, VaultCodec.unwrapMaster(newPw, meta2))
    }

    @Test
    fun backupRoundTrip() {
        val vault = VaultCodec.createVault(password, fastKdf)
        val keys = VaultKeys.fromMaster(vault.masterKey)
        val data = VaultData(listOf(sampleItem(keys)))
        val text = VaultCodec.makeBackup(vault.meta, VaultCodec.encryptData(keys, data), now = 5L)
        assertFalse(text.contains("Gmail"))

        val backup = VaultCodec.parseBackup(text)
        assertNull(VaultCodec.openBackup(backup, "nope".toCharArray()))
        val opened = VaultCodec.openBackup(backup, password)
        assertNotNull(opened)
        assertEquals(data, opened!!.data)
    }

    @Test
    fun nonBackupFileIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { VaultCodec.parseBackup("{\"hello\":1}") }
        assertThrows(IllegalArgumentException::class.java) { VaultCodec.parseBackup("not json") }
    }

    @Test
    fun resealMovesSecretsToNewKey() {
        val from = VaultKeys.fromMaster(ByteArray(32) { 1 })
        val to = VaultKeys.fromMaster(ByteArray(32) { 2 })
        val moved = VaultCodec.resealItem(sampleItem(from), from, to)
        val ctx = Field.context("item-1", Field.PASSWORD)
        assertEquals("hunter2!", to.openField(moved.login!!.password!!, ctx))
    }

    @Test
    fun mergeKeepsNewestCopy() {
        val keys = VaultKeys.fromMaster(ByteArray(32))
        val current = listOf(sampleItem(keys, "a", updatedAt = 10), sampleItem(keys, "b", updatedAt = 10))
        val incoming = listOf(
            sampleItem(keys, "a", updatedAt = 5).copy(name = "old"),
            sampleItem(keys, "b", updatedAt = 20).copy(name = "new"),
            sampleItem(keys, "c", updatedAt = 1),
        )
        val result = VaultCodec.merge(current, incoming)
        assertEquals(1, result.added)
        assertEquals(1, result.updated)
        val byId = result.items.associateBy { it.id }
        assertEquals("Gmail", byId["a"]!!.name)
        assertEquals("new", byId["b"]!!.name)
        assertTrue("c" in byId)
    }

    @Test
    fun argon2IsDeterministicAndSalted() {
        // The same input always gives the same key, and changing the salt changes it.
        val salt1 = ByteArray(16) { 1 }.toB64()
        val salt2 = ByteArray(16) { 2 }.toB64()
        val k1 = com.safevault.app.crypto.Kdf.derive(password, salt1.fromB64(), fastKdf)
        val k1b = com.safevault.app.crypto.Kdf.derive(password, salt1.fromB64(), fastKdf)
        val k2 = com.safevault.app.crypto.Kdf.derive(password, salt2.fromB64(), fastKdf)
        assertArrayEquals(k1, k1b)
        assertFalse(k1.contentEquals(k2))
    }
}
