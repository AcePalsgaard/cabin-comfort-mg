package dk.Android.Cpmonster.mgmanager.saic

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Port of saic_ismart_client_ng crypto (net/crypto.py + crypto_utils.py).
 * Requests and responses are AES-CBC encrypted (hex) with keys derived from MD5 hashes,
 * and every request is signed with an HMAC-SHA256 "verification string".
 */
object SaicCrypto {

    fun md5Hex(s: String) = digest("MD5", s)
    fun sha1Hex(s: String) = digest("SHA-1", s)
    fun sha256Hex(s: String) = digest("SHA-256", s)

    private fun digest(alg: String, s: String): String =
        MessageDigest.getInstance(alg).digest(s.toByteArray(Charsets.UTF_8)).encodeHex()

    fun aesEncryptHex(plain: String, keyHex: String, ivHex: String): String {
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyHex.decodeHex(), "AES"), IvParameterSpec(ivHex.decodeHex()))
        return c.doFinal(plain.toByteArray(Charsets.UTF_8)).encodeHex()
    }

    fun aesDecryptHex(cipherHex: String, keyHex: String, ivHex: String): String {
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyHex.decodeHex(), "AES"), IvParameterSpec(ivHex.decodeHex()))
        return String(c.doFinal(cipherHex.decodeHex()), Charsets.UTF_8)
    }

    private fun requestKey(path: String, tenantId: String, token: String, ts: String, contentType: String) =
        md5Hex(md5Hex(path + tenantId + token + "app") + ts + "1" + contentType)

    /** Returns (encrypted body hex or "", APP-VERIFICATION-STRING). */
    fun encryptRequest(
        path: String,
        tenantId: String,
        token: String,
        ts: String,
        contentType: String,
        plainBody: String,
    ): Pair<String, String> {
        val key = requestKey(path, tenantId, token, ts, contentType)
        val body = plainBody.trim()
        val enc = if (body.isNotEmpty()) aesEncryptHex(body, key, md5Hex(ts)) else ""
        val hmacKey = md5Hex(key + ts)
        val msg = path + tenantId + token + "app" + ts + "1" + contentType + enc
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacKey.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return enc to mac.doFinal(msg.toByteArray(Charsets.UTF_8)).encodeHex()
    }

    fun decryptResponse(bodyHex: String, appSendDate: String, originalContentType: String): String =
        aesDecryptHex(bodyHex.trim(), md5Hex(appSendDate + "1" + originalContentType), md5Hex(appSendDate))

    internal fun ByteArray.encodeHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xff
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0f])
        }
        return sb.toString()
    }

    internal fun String.decodeHex(): ByteArray =
        ByteArray(length / 2) { i -> ((Character.digit(this[i * 2], 16) shl 4) + Character.digit(this[i * 2 + 1], 16)).toByte() }

    private val HEX = "0123456789abcdef".toCharArray()
}
