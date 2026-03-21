package com.aichat.app

import android.net.Uri

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val imageUri: Uri? = null,
    val generatedImageUrl: String? = null,
    val isLoading: Boolean = false
) {
    fun copy(
        text: String = this.text,
        isUser: Boolean = this.isUser,
        imageUri: Uri? = this.imageUri,
        generatedImageUrl: String? = this.generatedImageUrl,
        isLoading: Boolean = this.isLoading
    ) = ChatMessage(text, isUser, imageUri, generatedImageUrl, isLoading)
}
