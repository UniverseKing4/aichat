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
        val list = mutableListOf<Conversation>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(Conversation(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    timestamp = obj.getLong("timestamp"),
                    systemPrompt = obj.optString("systemPrompt", "")
                ))
            }
        } catch (e: Exception) {}
        return list.sortedByDescending { it.timestamp }
    }
    
    fun saveConversation(conv: Conversation) {
        val list = getConversations().toMutableList()
        list.removeIf { it.id == conv.id }
        list.add(conv)
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("timestamp", c.timestamp)
                put("systemPrompt", c.systemPrompt)
            })
        }
        prefs.edit().putString("list", arr.toString()).apply()
    }
    
    fun deleteConversation(id: String) {
        val list = getConversations().toMutableList()
        list.removeIf { it.id == id }
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("timestamp", c.timestamp)
                put("systemPrompt", c.systemPrompt)
            })
        }
        prefs.edit().putString("list", arr.toString()).apply()
        prefs.edit().remove("msg_$id").apply()
    }
    
    fun createNew(name: String): Conversation {
        val conv = Conversation(id = UUID.randomUUID().toString(), name = name)
        saveConversation(conv)
        return conv
    }
    
    fun getMessages(convId: String): String? = prefs.getString("msg_$convId", null)
    
    fun saveMessages(convId: String, json: String) {
        prefs.edit().putString("msg_$convId", json).apply()
    }
}
