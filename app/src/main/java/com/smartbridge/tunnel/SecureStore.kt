package com.smartbridge.tunnel

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 加密存储：服务器凭证用 Android Keystore + AES-GCM 加密
 * 用户看不到密码，只需选择城市
 */
object SecureStore {
    private const val PREFS = "bridge_secure"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "bridge_key"
    private const val GCM_TAG_LEN = 128

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(ctx: Context, plaintext: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val enc = cipher.doFinal(plaintext.toByteArray())
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
               Base64.encodeToString(enc, Base64.NO_WRAP)
    }

    fun decrypt(ctx: Context, encoded: String): String {
        val parts = encoded.split(":")
        if (parts.size != 2) return ""
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val enc = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LEN, iv))
        return String(cipher.doFinal(enc))
    }

    fun save(ctx: Context, key: String, value: String) {
        prefs(ctx).edit().putString(key, encrypt(ctx, value)).apply()
    }

    fun load(ctx: Context, key: String, default: String = ""): String {
        val raw = prefs(ctx).getString(key, null) ?: return default
        return try { decrypt(ctx, raw) } catch (_: Exception) { default }
    }
}
