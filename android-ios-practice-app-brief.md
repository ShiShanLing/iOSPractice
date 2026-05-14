# Android iOS 面试刷题 App 实现说明

这份文档用于交接给新开的 Android 项目窗口。目标是基于当前 Angular 项目里的本地 iOS 题库，做一个原生 Android 刷题 App。

## 1. 项目定位

做一个本地单机版 **iOS 面试刷题 App**。

第一版只刷内置 iOS 题目，不做导入功能，不做账号、不做云同步、不做后台管理。

题库来源：

```text
/Volumes/dfre/LearningProject/angular20/src/app/pages/practice/ios.seed.json
```

Android 项目中建议复制为：

```text
app/src/main/assets/ios.seed.json
```

## 2. Android Studio 创建项目

创建项目时选择：

```text
Template: Empty Activity
Language: Kotlin
Build configuration language: Kotlin DSL
Minimum SDK: API 26
```

推荐项目名：

```text
Project name: IosPractice
App name: iOS 面试刷题
Package name: com.dfre.iospractice
```

技术栈：

```text
原生 Android
Kotlin
Jetpack Compose
MVVM
DataStore
Navigation Compose
Kotlin Serialization
Android TextToSpeech 可后加
```

## 3. 第一版功能范围

必须做：

- 启动时从 `assets/ios.seed.json` 读取内置 iOS 题库
- 今日 5 题
- 题目卡片展示：题干、标签、参考答案
- 参考答案默认隐藏，点击按钮显示/隐藏
- 用户可输入自己的回答
- “记住了 / 还没记住”
- 今日进度：例如 `今日 2 / 5`
- 上一题 / 下一题 / 随机一题
- 搜索全量题库：按题干、答案、标签匹配
- 本地保存每日刷题记录

可以第二版再做：

- 刷题日历
- 字号调整
- Markdown 渲染
- 播放题目/答案
- 唱题模式
- 通知提醒
- 导出/备份进度

明确不做：

- Excel/CSV 导入
- 题库编辑
- 多分类管理
- 账号登录
- 云同步
- 排行榜、积分、复杂关卡系统

## 4. 题库数据结构

当前 Web 项目里的题目结构来自：

```text
src/app/pages/practice/practice.types.ts
```

Android 侧保持字段兼容即可：

```kotlin
@Serializable
data class PracticeItem(
    val id: String,
    val category: String,
    val question: String,
    val answer: String,
    val tags: String = "",
    val importedAt: Long = 0L,
    val markD: Boolean = false,
    val oralOneLiner: String? = null
)
```

第一版只会用到 `ios` 分类，但仍建议保留 `category` 字段，方便以后扩展。

## 5. 本地进度结构

参考 Web 项目 `PracticeDayRecord` 的概念：

```kotlin
@Serializable
data class DailyPracticeRecord(
    val date: String,
    val itemIds: List<String>,
    val rememberedIds: List<String>,
    val attempts: Int = 0,
    val completedAt: Long? = null
)
```

本地用 DataStore 保存：

```text
practice_daily_state
```

可以保存成 JSON 字符串：

```kotlin
@Serializable
data class DailyPracticeState(
    val records: Map<String, DailyPracticeRecord> = emptyMap()
)
```

日期 key 用本地日期：

```text
yyyy-MM-dd
```

## 6. 今日 5 题规则

每日第一次打开时，如果当天没有记录：

1. 读取全量题库
2. 根据当天日期稳定打乱
3. 取前 5 题
4. 写入当天 `DailyPracticeRecord`

要求同一天多次打开，今日 5 题不变化。

实现方式可以简单一点：

```text
seed = yyyy-MM-dd 的 hash
items.shuffled(Random(seed)).take(5)
```

用户点击：

- `记住了`：把当前题 id 加入 `rememberedIds`，`attempts + 1`
- `还没记住`：只做 `attempts + 1`，进入下一道未记住题
- 当 `rememberedIds` 覆盖当天全部 `itemIds` 时，写入 `completedAt`

## 7. 推荐目录结构

```text
app/src/main/java/com/dfre/iospractice/
  MainActivity.kt

  data/
    PracticeItem.kt
    DailyPracticeRecord.kt
    DailyPracticeState.kt
    PracticeRepository.kt
    PracticeProgressStore.kt

  domain/
    DailyPracticeUseCase.kt
    SearchPracticeUseCase.kt

  ui/
    PracticeApp.kt
    navigation/
      AppNavGraph.kt
      Routes.kt
    screens/
      TodayPracticeScreen.kt
      AllPracticeScreen.kt
      SearchScreen.kt
      CalendarScreen.kt
    components/
      PracticeCard.kt
      AnswerPanel.kt
      DailyProgressBar.kt

  feature/
    tts/
      PracticeTtsController.kt

app/src/main/assets/
  ios.seed.json
```

第一版如果想更快，可以先不建 `domain/` 和 `feature/tts/`，等功能变多再拆。

## 8. 页面建议

### TodayPracticeScreen

首页打开直接进今日刷题。

包含：

- 今日进度
- 当前题序号
- 题干
- 标签
- 用户回答输入框
- 显示/隐藏参考答案
- 记住了
- 还没记住
- 上一题 / 下一题 / 随机

### SearchScreen

包含：

- 搜索框
- 搜索结果列表
- 点击结果进入题目详情或复用刷题卡片

搜索规则：

```text
题干 + 答案 + 标签 中包含关键词
多个词可以先简单按整体字符串匹配，后续再支持空格分词同时匹配
```

### CalendarScreen

第二版可做。展示每天是否完成、完成数量。

## 9. 依赖建议

版本号以新项目默认 Gradle 版本兼容为准。

需要：

```kotlin
implementation("androidx.navigation:navigation-compose:<version>")
implementation("androidx.datastore:datastore-preferences:<version>")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:<version>")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:<version>")
```

可选：

```kotlin
implementation("com.mikepenz:multiplatform-markdown-renderer:<version>")
```

如果第一版不做 Markdown，可以先把答案当纯文本显示。

## 10. 实现顺序

建议按这个顺序开发：

1. 创建 Empty Activity Compose 项目
2. 复制 `ios.seed.json` 到 `app/src/main/assets/`
3. 加 Kotlin Serialization，完成 `PracticeItem` 解析
4. 做 `PracticeRepository`，提供 `loadItems()`
5. 做 `TodayPracticeScreen` 静态 UI
6. 接入 ViewModel，显示今日 5 题
7. 实现显示/隐藏答案、上一题/下一题/随机
8. 用 DataStore 保存今日记录
9. 实现 “记住了 / 还没记住”
10. 做搜索页
11. 再考虑日历、Markdown、TTS

## 11. 与 Web 版对应关系

当前需求来自 Angular 项目刷题页：

```text
src/app/pages/practice/practice.component.ts
src/app/pages/practice/practice.component.html
src/app/pages/practice/practice-storage.service.ts
src/app/pages/practice/practice.types.ts
src/app/pages/practice/ios.seed.json
```

Android 版不需要完整复刻 Web 版，只需要保留核心刷题体验。

Web 版有但 Android 第一版可以不做：

- Excel/CSV 导入
- 清空题库
- 多分类筛选
- NgZorro 风格 UI
- 唱题模式
- 复杂 Markdown 展示

## 12. 验收标准

第一版完成后应满足：

- App 可正常启动
- 能从 assets 读取全部 iOS 题目
- 每天固定抽取 5 题
- 能显示题干、标签、参考答案
- 答案默认隐藏
- 点击“记住了”后今日进度增加
- 关闭 App 再打开，今日进度仍在
- 搜索能找到本地题库中的题目
- 不依赖网络
- 不需要导入任何文件

