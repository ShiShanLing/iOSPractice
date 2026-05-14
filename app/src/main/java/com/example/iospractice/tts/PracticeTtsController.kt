package com.example.iospractice.tts

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * Android TextToSpeech 的小封装。
 *
 * 这里集中处理 TTS 生命周期、引擎/语音选择、播放完成回调，以及朗读前的文本清洗。
 */
class PracticeTtsController(context: Context) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = TextToSpeech(appContext, this)
    private var onDone: (() -> Unit)? = null
    private var selectedVoiceName: String? = null

    var ready: Boolean = false
        private set

    var speaking: Boolean = false
        private set

    var onReady: (() -> Unit)? = null

    override fun onInit(status: Int) {
        val engine = tts ?: return
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            engine.language = Locale.CHINESE
            selectedVoiceName?.let { name ->
                engine.voices?.firstOrNull { it.name == name }?.let(engine::setVoice)
            }
            engine.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        mainHandler.post { speaking = true }
                    }

                    override fun onDone(utteranceId: String?) {
                        mainHandler.post {
                            speaking = false
                            onDone?.invoke()
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post { speaking = false }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        mainHandler.post { speaking = false }
                    }
                }
            )
            mainHandler.post { onReady?.invoke() }
        }
    }

    /** 系统已安装的 TTS 引擎，比如系统语音服务或 MultiTTS。 */
    fun engineOptions(): List<TtsOption> {
        return tts?.engines
            .orEmpty()
            .map { TtsOption(id = it.name, label = it.label) }
            .sortedBy { it.label.lowercase() }
    }

    fun currentEngineLabel(): String {
        val engine = tts ?: return "系统默认"
        val current = engine.defaultEngine
        return engine.engines.firstOrNull { it.name == current }?.label ?: current ?: "系统默认"
    }

    /** 切换 TTS 引擎会重建 TextToSpeech 实例，语音列表也会随之变化。 */
    fun selectEngine(packageName: String) {
        stop()
        tts?.shutdown()
        ready = false
        tts = TextToSpeech(appContext, this, packageName)
    }

    /** 当前引擎可用的语音；优先展示中文语音，没有中文时退回展示全部。 */
    fun voiceOptions(): List<TtsOption> {
        val engine = tts ?: return emptyList()
        val voices = engine.voices.orEmpty()
        val zhVoices = voices.filter { it.locale.language.equals("zh", ignoreCase = true) }
        return (zhVoices.ifEmpty { voices })
            .sortedWith(compareBy({ it.locale.toLanguageTag() }, { it.name }))
            .map { voice ->
                TtsOption(
                    id = voice.name,
                    label = "${voice.locale.toLanguageTag()} · ${voice.name}",
                )
            }
    }

    fun currentVoiceLabel(): String {
        val engine = tts ?: return "默认语音"
        val voice = engine.voice ?: return "默认语音"
        return "${voice.locale.toLanguageTag()} · ${voice.name}"
    }

    fun selectVoice(name: String) {
        val engine = tts ?: return
        val voice = engine.voices?.firstOrNull { it.name == name } ?: return
        selectedVoiceName = name
        engine.voice = voice
    }

    /** 朗读前会清洗代码块/函数行，避免把代码符号逐字念出来。 */
    fun speak(text: String, rate: Float, onDone: (() -> Unit)? = null): Boolean {
        val engine = tts
        val clean = cleanSpeechText(text)
        if (!ready || engine == null) {
            return false
        }
        if (clean.isBlank()) {
            onDone?.invoke()
            return true
        }
        this.onDone = onDone
        speaking = true
        engine.setSpeechRate(rate.coerceIn(0.6f, 1.4f))
        engine.speak(
            clean,
            TextToSpeech.QUEUE_FLUSH,
            Bundle(),
            UUID.randomUUID().toString(),
        )
        return true
    }

    fun stop() {
        onDone = null
        speaking = false
        tts?.stop()
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}

data class TtsOption(
    val id: String,
    val label: String,
)

fun questionSpeechText(question: String): String {
    return "题目：$question"
}

fun answerSpeechText(oralOneLiner: String?, answer: String): String {
    return listOf(
        oralOneLiner?.takeIf { it.isNotBlank() },
        "答案：",
        answer,
    ).filterNotNull().joinToString("\n")
}

/** 清洗 Markdown 和明显代码内容，保留适合口播的自然语言句子。 */
fun cleanSpeechText(raw: String): String {
    val withoutCodeBlocks = raw.replace(Regex("(?s)```.*?```"), "。")
    return withoutCodeBlocks
        .lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filterNot { looksLikeCodeLine(it) }
        .joinToString("。")
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("[*_>#\\-]+"), "")
        .replace(Regex("\\[[^]]*]\\([^)]*\\)"), "")
        .replace(Regex("\\s+"), " ")
        .replace(Regex("。+"), "。")
        .trim(' ', '。')
}

/** 粗略识别代码/函数签名行；宁可少读代码，也不要在唱题时念一大串符号。 */
private fun looksLikeCodeLine(line: String): Boolean {
    val lower = line.lowercase()
    if (line.length > 140 && countCodeSymbols(line) >= 4) return true
    if (Regex("""^\s*(import|package|class|struct|enum|interface|protocol|func|fun|let|var|const|return|if|else|for|while|switch|case|guard)\b""").containsMatchIn(lower)) {
        return true
    }
    if (Regex("""\b(export\s+)?(function|func|fun|class|struct|enum)\b""").containsMatchIn(lower)) {
        return true
    }
    if (Regex("""[A-Za-z_][A-Za-z0-9_]*\s*\([^)]*\)\s*(\{|->|:|;)?""").matches(line) && countCodeSymbols(line) >= 2) {
        return true
    }
    if (line.contains("{") || line.contains("}") || line.endsWith(";")) return true
    if (line.startsWith("//") || line.startsWith("/*") || line.startsWith("* ")) return true
    return false
}

private fun countCodeSymbols(line: String): Int {
    return line.count { it in "{}[]();=<>|" }
}
