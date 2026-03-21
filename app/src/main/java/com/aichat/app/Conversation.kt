package com.aichat.app

data class Conversation(
    val id: String,
    val name: String,
    val timestamp: Long,
    val systemPrompt: String = ""
)
