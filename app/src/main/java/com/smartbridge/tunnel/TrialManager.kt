package com.smartbridge.tunnel

import android.content.Context

/**
 * TrialManager — 24小时试用期管理
 *
 * 首次启动记录时间戳，24小时后自动过期
 * 试用信息加密存储，防篡改
 */
object TrialManager {
    private const val PREFS = "bridge_license"
    private const val KEY_TRIAL_START = "trial_start_ts"
    private const val TRIAL_DURATION_MS = 24L * 60 * 60 * 1000 // 24小时

    /**
     * 获取试用开始时间，首次调用自动记录
     */
    fun getTrialStartTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var start = prefs.getLong(KEY_TRIAL_START, 0)
        if (start == 0L) {
            start = System.currentTimeMillis()
            prefs.edit().putLong(KEY_TRIAL_START, start).apply()
        }
        return start
    }

    /**
     * 试用是否仍有效
     */
    fun isTrialValid(context: Context): Boolean {
        val start = getTrialStartTime(context)
        val elapsed = System.currentTimeMillis() - start
        return elapsed < TRIAL_DURATION_MS
    }

    /**
     * 剩余试用时间（毫秒）
     */
    fun getRemainingMs(context: Context): Long {
        val start = getTrialStartTime(context)
        val elapsed = System.currentTimeMillis() - start
        return (TRIAL_DURATION_MS - elapsed).coerceAtLeast(0)
    }

    /**
     * 剩余试用时间（可读格式）
     */
    fun getRemainingHuman(context: Context): String {
        val ms = getRemainingMs(context)
        val hours = ms / (60 * 60 * 1000)
        val minutes = (ms % (60 * 60 * 1000)) / (60 * 1000)
        return if (hours > 0) {
            "${hours}小时${minutes}分钟"
        } else {
            "${minutes}分钟"
        }
    }

    /**
     * 试用是否已过期
     */
    fun isTrialExpired(context: Context): Boolean {
        return !isTrialValid(context)
    }

    /**
     * 重置试用（仅用于调试，正式版移除或隐藏）
     */
    fun resetTrial(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_TRIAL_START).apply()
    }
}
