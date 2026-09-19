package at.clavierhaus.unisonmaster.persistence

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM with a key generated inside the Android Keystore. The key is
 * created on first use, never leaves the secure hardware and differs per
 * device, so the open source code does not help anyone forge a save file.
 *
 * Layout: [format 1 byte][IV 12 bytes][ciphertext + 16-byte tag]
 */
class KeystoreSealer(private val alias: String = "clavier-tuner-session") : Sealer {

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(STORE).apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE)
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    override fun seal(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        require(iv.size == IV_BYTES) { "unexpected IV length ${iv.size}" }
        return byteArrayOf(FORMAT) + iv + cipher.doFinal(plain)
    }

    override fun open(sealed: ByteArray): ByteArray {
        require(sealed.size > 1 + IV_BYTES + TAG_BITS / 8) { "too short" }
        require(sealed[0] == FORMAT) { "unknown format" }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 1, IV_BYTES))
        return cipher.doFinal(sealed, 1 + IV_BYTES, sealed.size - 1 - IV_BYTES)
    }

    private companion object {
        const val STORE = "AndroidKeyStore"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val FORMAT: Byte = 1
    }
}

/** The save file in app-private storage; written via a temporary file and a rename. */
class PrivateSaveFile(private val dir: File, private val name: String = "last-tuning.bin") : SaveFile {
    private val file get() = File(dir, name)

    override fun read(): ByteArray? = file.takeIf { it.isFile }?.readBytes()

    override fun writeAtomic(bytes: ByteArray) {
        dir.mkdirs()
        val tmp = File(dir, "$name.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "could not replace the save file" }
        }
    }

    override fun delete() {
        file.delete()
        File(dir, "$name.tmp").delete()
    }

    override fun setAside() {
        val f = file
        if (f.isFile) f.renameTo(File(dir, "$name.rejected-${System.currentTimeMillis()}"))
    }

    private fun setAsideFiles(): List<File> =
        dir.listFiles { f -> f.isFile && f.name.startsWith("$name.rejected-") }?.sortedBy { it.name } ?: emptyList()

    override fun readSetAside(): ByteArray? = setAsideFiles().lastOrNull()?.readBytes()

    override fun clearSetAside() { for (f in setAsideFiles()) f.delete() }
}
