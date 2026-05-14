package com.example.iospractice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.iospractice.data.DailyPracticeRecord
import com.example.iospractice.data.PracticeItem
import com.example.iospractice.data.PracticeProgressStore
import com.example.iospractice.data.PracticeRepository
import com.example.iospractice.tts.PracticeTtsController
import com.example.iospractice.tts.TtsOption
import com.example.iospractice.tts.answerSpeechText
import com.example.iospractice.tts.questionSpeechText
import com.example.iospractice.ui.theme.IOSPracticeTheme
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.max

private val GlassSurface = Color(0xEFFFFFFF)
private val GlassSurfaceStrong = Color(0xF7FFFFFF)
private val GlassBlue = Color(0xE8EAF4FF)
private val GlassMint = Color(0xE8E9FFF1)
private val GlassStroke = Color(0x8FFFFFFF)

private enum class AppTab {
    Today,
    Chant,
    Quiz,
    Calendar,
    Search,
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IOSPracticeTheme {
                PracticeApp()
            }
        }
    }
}

@Composable
private fun PracticeApp() {
    val context = LocalContext.current
    val repository = remember { PracticeRepository(context.applicationContext) }
    val progressStore = remember { PracticeProgressStore(context.applicationContext) }
    val ttsController = remember { PracticeTtsController(context.applicationContext) }
    val today = remember { LocalDate.now().format(DateTimeFormatter.ISO_DATE) }

    var allItems by remember { mutableStateOf<List<PracticeItem>>(emptyList()) }
    var dailyRecord by remember { mutableStateOf<DailyPracticeRecord?>(null) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var selectedTab by remember { mutableStateOf(AppTab.Today) }
    var isSpeaking by remember { mutableStateOf(false) }
    var chantMode by remember { mutableStateOf(false) }
    var chantPhase by remember { mutableStateOf("待机") }
    var speechRate by remember { mutableStateOf(0.9f) }
    var engineOptions by remember { mutableStateOf<List<TtsOption>>(emptyList()) }
    var voiceOptions by remember { mutableStateOf<List<TtsOption>>(emptyList()) }
    var currentEngineLabel by remember { mutableStateOf("系统默认") }
    var currentVoiceLabel by remember { mutableStateOf("默认语音") }
    var dailyRecords by remember { mutableStateOf<Map<String, DailyPracticeRecord>>(emptyMap()) }

    // TTS 引擎和语音依赖系统服务，初始化完成后再刷新一次可选项。
    fun refreshTtsOptions() {
        engineOptions = ttsController.engineOptions()
        voiceOptions = ttsController.voiceOptions()
        currentEngineLabel = ttsController.currentEngineLabel()
        currentVoiceLabel = ttsController.currentVoiceLabel()
    }

    DisposableEffect(Unit) {
        onDispose { ttsController.shutdown() }
    }

    LaunchedEffect(Unit) {
        ttsController.onReady = { refreshTtsOptions() }
        delay(600)
        refreshTtsOptions()
        val loaded = repository.loadItems()
        allItems = loaded
        val existing = progressStore.readDailyRecord(today)
        dailyRecord = existing ?: createDailyRecord(today, loaded).also(progressStore::saveDailyRecord)
        dailyRecords = progressStore.readAllDailyRecords()
    }

    val record = dailyRecord
    // 今日 5 题只保存 id；展示时从全量题库映射回题目，避免题库内容重复存储。
    val dailyItems = remember(allItems, record) {
        if (record == null) {
            emptyList()
        } else {
            val byId = allItems.associateBy { it.id }
            record.itemIds.mapNotNull { byId[it] }
        }
    }

    LaunchedEffect(dailyItems.size) {
        currentIndex = currentIndex.coerceIn(0, max(0, dailyItems.lastIndex))
    }

    fun stopSpeech() {
        ttsController.stop()
        isSpeaking = false
        chantMode = false
        chantPhase = "待机"
    }

    /** 单次播放题目或答案；不会进入自动循环。 */
    fun speakOnce(item: PracticeItem, answer: Boolean) {
        chantMode = false
        chantPhase = if (answer) "答案" else "题目"
        isSpeaking = true
        val text = if (answer) {
            answerSpeechText(item.oralOneLiner, item.answer)
        } else {
            questionSpeechText(item.question)
        }
        val started = ttsController.speak(text, speechRate) {
            isSpeaking = false
            chantPhase = "待机"
        }
        if (!started) {
            isSpeaking = false
            chantPhase = "语音引擎初始化中，请稍后再试"
        }
    }

    /**
     * 唱题循环：题目 -> 答案 -> 下一题。
     *
     * TextToSpeech 的完成回调回来后再推进，确保几段语音不会重叠播放。
     */
    fun playChant(index: Int, answer: Boolean) {
        val item = dailyItems.getOrNull(index)
        if (!chantMode || item == null) {
            stopSpeech()
            return
        }
        currentIndex = index
        chantPhase = if (answer) "正在播放答案" else "正在播放题目"
        isSpeaking = true
        val text = if (answer) {
            answerSpeechText(item.oralOneLiner, item.answer)
        } else {
            questionSpeechText(item.question)
        }
        val started = ttsController.speak(text, speechRate) {
            if (!chantMode || dailyItems.isEmpty()) {
                isSpeaking = false
                return@speak
            }
            if (answer) {
                playChant((index + 1) % dailyItems.size, answer = false)
            } else {
                playChant(index, answer = true)
            }
        }
        if (!started) {
            isSpeaking = false
            chantMode = false
            chantPhase = "语音引擎初始化中，请稍后再试"
        }
    }

    /** 开关唱题模式，并在进入时跳到独立的唱题页。 */
    fun toggleChant() {
        if (chantMode) {
            stopSpeech()
            return
        }
        if (dailyItems.isEmpty()) return
        selectedTab = AppTab.Chant
        chantMode = true
        playChant(currentIndex.coerceIn(0, dailyItems.lastIndex), answer = false)
    }

    /** 轮换系统 TTS 引擎；切换后需等待新引擎初始化并重新读取语音列表。 */
    fun cycleEngine() {
        if (engineOptions.isEmpty()) return
        val currentEngineIndex = engineOptions.indexOfFirst { it.label == currentEngineLabel }
        val next = engineOptions[(currentEngineIndex + 1).floorMod(engineOptions.size)]
        stopSpeech()
        ttsController.selectEngine(next.id)
        currentEngineLabel = next.label
        currentVoiceLabel = "语音加载中"
        voiceOptions = emptyList()
    }

    /** 在当前 TTS 引擎下轮换语音，例如 MultiTTS 提供的不同音色。 */
    fun cycleVoice() {
        if (voiceOptions.isEmpty()) return
        val currentVoiceIndex = voiceOptions.indexOfFirst { it.label == currentVoiceLabel }
        val next = voiceOptions[(currentVoiceIndex + 1).floorMod(voiceOptions.size)]
        stopSpeech()
        ttsController.selectVoice(next.id)
        refreshTtsOptions()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            PracticeTopBar(
                total = allItems.size,
                remembered = record?.rememberedIds?.size ?: 0,
                dailyTotal = record?.itemIds?.size ?: 0,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(innerPadding),
        ) {
            when {
                allItems.isEmpty() -> EmptyState()
                selectedTab == AppTab.Today -> TodayPracticeScreen(
                    items = dailyItems,
                    record = record,
                    currentIndex = currentIndex,
                    onIndexChange = { currentIndex = it.coerceIn(0, max(0, dailyItems.lastIndex)) },
                    onRemembered = { item ->
                        val updated = markRemembered(record, item)
                        dailyRecord = updated
                        progressStore.saveDailyRecord(updated)
                        dailyRecords = progressStore.readAllDailyRecords()
                    },
                    onForgotten = {
                        val updated = markForgotten(record)
                        dailyRecord = updated
                        progressStore.saveDailyRecord(updated)
                        dailyRecords = progressStore.readAllDailyRecords()
                        if (dailyItems.isNotEmpty()) {
                            currentIndex = (currentIndex + 1) % dailyItems.size
                        }
                    },
                )
                selectedTab == AppTab.Chant -> ChantPracticeScreen(
                    items = dailyItems,
                    currentIndex = currentIndex,
                    onIndexChange = { currentIndex = it.coerceIn(0, max(0, dailyItems.lastIndex)) },
                    isSpeaking = isSpeaking,
                    chantMode = chantMode,
                    chantPhase = chantPhase,
                    speechRate = speechRate,
                    engineOptions = engineOptions,
                    voiceOptions = voiceOptions,
                    currentEngineLabel = currentEngineLabel,
                    currentVoiceLabel = currentVoiceLabel,
                    onSpeechRateChange = { speechRate = it.coerceIn(0.6f, 1.4f) },
                    onCycleEngine = ::cycleEngine,
                    onCycleVoice = ::cycleVoice,
                    onRefreshVoices = ::refreshTtsOptions,
                    onSpeakQuestion = { speakOnce(it, answer = false) },
                    onSpeakAnswer = { speakOnce(it, answer = true) },
                    onStopSpeech = ::stopSpeech,
                    onToggleChant = ::toggleChant,
                )
                selectedTab == AppTab.Quiz -> QuizPracticeScreen(
                    allItems = allItems,
                    records = dailyRecords,
                    today = today,
                )
                selectedTab == AppTab.Calendar -> LearningCalendarScreen(
                    today = today,
                    records = dailyRecords,
                )
                else -> SearchPracticeScreen(allItems)
            }

            PracticeBottomBar(
                selectedTab = selectedTab,
                onTabSelected = {
                    if (it != AppTab.Chant) stopSpeech()
                    selectedTab = it
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun PracticeTopBar(
    total: Int,
    remembered: Int,
    dailyTotal: Int,
) {
    Surface(
        color = GlassSurface,
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "刷题",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "本地题库 $total 题",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "今日 $remembered / $dailyTotal",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun PracticeBottomBar(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = GlassSurface,
            tonalElevation = 0.dp,
            shadowElevation = 14.dp,
            border = BorderStroke(1.dp, GlassStroke),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppTab.entries.forEach { tab ->
                    FloatingTabItem(
                        tab = tab,
                        selected = selectedTab == tab,
                        onClick = { onTabSelected(tab) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingTabItem(
    tab: AppTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    val containerColor = if (selected) GlassBlue else Color.Transparent
    val textColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier
            .height(58.dp)
            .clip(shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = shape,
        color = containerColor,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            TabIcon(tab = tab, selected = selected)
            Text(
                text = tabLabel(tab),
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

private fun tabLabel(tab: AppTab): String = when (tab) {
    AppTab.Today -> "刷题"
    AppTab.Chant -> "唱题"
    AppTab.Quiz -> "小测"
    AppTab.Calendar -> "日历"
    AppTab.Search -> "搜索"
}

@Composable
private fun TabIcon(tab: AppTab, selected: Boolean) {
    val color = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Canvas(modifier = Modifier.height(24.dp).fillMaxWidth()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val stroke = Stroke(width = 2.3f)
        when (tab) {
            AppTab.Today -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(cx - 10f, cy - 9f),
                    size = Size(20f, 18f),
                    cornerRadius = CornerRadius(7f, 7f),
                    style = stroke,
                )
                drawLine(color, Offset(cx, cy - 9f), Offset(cx, cy + 9f), strokeWidth = 2.3f)
                drawLine(color, Offset(cx - 7f, cy - 4f), Offset(cx - 2f, cy - 4f), strokeWidth = 2.3f)
                drawLine(color, Offset(cx + 2f, cy - 4f), Offset(cx + 7f, cy - 4f), strokeWidth = 2.3f)
            }
            AppTab.Chant -> {
                drawLine(color, Offset(cx - 9f, cy - 5f), Offset(cx - 9f, cy + 5f), strokeWidth = 2.6f)
                drawLine(color, Offset(cx - 3f, cy - 9f), Offset(cx - 3f, cy + 9f), strokeWidth = 2.6f)
                drawLine(color, Offset(cx + 3f, cy - 6f), Offset(cx + 3f, cy + 6f), strokeWidth = 2.6f)
                drawLine(color, Offset(cx + 9f, cy - 11f), Offset(cx + 9f, cy + 11f), strokeWidth = 2.6f)
            }
            AppTab.Quiz -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(cx - 9f, cy - 10f),
                    size = Size(18f, 20f),
                    cornerRadius = CornerRadius(7f, 7f),
                    style = stroke,
                )
                drawLine(color, Offset(cx - 4f, cy - 1f), Offset(cx - 1f, cy + 3f), strokeWidth = 2.4f)
                drawLine(color, Offset(cx - 1f, cy + 3f), Offset(cx + 6f, cy - 5f), strokeWidth = 2.4f)
                drawLine(color, Offset(cx - 4f, cy + 8f), Offset(cx + 5f, cy + 8f), strokeWidth = 2.2f)
            }
            AppTab.Calendar -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(cx - 10f, cy - 10f),
                    size = Size(20f, 20f),
                    cornerRadius = CornerRadius(7f, 7f),
                    style = stroke,
                )
                drawLine(color, Offset(cx - 10f, cy - 4f), Offset(cx + 10f, cy - 4f), strokeWidth = 2.3f)
                drawLine(color, Offset(cx - 4f, cy - 12f), Offset(cx - 4f, cy - 7f), strokeWidth = 2.3f)
                drawLine(color, Offset(cx + 4f, cy - 12f), Offset(cx + 4f, cy - 7f), strokeWidth = 2.3f)
                drawCircle(color, radius = 1.5f, center = Offset(cx - 4f, cy + 2f))
                drawCircle(color, radius = 1.5f, center = Offset(cx + 4f, cy + 2f))
                drawCircle(color, radius = 1.5f, center = Offset(cx - 4f, cy + 7f))
                drawCircle(color, radius = 1.5f, center = Offset(cx + 4f, cy + 7f))
            }
            AppTab.Search -> {
                drawCircle(color, radius = 7f, center = Offset(cx - 2f, cy - 2f), style = stroke)
                drawLine(color, Offset(cx + 4f, cy + 4f), Offset(cx + 10f, cy + 10f), strokeWidth = 2.8f)
            }
        }
    }
}

@Composable
private fun LearningCalendarScreen(
    today: String,
    records: Map<String, DailyPracticeRecord>,
) {
    var month by remember { mutableStateOf(YearMonth.from(LocalDate.parse(today))) }
    val days = remember(month, records) { calendarCells(month, records) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = GlassSurface),
        border = BorderStroke(1.dp, GlassStroke),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${month.year} 年 ${month.monthValue} 月",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "学习记录",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { month = month.minusMonths(1) },
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("上月")
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { month = YearMonth.from(LocalDate.parse(today)) },
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("本月")
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { month = month.plusMonths(1) },
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("下月")
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach { week ->
                Text(
                    text = week,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        days.chunked(7).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { cell ->
                    CalendarDayCell(
                        cell = cell,
                        isToday = cell.date == today,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Text(
            text = "完成日会高亮；日期下方显示已记住 / 当日题数。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CalendarDayCell(
    cell: CalendarCell,
    isToday: Boolean,
    modifier: Modifier = Modifier,
) {
    val record = cell.record
    val total = record?.itemIds?.size ?: 0
    val remembered = record?.rememberedIds?.size ?: 0
    val done = total > 0 && remembered >= total
    val containerColor = when {
        done -> MaterialTheme.colorScheme.primaryContainer
        total > 0 -> MaterialTheme.colorScheme.secondaryContainer
        isToday -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        done -> MaterialTheme.colorScheme.onPrimaryContainer
        total > 0 -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier.height(58.dp),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        tonalElevation = if (isToday || total > 0) 1.dp else 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (cell.inMonth) cell.day.toString() else "",
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                text = if (total > 0) "$remembered/$total" else "",
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "没有读取到题库",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "请确认 app/src/main/assets/ios.seed.json 已存在。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 今日刷题页：负责回答、查看参考答案、标记记住/没记住，不承载唱题设置。 */
@Composable
private fun TodayPracticeScreen(
    items: List<PracticeItem>,
    record: DailyPracticeRecord?,
    currentIndex: Int,
    onIndexChange: (Int) -> Unit,
    onRemembered: (PracticeItem) -> Unit,
    onForgotten: () -> Unit,
) {
    val currentItem = items.getOrNull(currentIndex)
    val rememberedCount = record?.rememberedIds?.size ?: 0
    val total = record?.itemIds?.size ?: 0
    val complete = total > 0 && rememberedCount >= total

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DailyStatusCard(
            remembered = rememberedCount,
            total = total,
            attempts = record?.attempts ?: 0,
            complete = complete,
        )

        if (currentItem == null) {
            Text("今日题目生成中...")
            return@Column
        }

        PracticeCard(
            item = currentItem,
            indexText = "${currentIndex + 1} / ${items.size}",
            remembered = record?.rememberedIds?.contains(currentItem.id) == true,
            onRemembered = { onRemembered(currentItem) },
            onForgotten = onForgotten,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = items.size > 1,
                onClick = {
                    val next = if (currentIndex <= 0) items.lastIndex else currentIndex - 1
                    onIndexChange(next)
                },
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("上一题")
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = items.size > 1,
                onClick = { onIndexChange((currentIndex + 1) % items.size) },
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("下一题")
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = items.size > 1,
                onClick = {
                    val candidates = items.indices.filter { it != currentIndex }
                    onIndexChange(candidates.random())
                },
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("随机")
            }
        }
    }
}

/** 独立唱题页：内容可滚动，上一题/下一题固定在底部。 */
@Composable
private fun ChantPracticeScreen(
    items: List<PracticeItem>,
    currentIndex: Int,
    onIndexChange: (Int) -> Unit,
    isSpeaking: Boolean,
    chantMode: Boolean,
    chantPhase: String,
    speechRate: Float,
    engineOptions: List<TtsOption>,
    voiceOptions: List<TtsOption>,
    currentEngineLabel: String,
    currentVoiceLabel: String,
    onSpeechRateChange: (Float) -> Unit,
    onCycleEngine: () -> Unit,
    onCycleVoice: () -> Unit,
    onRefreshVoices: () -> Unit,
    onSpeakQuestion: (PracticeItem) -> Unit,
    onSpeakAnswer: (PracticeItem) -> Unit,
    onStopSpeech: () -> Unit,
    onToggleChant: () -> Unit,
) {
    val currentItem = items.getOrNull(currentIndex)

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        if (currentItem == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
            ) {
                EmptyChantState()
            }
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 118.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SpeechControlCard(
                isSpeaking = isSpeaking,
                chantMode = chantMode,
                chantPhase = chantPhase,
                speechRate = speechRate,
                engineOptions = engineOptions,
                voiceOptions = voiceOptions,
                currentEngineLabel = currentEngineLabel,
                currentVoiceLabel = currentVoiceLabel,
                onSpeechRateChange = onSpeechRateChange,
                onCycleEngine = onCycleEngine,
                onCycleVoice = onCycleVoice,
                onRefreshVoices = onRefreshVoices,
                onToggleChant = onToggleChant,
                onStopSpeech = onStopSpeech,
            )

            ChantItemCard(
                item = currentItem,
                indexText = "${currentIndex + 1} / ${items.size}",
                chantPhase = chantPhase,
                onSpeakQuestion = { onSpeakQuestion(currentItem) },
                onSpeakAnswer = { onSpeakAnswer(currentItem) },
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = GlassSurface,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = items.size > 1,
                    onClick = {
                        onStopSpeech()
                        val next = if (currentIndex <= 0) items.lastIndex else currentIndex - 1
                        onIndexChange(next)
                    },
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("上一题")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = items.size > 1,
                    onClick = {
                        onStopSpeech()
                        onIndexChange((currentIndex + 1) % items.size)
                    },
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("下一题")
                }
            }
        }
    }
}

@Composable
private fun EmptyChantState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = GlassSurface),
        border = BorderStroke(1.dp, GlassStroke),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "暂无可唱题目",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "今日 5 题生成后，唱题模式会按题目和答案循环播放。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 唱题页当前题卡：听不清时可以直接低头看题目、口播和参考答案。 */
@Composable
private fun ChantItemCard(
    item: PracticeItem,
    indexText: String,
    chantPhase: String,
    onSpeakQuestion: () -> Unit,
    onSpeakAnswer: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = GlassSurface),
        border = BorderStroke(1.dp, GlassStroke),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tag(text = item.tags.ifBlank { item.category })
                Text(
                    text = indexText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = chantPhase,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = item.question,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            if (!item.oralOneLiner.isNullOrBlank()) {
                ReadAlongSection(
                    title = "面试口播",
                    body = item.oralOneLiner,
                    emphasis = true,
                )
            }
            ReadAlongSection(
                title = "参考答案",
                body = item.answer.ifBlank { "本题暂无参考答案。" },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onSpeakQuestion,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("只播放题目")
                }
                OutlinedButton(
                    onClick = onSpeakAnswer,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("只播放答案")
                }
            }
        }
    }
}

@Composable
private fun ReadAlongSection(title: String, body: String, emphasis: Boolean = false) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (emphasis) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = if (emphasis) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = if (emphasis) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

/** TTS 控制区：语速、引擎、语音与唱题开关集中放在这里。 */
@Composable
private fun SpeechControlCard(
    isSpeaking: Boolean,
    chantMode: Boolean,
    chantPhase: String,
    speechRate: Float,
    engineOptions: List<TtsOption>,
    voiceOptions: List<TtsOption>,
    currentEngineLabel: String,
    currentVoiceLabel: String,
    onSpeechRateChange: (Float) -> Unit,
    onCycleEngine: () -> Unit,
    onCycleVoice: () -> Unit,
    onRefreshVoices: () -> Unit,
    onToggleChant: () -> Unit,
    onStopSpeech: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = GlassSurface),
        border = BorderStroke(1.dp, GlassStroke),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = if (chantMode) "唱题模式" else "语音播放",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (isSpeaking || chantMode) chantPhase else "朗读时会忽略代码块和函数行",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "${"%.1f".format(speechRate)}x",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { onSpeechRateChange(speechRate - 0.1f) },
                    enabled = speechRate > 0.6f,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("慢一点")
                }
                OutlinedButton(
                    onClick = { onSpeechRateChange(speechRate + 0.1f) },
                    enabled = speechRate < 1.4f,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("快一点")
                }
            }
            Text(
                text = "引擎：$currentEngineLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "语音：$currentVoiceLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onCycleEngine,
                    enabled = engineOptions.size > 1,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("切换引擎")
                }
                OutlinedButton(
                    onClick = onCycleVoice,
                    enabled = voiceOptions.isNotEmpty(),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("切换语音")
                }
                OutlinedButton(
                    onClick = onRefreshVoices,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("刷新")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onToggleChant,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(if (chantMode) "停止唱题" else "开始唱题")
                }
                OutlinedButton(
                    onClick = onStopSpeech,
                    enabled = isSpeaking || chantMode,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("停止播放")
                }
            }
        }
    }
}

/** 今日进度卡，展示当天记住进度和尝试次数。 */
@Composable
private fun DailyStatusCard(remembered: Int, total: Int, attempts: Int, complete: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = GlassBlue),
        border = BorderStroke(1.dp, GlassStroke),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (complete) "今天都记住了" else "今日 5 题",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "$remembered / $total",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else remembered.toFloat() / total.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "尝试 $attempts 次。点击“还没记住”会继续循环练，点击“记住了”会推进今日进度。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** 普通刷题卡：输入自己的回答，再决定是否展示参考答案。 */
@Composable
private fun PracticeCard(
    item: PracticeItem,
    indexText: String,
    remembered: Boolean,
    onSpeakQuestion: (() -> Unit)? = null,
    onSpeakAnswer: (() -> Unit)? = null,
    onRemembered: () -> Unit,
    onForgotten: () -> Unit,
) {
    var showAnswer by remember(item.id) { mutableStateOf(false) }
    var userAnswer by remember(item.id) { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = GlassSurface),
        border = BorderStroke(1.dp, GlassStroke),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tag(text = item.tags.ifBlank { item.category })
                Text(
                    text = indexText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = item.question,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            if (!item.oralOneLiner.isNullOrBlank()) {
                Text(
                    text = item.oralOneLiner,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            OutlinedTextField(
                value = userAnswer,
                onValueChange = { userAnswer = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                label = { Text("你的回答") },
                placeholder = { Text("先写出要点，再看参考答案") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { showAnswer = !showAnswer },
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(if (showAnswer) "隐藏参考答案" else "显示参考答案")
                }
                if (onSpeakQuestion != null) {
                    OutlinedButton(
                        onClick = onSpeakQuestion,
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text("播放题目")
                    }
                }
                if (remembered) {
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text("已记住")
                    }
                }
            }
            if (showAnswer) {
                AnswerPanel(item.answer.ifBlank { "本题暂无参考答案。" })
            }
            if (onSpeakAnswer != null) {
                OutlinedButton(
                    onClick = onSpeakAnswer,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("播放答案")
                }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onRemembered,
                    enabled = !remembered,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("记住了")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onForgotten,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("还没记住")
                }
            }
        }
    }
}

@Composable
private fun AnswerPanel(answer: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = GlassSurfaceStrong,
        tonalElevation = 0.dp,
    ) {
        Text(
            text = answer,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** 小测页：从最近 3/5/7 天学过的题中抽题，用户自判答对/答错。 */
@Composable
private fun QuizPracticeScreen(
    allItems: List<PracticeItem>,
    records: Map<String, DailyPracticeRecord>,
    today: String,
) {
    var dayWindow by remember { mutableIntStateOf(7) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var showAnswer by remember { mutableStateOf(false) }
    var correctIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var wrongIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val quizItems = remember(allItems, records, today, dayWindow) {
        recentQuizItems(allItems, records, today, dayWindow)
    }
    val answeredCount = correctIds.size + wrongIds.size
    val finished = quizItems.isNotEmpty() && answeredCount >= quizItems.size
    val currentItem = quizItems.getOrNull(currentIndex.coerceIn(0, max(0, quizItems.lastIndex)))

    fun resetQuiz(days: Int = dayWindow) {
        dayWindow = days
        currentIndex = 0
        showAnswer = false
        correctIds = emptySet()
        wrongIds = emptySet()
    }

    fun markCurrent(correct: Boolean) {
        val item = currentItem ?: return
        correctIds = if (correct) correctIds + item.id else correctIds - item.id
        wrongIds = if (correct) wrongIds - item.id else wrongIds + item.id
        if (currentIndex < quizItems.lastIndex) {
            currentIndex += 1
            showAnswer = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = GlassBlue),
        border = BorderStroke(1.dp, GlassStroke),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "阶段小测",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "从最近 $dayWindow 天学过的题中抽最多 10 题，先回答再自判。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 5, 7).forEach { days ->
                        if (dayWindow == days) {
                            Button(
                                onClick = { resetQuiz(days) },
                                shape = RoundedCornerShape(18.dp),
                            ) {
                                Text("${days}天")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { resetQuiz(days) },
                                shape = RoundedCornerShape(18.dp),
                            ) {
                                Text("${days}天")
                            }
                        }
                    }
                }
                LinearProgressIndicator(
                    progress = { if (quizItems.isEmpty()) 0f else answeredCount.toFloat() / quizItems.size },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "进度 $answeredCount / ${quizItems.size}，答对 ${correctIds.size} 题",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        when {
            quizItems.isEmpty() -> EmptyQuizState(dayWindow)
            finished -> QuizResultCard(
                total = quizItems.size,
                correct = correctIds.size,
                onRestart = { resetQuiz() },
            )
            currentItem != null -> QuizQuestionCard(
                item = currentItem,
                indexText = "${currentIndex + 1} / ${quizItems.size}",
                showAnswer = showAnswer,
                onToggleAnswer = { showAnswer = !showAnswer },
                onCorrect = { markCurrent(correct = true) },
                onWrong = { markCurrent(correct = false) },
            )
        }
    }
}

@Composable
private fun EmptyQuizState(dayWindow: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = GlassSurface),
        border = BorderStroke(1.dp, GlassStroke),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "最近 $dayWindow 天还没有可测题目",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "先完成几天今日刷题，再回来做阶段小测。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuizResultCard(total: Int, correct: Int, onRestart: () -> Unit) {
    val percent = if (total == 0) 0 else correct * 100 / total
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = GlassMint),
        border = BorderStroke(1.dp, GlassStroke),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "小测完成",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "答对 $correct / $total，正确率 $percent%",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Button(onClick = onRestart, shape = RoundedCornerShape(18.dp)) {
                Text("重新测一次")
            }
        }
    }
}

@Composable
private fun QuizQuestionCard(
    item: PracticeItem,
    indexText: String,
    showAnswer: Boolean,
    onToggleAnswer: () -> Unit,
    onCorrect: () -> Unit,
    onWrong: () -> Unit,
) {
    var userAnswer by remember(item.id) { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = GlassSurface),
        border = BorderStroke(1.dp, GlassStroke),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tag(text = item.tags.ifBlank { item.category })
                Text(
                    text = indexText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = item.question,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = userAnswer,
                onValueChange = { userAnswer = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                label = { Text("你的回答") },
                placeholder = { Text("先凭记忆写，再显示答案") },
            )
            Button(onClick = onToggleAnswer, shape = RoundedCornerShape(18.dp)) {
                Text(if (showAnswer) "隐藏答案" else "显示答案")
            }
            if (showAnswer) {
                AnswerPanel(item.answer.ifBlank { "本题暂无参考答案。" })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onCorrect,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("答对")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onWrong,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("答错")
                }
            }
        }
    }
}

/** 搜索全量本地题库；多个关键词会按“全部命中”过滤。 */
@Composable
private fun SearchPracticeScreen(items: List<PracticeItem>) {
    var query by remember { mutableStateOf("") }
    var selectedItem by remember { mutableStateOf<PracticeItem?>(null) }

    val results = remember(query, items) {
        val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) {
            items.take(30)
        } else {
            items.filter { item ->
                val haystack = "${item.question}\n${item.answer}\n${item.tags}".lowercase()
                tokens.all { token -> haystack.contains(token.lowercase()) }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                selectedItem = null
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("搜索题干 / 答案 / 标签") },
            placeholder = { Text("例如 weak、RunLoop、Swift") },
        )

        Text(
            text = "命中 ${results.size} 题",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            selectedItem?.let { selected ->
                item {
                    PracticeCard(
                        item = selected,
                        indexText = "搜索结果",
                        remembered = false,
                        onRemembered = {},
                        onForgotten = {},
                    )
                }
            }
            items(results, key = { it.id }) { item ->
                SearchResultRow(
                    item = item,
                    selected = selectedItem?.id == item.id,
                    onClick = { selectedItem = item },
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(item: PracticeItem, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = item.question,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Tag(text = item.tags.ifBlank { "iOS" })
        }
    }
}

@Composable
private fun Tag(text: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun createDailyRecord(date: String, items: List<PracticeItem>): DailyPracticeRecord {
    // 按“日期 + 题目 id”的稳定哈希抽题，保证同一天多次打开得到同一组 5 题。
    val ids = items
        .sortedBy { stableHash("$date:${it.id}") }
        .take(5)
        .map { it.id }
    return DailyPracticeRecord(
        date = date,
        itemIds = ids,
        rememberedIds = emptySet(),
    )
}

private data class CalendarCell(
    val date: String,
    val day: Int,
    val inMonth: Boolean,
    val record: DailyPracticeRecord?,
)

private fun calendarCells(
    month: YearMonth,
    records: Map<String, DailyPracticeRecord>,
): List<CalendarCell> {
    // 日历固定渲染 6 行，避免月份切换时弹窗高度跳动。
    val firstDay = month.atDay(1)
    val leadingEmptyDays = firstDay.dayOfWeek.value % 7
    val totalCells = 42
    return List(totalCells) { index ->
        val dayOffset = index - leadingEmptyDays
        if (dayOffset < 0 || dayOffset >= month.lengthOfMonth()) {
            CalendarCell(
                date = "",
                day = 0,
                inMonth = false,
                record = null,
            )
        } else {
            val date = month.atDay(dayOffset + 1)
            val key = date.format(DateTimeFormatter.ISO_DATE)
            CalendarCell(
                date = key,
                day = date.dayOfMonth,
                inMonth = true,
                record = records[key],
            )
        }
    }
}

private fun recentQuizItems(
    allItems: List<PracticeItem>,
    records: Map<String, DailyPracticeRecord>,
    today: String,
    days: Int,
): List<PracticeItem> {
    val end = LocalDate.parse(today)
    val validDates = (0 until days)
        .map { end.minusDays(it.toLong()).format(DateTimeFormatter.ISO_DATE) }
        .toSet()
    val ids = records
        .filterKeys { it in validDates }
        .values
        .flatMap { it.itemIds }
        .distinct()
    val byId = allItems.associateBy { it.id }
    return ids
        .mapNotNull { byId[it] }
        .sortedBy { stableHash("$today:$days:${it.id}") }
        .take(10)
}

private fun markRemembered(record: DailyPracticeRecord?, item: PracticeItem): DailyPracticeRecord {
    val current = record ?: DailyPracticeRecord(
        date = LocalDate.now().format(DateTimeFormatter.ISO_DATE),
        itemIds = listOf(item.id),
        rememberedIds = emptySet(),
    )
    val remembered = current.rememberedIds + item.id
    val completedAt = if (remembered.size >= current.itemIds.size) {
        current.completedAt ?: System.currentTimeMillis()
    } else {
        current.completedAt
    }
    return current.copy(
        rememberedIds = remembered,
        attempts = current.attempts + 1,
        completedAt = completedAt,
    )
}

private fun markForgotten(record: DailyPracticeRecord?): DailyPracticeRecord {
    val current = record ?: DailyPracticeRecord(
        date = LocalDate.now().format(DateTimeFormatter.ISO_DATE),
        itemIds = emptyList(),
        rememberedIds = emptySet(),
    )
    return current.copy(attempts = current.attempts + 1)
}

private fun stableHash(input: String): Long {
    var hash = 2166136261L
    for (char in input) {
        hash = hash xor char.code.toLong()
        hash *= 16777619L
    }
    return hash and 0xffffffffL
}

private fun Int.floorMod(modulus: Int): Int {
    return ((this % modulus) + modulus) % modulus
}
