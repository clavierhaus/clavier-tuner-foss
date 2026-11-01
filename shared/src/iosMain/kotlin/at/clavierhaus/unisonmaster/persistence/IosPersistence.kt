@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package at.clavierhaus.unisonmaster.persistence

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFErrorGetCode
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFNumberIntType
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.Security.SecItemCopyMatching
import platform.Security.SecKeyCopyPublicKey
import platform.Security.SecKeyCreateDecryptedData
import platform.Security.SecKeyCreateEncryptedData
import platform.Security.SecKeyCreateRandomKey
import platform.Security.SecKeyRef
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrApplicationTag
import platform.Security.kSecAttrIsPermanent
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import platform.Security.kSecClass
import platform.Security.kSecClassKey
import platform.Security.kSecKeyAlgorithmECIESEncryptionCofactorVariableIVX963SHA256AESGCM
import platform.Security.kSecPrivateKeyAttrs
import platform.Security.kSecReturnRef
import platform.posix.memcpy

/*
 * The saved tuning on the iPhone (1 Nov 2026): what Android has had since September -- one sealed file in the
 * app's private storage, continued after a restart -- so Continue works on the iPhone as it does on Android. The
 * file is the same SessionSnapshot, sealed by a key that lives in the phone's keychain and never in the code.
 */

/**
 * Authenticated encryption under a key made in the keychain on first use, kept on this device only (never in a
 * backup) and never readable by the app: an elliptic-curve key (P-256) with Apple's ECIES -- a fresh key agreement
 * per file and AES-GCM over the data, so a file changed, or sealed on another phone, does not open. The open source
 * code does not help anyone forge a save file, as with Android's KeystoreSealer.
 *
 * Layout: [format 1 byte][Apple's ECIES message: ephemeral public key, ciphertext, 16-byte tag]
 */
class KeychainSealer private constructor(private val tag: String, private val inKeychain: Boolean) : Sealer {

    constructor(tag: String = "at.clavierhaus.claviertuner.session") : this(tag, inKeychain = true)

    internal companion object {
        /**
         * The same seal under a key held in memory only, never in the keychain: for the simulator suite, whose test
         * process has no keychain at all (errSecNotAvailable, -25291, CI run 41341321396). It checks the seal and
         * its refusal; whether the key is kept in the keychain across a restart is seen only on a phone.
         */
        fun inMemory(): KeychainSealer = KeychainSealer("in-memory", inKeychain = false)
    }

    override fun seal(plain: ByteArray): ByteArray {
        val pub = SecKeyCopyPublicKey(privateKey()) ?: error("no public key")
        try {
            val input = cfData(plain)
            try {
                val out = SecKeyCreateEncryptedData(pub, kSecKeyAlgorithmECIESEncryptionCofactorVariableIVX963SHA256AESGCM, input, null)
                    ?: error("the keychain refused to seal")
                try { return byteArrayOf(FORMAT) + bytes(out) } finally { CFRelease(out) }
            } finally { CFRelease(input) }
        } finally { CFRelease(pub) }
    }

    override fun open(sealed: ByteArray): ByteArray {
        require(sealed.size > 1) { "too short" }
        require(sealed[0] == FORMAT) { "unknown format" }
        val input = cfData(sealed.copyOfRange(1, sealed.size))
        try {
            val out = SecKeyCreateDecryptedData(privateKey(), kSecKeyAlgorithmECIESEncryptionCofactorVariableIVX963SHA256AESGCM, input, null)
                ?: error("the file was changed or sealed elsewhere")
            try { return bytes(out) } finally { CFRelease(out) }
        } finally { CFRelease(input) }
    }

    /** The key from the keychain, made there the first time. Kept for the app's life. */
    private val key: SecKeyRef by lazy { if (inKeychain) find() ?: make() else make() }
    private fun privateKey(): SecKeyRef = key

    private fun find(): SecKeyRef? = memScoped {
        val tagData = cfData(tag.encodeToByteArray())
        val q = dict()
        CFDictionaryAddValue(q, kSecClass, kSecClassKey)
        CFDictionaryAddValue(q, kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
        CFDictionaryAddValue(q, kSecAttrApplicationTag, tagData)
        CFDictionaryAddValue(q, kSecReturnRef, kCFBooleanTrue)
        val out = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(q, out.ptr)
        CFRelease(q); CFRelease(tagData)
        lastFindStatus = status
        if (status == errSecSuccess) out.value?.reinterpret() else null
    }

    private fun make(): SecKeyRef = memScoped {
        val tagData = cfData(tag.encodeToByteArray())
        val bits = alloc<kotlinx.cinterop.IntVar>().apply { value = 256 }
        val size = CFNumberCreate(null, kCFNumberIntType, bits.ptr)
        val priv = dict()
        if (inKeychain) {
            CFDictionaryAddValue(priv, kSecAttrIsPermanent, kCFBooleanTrue)
            CFDictionaryAddValue(priv, kSecAttrApplicationTag, tagData)
            CFDictionaryAddValue(priv, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        }
        val p = dict()
        CFDictionaryAddValue(p, kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
        CFDictionaryAddValue(p, kSecAttrKeySizeInBits, size)
        CFDictionaryAddValue(p, kSecPrivateKeyAttrs, priv)
        val err = alloc<CFErrorRefVar>()
        val k = SecKeyCreateRandomKey(p, err.ptr)
        CFRelease(p); CFRelease(priv); CFRelease(size); CFRelease(tagData)
        // the keychain's own code, so a refusal says why (-25291: no keychain, as in the simulator suite)
        k ?: error("the keychain refused to make the key: " + (err.value?.let { CFErrorGetCode(it) } ?: "no code") + ", search said $lastFindStatus")
    }

    /** The keychain's answer to the last search for the key, for a refusal to name. */
    private var lastFindStatus = 0
}

private const val FORMAT: Byte = 1

private fun dict(): CFMutableDictionaryRef? =
    CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)

private fun cfData(b: ByteArray): CFDataRef? =
    if (b.isEmpty()) CFDataCreate(null, null, 0)
    else b.usePinned { CFDataCreate(null, it.addressOf(0).reinterpret(), b.size.toLong()) }

private fun bytes(d: CFDataRef): ByteArray {
    val n = CFDataGetLength(d).toInt()
    val out = ByteArray(n)
    if (n > 0) out.usePinned { memcpy(it.addressOf(0), CFDataGetBytePtr(d), n.toULong()) }
    return out
}

/**
 * The save file in the app's Application Support folder, private to the app; written whole and moved into place
 * by the system, so a crash never leaves half a file. [dir] defaults to `<Application Support>/ClavierTuner`.
 */
class IosSaveFile(dir: String? = null, private val name: String = "last-tuning.bin") : SaveFile {
    private val fm = NSFileManager.defaultManager
    private val dir: String = dir ?: run {
        val base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).firstOrNull() as? String ?: ""
        "$base/ClavierTuner"
    }
    private val path get() = "$dir/$name"

    private fun read(p: String): ByteArray? {
        val d = NSData.dataWithContentsOfFile(p) ?: return null
        val n = d.length.toInt()
        val out = ByteArray(n)
        if (n > 0) out.usePinned { memcpy(it.addressOf(0), d.bytes, d.length) }
        return out
    }

    override fun read(): ByteArray? = read(path)

    override fun writeAtomic(bytes: ByteArray) {
        fm.createDirectoryAtPath(dir, true, null, null)
        val d = if (bytes.isEmpty()) NSData() else bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        check(d.writeToFile(path, atomically = true)) { "could not write the save file" }
    }

    override fun delete() {
        fm.removeItemAtPath(path, null)
    }

    override fun setAside() {
        if (fm.fileExistsAtPath(path)) fm.moveItemAtPath(path, "$path.rejected-${(NSDate().timeIntervalSince1970 * 1000).toLong()}", null)
    }

    private fun setAsidePaths(): List<String> =
        fm.contentsOfDirectoryAtPath(dir, null).orEmpty().mapNotNull { it as? String }
            .filter { it.startsWith("$name.rejected-") }.sorted().map { "$dir/$it" }

    override fun readSetAside(): ByteArray? = setAsidePaths().lastOrNull()?.let { read(it) }

    override fun clearSetAside() { for (p in setAsidePaths()) fm.removeItemAtPath(p, null) }
}
