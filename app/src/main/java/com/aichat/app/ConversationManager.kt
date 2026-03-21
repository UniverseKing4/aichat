package com.aichat.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ConversationManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("conversations", Context.MODE_PRIVATE)
    
    fun getConversations(): List<Conversation> {
        val json = prefs.getString("list", "[]") ?: "[]"
        val array = JSONArray(json)
        val list = mutableListOf<Conversation>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(Conversation(
                obj.getString("id"),
                obj.getString("name"),
                obj.getLong("timestamp"),
                obj.optString("systemPrompt", "")
            ))
        }
        return list.sortedByDescending { it.timestamp }
    }
    
    fun saveConversation(conv: Conversation) {
        val list = getConversations().toMutableList()
        list.removeAll { it.id == conv.id }
        list.add(conv)
        saveList(list)
    }
    
    fun deleteConversation(id: String) {
        val list = getConversations().filter { it.id != id }
        saveList(list)
        prefs.edit().remove("messages_$id").apply()
    }
    
    fun createNew(name: String): Conversation {
        val conv = Conversation(UUID.randomUUID().toString(), name, System.currentTimeMillis())
        saveConversation(conv)
        return conv
    }
    
    private fun saveList(list: List<Conversation>) {
        val array = JSONArray()
        list.forEach {
            array.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("timestamp", it.timestamp)
                put("systemPrompt", it.systemPrompt)
            })
        }
        prefs.edit().putString("list", array.toString()).apply()
    }
    
    fun getMessages(convId: String): String? = prefs.getString("messages_$convId", null)
    fun saveMessages(convId: String, json: String) = prefs.edit().putString("messages_$convId", json).apply()
}
