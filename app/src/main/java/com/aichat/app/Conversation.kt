package com.aichat.app

data class Conversation(
    val id: String,
    val name: String,
    val timestamp: Long = System.currentTimeMillis(),
    val systemPrompt: String = ""
)
