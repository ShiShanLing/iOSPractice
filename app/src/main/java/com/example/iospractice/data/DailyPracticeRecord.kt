package com.example.iospractice.data

data class DailyPracticeRecord(
    val date: String,
    val itemIds: List<String>,
    val rememberedIds: Set<String>,
    val attempts: Int = 0,
    val completedAt: Long? = null,
)

