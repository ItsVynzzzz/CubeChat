package com.example.cubechat.data

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class PrefsKeyValue(private val prefs: SharedPreferences) : KeyValue {
    override fun get(key: String): String? = prefs.getString(key, null)

    override fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}

/**
 * Enkripsi AES-256-GCM dengan kunci yang disimpan di Android Keystore (tidak bisa dibaca keluar dari HP).
 * Kalau kunci hilang (mis. setelah restore cadangan), password lama tidak bisa dibuka dan diperlakukan
 * sebagai tidak ada: pemain cukup login manual lagi dan diminta menyimpan ulang.
 */
class KeystoreVault : Vault {
    private val alias = "cubechat_vault_v1"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val existing = ks.getKey(alias, null)
        if (existing is SecretKey) return existing
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    override fun encrypt(plain: String): String? {
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key())
            val iv = c.iv
            val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
            Base64.getEncoder().encodeToString(iv + ct)
        } catch (e: Exception) {
            null
        }
    }

    override fun decrypt(token: String): String? {
        return try {
            val all = Base64.getDecoder().decode(token)
            if (all.size < 13) return null
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all.copyOfRange(0, 12)))
            String(c.doFinal(all.copyOfRange(12, all.size)), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
