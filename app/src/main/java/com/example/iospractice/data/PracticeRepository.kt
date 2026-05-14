package com.example.iospractice.data

import android.content.Context
import org.json.JSONArray

/**
 * 只负责读取随包发布的本地 iOS 题库。
 *
 * 当前版本不做导入/同步，因此题库来源固定为 assets/ios.seed.json。
 */
class PracticeRepository(private val context: Context) {
    /** 将 Web 端导出的 seed JSON 转成 App 内部使用的题目模型。 */
    fun loadItems(): List<PracticeItem> {
        val raw = context.assets.open("ios.seed.json").bufferedReader().use { it.readText() }
        val array = JSONArray(raw)
        val items = buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: "ios-$index"
                val topic = obj.optString("topic")
                val difficulty = obj.optString("difficulty")
                val tags = listOf(topic, difficulty).filter { it.isNotBlank() }.joinToString(" · ")
                add(
                    PracticeItem(
                        id = id,
                        category = obj.optString("category", "iOS"),
                        question = obj.optString("question"),
                        answer = obj.optString("answer"),
                        tags = tags,
                        importedAt = obj.optLong("importedAt", 0L),
                        markD = obj.optBoolean("markD", true),
                        oralOneLiner = obj.optString("oralOneLiner").takeIf { it.isNotBlank() },
                    )
                )
            }
        }
        return items.filter { it.question.isNotBlank() }
    }
}
