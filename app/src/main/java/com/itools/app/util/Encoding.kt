package com.itools.app.util

import org.bouncycastle.crypto.Digest
import org.bouncycastle.crypto.digests.*
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.KeyParameter
import java.security.SecureRandom
import java.util.Base64

val secureRandom = SecureRandom()

private val HEX = "0123456789abcdef".toCharArray()
private val HEX_SEPARATORS = Regex("[\\s:]")
private val WHITESPACE = Regex("\\s")

fun ByteArray.toHex(): String {
    val out = CharArray(size * 2)
    forEachIndexed { i, b ->
        val v = b.toInt() and 0xff
        out[i * 2] = HEX[v ushr 4]; out[i * 2 + 1] = HEX[v and 0x0f]
    }
    return String(out)
}
private fun hexDigit(c: Char): Int = when (c) {
    in '0'..'9' -> c - '0'; in 'a'..'f' -> c - 'a' + 10; in 'A'..'F' -> c - 'A' + 10; else -> -1
}
fun String.hexToBytes(): ByteArray {
    val s = replace(HEX_SEPARATORS, "")
    require(s.length % 2 == 0) { "Invalid hex" }
    return ByteArray(s.length / 2) {
        val hi = hexDigit(s[it * 2]); val lo = hexDigit(s[it * 2 + 1])
        require(hi >= 0 && lo >= 0) { "Invalid hex" }
        ((hi shl 4) or lo).toByte()
    }
}
fun ByteArray.toB64(): String = Base64.getEncoder().encodeToString(this)
fun ByteArray.toB64Url(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(this)
fun ByteArray.toBinary(): String {
    val out = CharArray(size * 8)
    forEachIndexed { i, b -> for (bit in 0 until 8) out[i * 8 + bit] = if ((b.toInt() shr (7 - bit)) and 1 == 1) '1' else '0' }
    return String(out)
}

/** Decodes standard or URL-safe Base64, with or without padding. */
fun decodeB64(s: String): ByteArray {
    val clean = s.trim().replace(WHITESPACE, "").substringAfter("base64,")
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

/** Base32 RFC 4648 (for OTP). */
object Base32 {
    private const val A = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val IGNORED = Regex("[\\s=-]")
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
        val c = s.uppercase().replace(IGNORED, "")
        val out = java.io.ByteArrayOutputStream(); var buf = 0; var bits = 0
        for (ch in c) {
            val v = A.indexOf(ch); require(v >= 0) { "Invalid base32 char '$ch'" }
            buf = (buf shl 5) or v; bits += 5
            if (bits >= 8) { out.write((buf shr (bits - 8)) and 0xff); bits -= 8 }
        }
        return out.toByteArray()
    }
}
