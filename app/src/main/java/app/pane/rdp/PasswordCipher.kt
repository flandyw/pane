package app.pane.rdp

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object PasswordCipher {
    private const val ALIAS = "pane.password"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return store.getKey(ALIAS, null) as? SecretKey ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    // Bind the secret to this server, account and certificate so edits cannot send it elsewhere.
    private fun identity(profile: Profile) =
        listOf(profile.host.lowercase(), profile.username, profile.normalizedPin().lowercase()).joinToString("\u0000").toByteArray(Charsets.UTF_8)

    fun encrypt(password: String, profile: Profile, key: SecretKey = key()): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(identity(profile))
        return Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(password.toByteArray(Charsets.UTF_8)))
    }

    fun decrypt(value: String, profile: Profile, key: SecretKey = key()): String {
        val bytes = Base64.getDecoder().decode(value)
        require(bytes.size >= 12 + 16)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(identity(profile))
        return String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
    }
}
