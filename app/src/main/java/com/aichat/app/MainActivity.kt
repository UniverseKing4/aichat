package com.aichat.app

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import com.aichat.app.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    companion object {
        private const val PROXY_URL = "https://aichat-proxy.universeking.workers.dev"
    }
    
    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { getSharedPreferences("app_prefs", MODE_PRIVATE) }
    private val messages = mutableListOf<Message>()
    private lateinit var adapter: ChatAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        window.setBackgroundDrawableResource(R.color.background)
        
        adapter = ChatAdapter(messages)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        
        binding.sendButton.setOnClickListener {
            val text = binding.messageInput.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                binding.messageInput.text?.clear()
            }
        }
        
        binding.clearButton.setOnClickListener {
            messages.clear()
            adapter.notifyDataSetChanged()
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        updateThemeIcon(menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_model -> {
                showModelDialog()
                true
            }
            R.id.action_dark_mode -> {
                toggleDarkMode()
                invalidateOptionsMenu()
                true
            }
            R.id.action_settings -> {
                showApiKeyDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun updateThemeIcon(menu: Menu) {
        val isDark = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        menu.findItem(R.id.action_dark_mode)?.setIcon(if (isDark) R.drawable.ic_light_mode else R.drawable.ic_dark_mode)
    }
    
    private fun toggleDarkMode() {
        val isDark = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        prefs.edit().putBoolean("dark_mode", !isDark).apply()
        AppCompatDelegate.setDefaultNightMode(if (isDark) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES)
    }
    
    private fun showModelDialog() {
        val models = arrayOf("openai", "openai-fast", "qwen-coder", "mistral", "gemini-fast", "deepseek", "claude-fast", "kimi", "nova-fast", "glm", "minimax", "polly")
        val current = prefs.getString("model", "openai")
        val selected = models.indexOf(current)
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Select Model")
            .setSingleChoiceItems(models, selected) { dialog, which ->
                prefs.edit().putString("model", models[which]).apply()
                Toast.makeText(this, "Model: ${models[which]}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showApiKeyDialog() {
        val input = android.widget.EditText(this)
        input.setText(prefs.getString("api_key", ""))
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("API Key (Optional)")
            .setMessage("Leave empty to use default proxy")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit().putString("api_key", input.text.toString()).apply()
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun sendMessage(text: String) {
        messages.add(Message(text, true))
        adapter.notifyItemInserted(messages.size - 1)
        binding.recyclerView.scrollToPosition(messages.size - 1)
        
        binding.progressBar.visibility = View.VISIBLE
        binding.sendButton.isEnabled = false
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = callAPI(text)
                withContext(Dispatchers.Main) {
                    messages.add(Message(response, false))
                    adapter.notifyItemInserted(messages.size - 1)
                    binding.recyclerView.scrollToPosition(messages.size - 1)
                    binding.progressBar.visibility = View.GONE
                    binding.sendButton.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    binding.progressBar.visibility = View.GONE
                    binding.sendButton.isEnabled = true
                }
            }
        }
    }
    
    private fun callAPI(text: String): String {
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        
        val model = prefs.getString("model", "openai") ?: "openai"
        val apiKey = prefs.getString("api_key", "") ?: ""
        
        val json = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", text)
                })
            })
        }
        
        val url = if (apiKey.isEmpty()) PROXY_URL else "https://gen.pollinations.ai/v1/chat/completions"
        
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
        
        if (apiKey.isNotEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }
        
        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw Exception("API error: ${response.code}")
            val body = response.body?.string() ?: throw Exception("Empty response")
            val jsonResponse = JSONObject(body)
            return jsonResponse.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }
}

data class Message(val text: String, val isUser: Boolean)
