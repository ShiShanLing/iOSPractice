package com.example.iospractice.data

import android.content.Context

/**
 * 本地学习进度存储。
 *
 * 使用 SharedPreferences 是为了保持单机版足够轻：每个日期按 yyyy-MM-dd 前缀拆成几组键，
 * 方便只读当天记录，也方便学习日历扫描所有已有日期。
 */
class PracticeProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences("ios_practice_progress", Context.MODE_PRIVATE)

    /** 读取某一天的今日 5 题与记忆进度；不存在时返回 null。 */
    fun readDailyRecord(date: String): DailyPracticeRecord? {
        val itemIds = prefs.getString("${date}_item_ids", null)
            ?.split('|')
            ?.filter { it.isNotBlank() }
            ?: return null
        val rememberedIds = prefs.getStringSet("${date}_remembered_ids", emptySet()).orEmpty()
        val attempts = prefs.getInt("${date}_attempts", 0)
        val completedAt = prefs.getLong("${date}_completed_at", 0L).takeIf { it > 0L }
        return DailyPracticeRecord(
            date = date,
            itemIds = itemIds,
            rememberedIds = rememberedIds,
            attempts = attempts,
            completedAt = completedAt,
        )
    }

    /** 覆盖保存某一天的学习记录。 */
    fun saveDailyRecord(record: DailyPracticeRecord) {
        prefs.edit()
            .putString("${record.date}_item_ids", record.itemIds.joinToString("|"))
            .putStringSet("${record.date}_remembered_ids", record.rememberedIds)
            .putInt("${record.date}_attempts", record.attempts)
            .putLong("${record.date}_completed_at", record.completedAt ?: 0L)
            .apply()
    }

    /** 读取所有日期记录，供学习日历展示。 */
    fun readAllDailyRecords(): Map<String, DailyPracticeRecord> {
        return prefs.all.keys
            .asSequence()
            .filter { it.endsWith("_item_ids") }
            .map { it.removeSuffix("_item_ids") }
            .distinct()
            .mapNotNull { date -> readDailyRecord(date)?.let { date to it } }
            .toMap()
    }
}
