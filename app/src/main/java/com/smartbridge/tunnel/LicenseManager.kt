package com.smartbridge.tunnel

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * LicenseManager — ClawClaw.tech 离线激活码验证
 *
 * 算法: SHA-256(machineId + SALT) 前16位hex → 分4组 XXXX-XXXX-XXXX-XXXX
 * 与 ClawClaw.tech 其他APP（狗狗在家Pro等）使用相同体系
 *
 * 激活码生成（管理端）:
 *   val machineId = ... // 从APP获取
 *   val code = LicenseManager.generateCode(machineId)
 */
object LicenseManager {
    private const val SALT = "sm4rt-br1dge-pr0-2026-cl4w"
    private const val PREFS = "bridge_license"
    private const val KEY_CODE = "license_code"
    private const val KEY_MACHINE = "machine_id"
    private const val KEY_ACTIVATED_AT = "activated_at"
    private const val KEY_VERSION = "license_version"

    // AES 加密用的 key（固定，用于加密存储激活信息）
    private val STORAGE_KEY = "ClawClaw2026!".padEnd(32, '0').toByteArray()

    /**
     * 获取本机唯一标识 (machineId)
     * 使用 Android ID + 设备硬件信息组合哈希
     */
    fun getMachineId(context: Context): String {
        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        val combined = androidId + android.os.Build.MANUFACTURER + android.os.Build.MODEL
        return sha256(combined).substring(0, 16).uppercase()
    }

    /**
     * 生成激活码（管理端使用）
     */
    fun generateCode(machineId: String): String {
        val hash = sha256(machineId + SALT).substring(0, 16).uppercase()
        return hash.chunked(4).joinToString("-")
    }

    /**
     * 验证激活码是否匹配本机
     */
    fun verify(code: String, machineId: String): Boolean {
        val cleanCode = code.replace("-", "").trim().uppercase()
        if (cleanCode.length != 16) return false
        val expected = sha256(machineId + SALT).substring(0, 16).uppercase()
        return cleanCode == expected
    }

    /**
     * 激活：保存激活码到加密存储
     */
    fun activate(context: Context, code: String): Boolean {
        val machineId = getMachineId(context)
        if (!verify(code, machineId)) return false

        val data = "$code|$machineId|${System.currentTimeMillis()}|2.0.0"
        val encrypted = encrypt(data)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CODE, encrypted)
            .apply()
        return true
    }

    /**
     * 检查是否已激活
     */
    fun isActivated(context: Context): Boolean {
        val encrypted = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CODE, null) ?: return false
        return try {
            val data = decrypt(encrypted)
            val parts = data.split("|")
            if (parts.size < 2) return false
            val code = parts[0]
            val machineId = parts[1]
            verify(code, machineId)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 获取激活信息（用于显示）
     */
    fun getLicenseInfo(context: Context): LicenseInfo? {
        val encrypted = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CODE, null) ?: return null
        return try {
            val data = decrypt(encrypted)
            val parts = data.split("|")
            if (parts.size < 4) return null
            LicenseInfo(
                code = parts[0],
                machineId = parts[1],
                activatedAt = parts[2].toLongOrNull() ?: 0L,
                version = parts[3]
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 退出激活
     */
    fun deactivate(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_CODE).apply()
    }

    // === 内部工具 ===

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(STORAGE_KEY, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        val iv = cipher.iv
        val enc = cipher.doFinal(plaintext.toByteArray())
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(enc, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val parts = encoded.split(":")
        if (parts.size != 2) throw IllegalArgumentException("Invalid format")
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val enc = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(STORAGE_KEY, "AES")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(enc))
    }

    data class LicenseInfo(
        val code: String,
        val machineId: String,
        val activatedAt: Long,
        val version: String
    )
}
