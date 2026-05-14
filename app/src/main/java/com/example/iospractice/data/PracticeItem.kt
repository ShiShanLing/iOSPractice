package com.example.iospractice.data

data class PracticeItem(
    val id: String,
    val category: String,
    val question: String,
    val answer: String,
    val tags: String = "",
    val importedAt: Long = 0L,
    val markD: Boolean = false,
    val oralOneLiner: String? = null,
)

