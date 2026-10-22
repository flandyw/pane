package app.pane.rdp

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator

class PasswordCipherTest {
    private val profile = Profile(host = "server.ts.net", username = "user", fingerprint = "AB".repeat(32))
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test fun passwordRoundTripsWithFreshIvAndNormalizedIdentity() {
        val password = "space / : and ü 🔑"
        val saved = PasswordCipher.encrypt(password, profile, key)
        assertEquals(password, PasswordCipher.decrypt(saved, profile, key))
        assertNotEquals(saved, PasswordCipher.encrypt(password, profile, key))
        assertFalse(String(Base64.getDecoder().decode(saved), Charsets.UTF_8).contains(password))
        assertEquals(password, PasswordCipher.decrypt(saved,
            profile.copy(host = "SERVER.ts.net", fingerprint = profile.fingerprint.lowercase().chunked(2).joinToString(":")), key))
        assertEquals(password, PasswordCipher.decrypt(saved, profile.copy(audio = false, quality = Quality.FAST), key))
    }

    @Test fun rejectsOtherServersAccountsCertificatesKeysAndTampering() {
        val saved = PasswordCipher.encrypt("secret", profile, key)
        listOf(profile.copy(host = "other.ts.net"), profile.copy(username = "other"),
            profile.copy(fingerprint = "CD".repeat(32))).forEach {
            assertThrows(AEADBadTagException::class.java) { PasswordCipher.decrypt(saved, it, key) }
        }
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertThrows(AEADBadTagException::class.java) { PasswordCipher.decrypt(saved, profile, otherKey) }
        val bytes = Base64.getDecoder().decode(saved)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertThrows(AEADBadTagException::class.java) {
            PasswordCipher.decrypt(Base64.getEncoder().encodeToString(bytes), profile, key)
        }
        listOf("not base64!", "", "AA==").forEach {
            assertThrows(IllegalArgumentException::class.java) { PasswordCipher.decrypt(it, profile, key) }
        }
    }
}
