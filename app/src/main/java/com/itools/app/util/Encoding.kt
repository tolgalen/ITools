package com.itools.app.util

import org.bouncycastle.crypto.Digest
import org.bouncycastle.crypto.digests.*
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.KeyParameter
import java.security.SecureRandom
import java.util.Base64

val secureRandom = SecureRandom()

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
fun String.hexToBytes(): ByteArray {
    val s = replace(Regex("[\\s:]"), "")
    require(s.length % 2 == 0 && s.matches(Regex("[0-9a-fA-F]*"))) { "Invalid hex" }
    return ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
fun ByteArray.toB64(): String = Base64.getEncoder().encodeToString(this)
fun ByteArray.toB64Url(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(this)
fun ByteArray.toBinary(): String = joinToString("") { (it.toInt() and 0xff).toString(2).padStart(8, '0') }

/** Decodifica Base64 estándar o URL-safe, con o sin relleno. */
fun decodeB64(s: String): ByteArray {
    val clean = s.trim().replace(Regex("\\s"), "").substringAfter("base64,")
    val std = clean.replace('-', '+').replace('_', '/')
    val padded = std + "=".repeat((4 - std.length % 4) % 4)
    return Base64.getDecoder().decode(padded)
}

enum class HashAlgo(val label: String, val make: () -> Digest) {
    MD5("MD5", { MD5Digest() }),
    SHA1("SHA1", { SHA1Digest() }),
    SHA256("SHA256", { SHA256Digest() }),
    SHA224("SHA224", { SHA224Digest() }),
    SHA512("SHA512", { SHA512Digest() }),
    SHA384("SHA384", { SHA384Digest() }),
    SHA3_256("SHA3-256", { SHA3Digest(256) }),
    SHA3_512("SHA3-512", { SHA3Digest(512) }),
    RIPEMD160("RIPEMD160", { RIPEMD160Digest() });
}

fun digest(algo: HashAlgo, data: ByteArray): ByteArray {
    val d = algo.make()
    d.update(data, 0, data.size)
    return ByteArray(d.digestSize).also { d.doFinal(it, 0) }
}

fun hmac(algo: HashAlgo, key: ByteArray, data: ByteArray): ByteArray {
    val m = HMac(algo.make())
    m.init(KeyParameter(key))
    m.update(data, 0, data.size)
    return ByteArray(m.macSize).also { m.doFinal(it, 0) }
}

enum class OutEncoding(val label: String) { HEX("Hex (base 16)"), BIN("Binary (base 2)"), B64("Base64"), B64URL("Base64url") }

fun ByteArray.encode(e: OutEncoding) = when (e) {
    OutEncoding.HEX -> toHex()
    OutEncoding.BIN -> toBinary()
    OutEncoding.B64 -> toB64()
    OutEncoding.B64URL -> toB64Url()
}

fun randomBytes(n: Int) = ByteArray(n).also { secureRandom.nextBytes(it) }

/** Base32 RFC 4648 (para OTP). */
object Base32 {
    private const val A = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    fun encode(b: ByteArray): String {
        val sb = StringBuilder(); var buf = 0; var bits = 0
        for (x in b) {
            buf = (buf shl 8) or (x.toInt() and 0xff); bits += 8
            while (bits >= 5) { sb.append(A[(buf shr (bits - 5)) and 31]); bits -= 5 }
        }
        if (bits > 0) sb.append(A[(buf shl (5 - bits)) and 31])
        return sb.toString()
    }
    fun decode(s: String): ByteArray {
        val c = s.uppercase().replace(Regex("[\\s=-]"), "")
        val out = java.io.ByteArrayOutputStream(); var buf = 0; var bits = 0
        for (ch in c) {
            val v = A.indexOf(ch); require(v >= 0) { "Invalid base32 char '$ch'" }
            buf = (buf shl 5) or v; bits += 5
            if (bits >= 8) { out.write((buf shr (bits - 8)) and 0xff); bits -= 8 }
        }
        return out.toByteArray()
    }
}
