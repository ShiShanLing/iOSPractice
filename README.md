# iOS 面试刷题

一个原生 Android 本地刷题 App，用来复习 iOS / Swift / Objective-C 面试题。项目使用 Kotlin + Jetpack Compose 开发，题库随 App 内置，不依赖网络、不需要登录，也不需要导入文件。

## 功能

- **今日刷题**：每天固定抽取 5 道题，支持“记住了 / 还没记住”。
- **唱题模式**：使用 Android TextToSpeech 自动朗读题目和答案。
- **TTS 设置**：支持切换系统 TTS 引擎、语音和语速，可配合 MultiTTS 使用。
- **小测**：从最近 3 / 5 / 7 天学过的题里抽题，手动判断答对/答错并统计正确率。
- **学习日历**：按月份查看学习记录，显示每天已记住题数和当日题数。
- **搜索题库**：按题干、答案、标签搜索本地题库。
- **本地进度保存**：使用 SharedPreferences 保存每日学习记录。
- **本地题库**：题库放在 `app/src/main/assets/ios.seed.json`。

## 设计与交互

- 使用底部浮动 Tab Bar 切换功能页。
- UI 偏 iOS 风格：大圆角、半透明拟玻璃卡片、轻量阴影。
- 唱题页中题目、面试口播和参考答案会同时展示，方便听不清时查看。
- 唱题朗读时会跳过 Markdown 代码块和明显代码/函数行，避免 TTS 逐字朗读代码符号。

## 技术栈

- Kotlin
- Jetpack Compose
- Material 3
- Android TextToSpeech
- SharedPreferences
- Gradle Kotlin DSL

## 项目结构

```text
app/src/main/java/com/example/iospractice/
  MainActivity.kt
  data/
    PracticeItem.kt
    DailyPracticeRecord.kt
    PracticeRepository.kt
    PracticeProgressStore.kt
  tts/
    PracticeTtsController.kt
  ui/theme/
    Color.kt
    Theme.kt
    Type.kt

app/src/main/assets/
  ios.seed.json
```

## 运行

使用 Android Studio 打开项目根目录：

```text
/Users/SSL/Desktop/练习项目/IOSPractice
```

等待 Gradle Sync 完成后，选择模拟器或真机，点击 Run。

也可以用命令行编译：

```bash
./gradlew :app:assembleDebug
```

生成的 debug APK 在：

```text
app/build/outputs/apk/debug/app-debug.apk
```

安装到已连接的设备：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 题库说明

当前题库为本地 JSON：

```text
app/src/main/assets/ios.seed.json
```

题目结构主要字段：

```json
{
  "id": "ios-weak-side-table",
  "category": "iOS",
  "topic": "内存 / ARC",
  "question": "从原理上讲，如何实现 weak？",
  "answer": "参考答案...",
  "difficulty": "Hard",
  "oralOneLiner": "面试口播一句..."
}
```

## 打包状态

当前已验证：

```bash
./gradlew :app:assembleDebug
```

构建结果：

```text
BUILD SUCCESSFUL
```

## 后续可扩展

- Release 签名配置
- 题库版本管理
- 错题集中复习
- 小测历史记录
- Android 12+ 真背景模糊玻璃效果
- 主题色/字体大小设置

