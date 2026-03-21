package com.aichat.app

import android.net.Uri

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val imageUri: Uri? = null,
    val generatedImageUrl: String? = null,
    val isLoading: Boolean = false
)
