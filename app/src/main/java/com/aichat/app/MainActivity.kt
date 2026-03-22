package com.aichat.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.aichat.app.databinding.ActivityMainBinding
import com.aichat.app.databinding.DialogApiKeyBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    companion object {
        private const val API_URL = "https://gen.pollinations.ai/v1/chat/completions"
        private const val IMAGE_URL = "https://gen.pollinations.ai/v1/images/generations"
        private const val PROXY_URL = "https://aivision-proxy.universeking.workers.dev"
    }
    
    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { getSharedPreferences("app_prefs", MODE_PRIVATE) }
    private val scrollPrefs by lazy { getSharedPreferences("scroll_positions", MODE_PRIVATE) }
    private val chatMessages = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter
    private var chatJob: Job? = null
    private var selectedImageUri: Uri? = null
    private var wasAtBottom = true
    
    private lateinit var conversationManager: ConversationManager
    private var currentConversationId: String = ""
    private var systemPrompt: String = ""
    private var conversationAdapter: ConversationAdapter? = null
    
    private val pickImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                binding.attachedImage.visibility = View.VISIBLE
                binding.attachedImage.setImageURI(uri)
                binding.removeImageButton.visibility = View.VISIBLE
            }
        }
    }
    
    private val pickFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                importChat(uri)
            }
        }
    }
    
    private val createFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                exportChatToUri(uri)
            }
        }
    }
    
    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) selectImage() else Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        window.setBackgroundDrawableResource(R.color.background)
        
        conversationManager = ConversationManager(this)
        initConversation()
        setupRecyclerView()
        setupListeners()
        setupDrawer()
        
        wasAtBottom = prefs.getBoolean("was_at_bottom", true)
        if (wasAtBottom && chatMessages.isNotEmpty()) {
            binding.chatRecyclerView.post {
                val layoutManager = binding.chatRecyclerView.layoutManager as? LinearLayoutManager
                layoutManager?.scrollToPositionWithOffset(chatMessages.size - 1, 0)
                binding.chatRecyclerView.postDelayed({
                    binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size - 1)
                }, 100)
            }
        }
    }
    
    private fun initConversation() {
        val lastId = prefs.getString("last_conv_id", null)
        val convs = conversationManager.getConversations()
        currentConversationId = if (lastId != null && convs.any { it.id == lastId }) {
            lastId
        } else {
            if (convs.isEmpty()) {
                conversationManager.createNew("Chat 1").id
            } else {
                convs.first().id
            }
        }
        prefs.edit().putString("last_conv_id", currentConversationId).apply()
        val conv = conversationManager.getConversations().find { it.id == currentConversationId }
        systemPrompt = conv?.systemPrompt ?: ""
        loadChatHistory()
    }
    
    private fun setupDrawer() {
        binding.drawerLayout.setScrimColor(android.graphics.Color.parseColor("#80000000"))
        binding.drawerLayout.drawerElevation = 0f
        
        try {
            val draggerField = binding.drawerLayout.javaClass.getDeclaredField("mLeftDragger")
            draggerField.isAccessible = true
            val dragger = draggerField.get(binding.drawerLayout) as androidx.customview.widget.ViewDragHelper
            val edgeSizeField = dragger.javaClass.getDeclaredField("mEdgeSize")
            edgeSizeField.isAccessible = true
            val edgeSize = edgeSizeField.getInt(dragger)
            edgeSizeField.setInt(dragger, edgeSize * 3)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        binding.drawerLayout.addDrawerListener(object : androidx.drawerlayout.widget.DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: android.view.View, slideOffset: Float) {}
            override fun onDrawerOpened(drawerView: android.view.View) {}
            override fun onDrawerClosed(drawerView: android.view.View) {}
            override fun onDrawerStateChanged(newState: Int) {
                if (newState == androidx.drawerlayout.widget.DrawerLayout.STATE_DRAGGING) {
                    val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
                }
            }
        })
        
        binding.toolbar.setNavigationOnClickListener {
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
            binding.drawerLayout.open()
        }
        
        val drawerView = layoutInflater.inflate(R.layout.drawer_content, binding.navigationView, false)
        binding.navigationView.removeAllViews()
        binding.navigationView.addView(drawerView)
        
        val btnNewChat = drawerView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnNewChat)
        val btnExport = drawerView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnExport)
        val btnImport = drawerView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnImport)
        val btnDeleteSelected = drawerView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDeleteSelected)
        val btnCancelSelection = drawerView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancelSelection)
        val conversationsRv = drawerView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.conversationsRecyclerView)
        
        btnNewChat.setOnClickListener { createNewChat() }
        btnExport.setOnClickListener { exportChat() }
        btnImport.setOnClickListener { selectFileToImport() }
        btnDeleteSelected.setOnClickListener { deleteSelectedConversations() }
        btnCancelSelection.setOnClickListener { 
            exitSelectionMode()
            updateDrawerConversations()
        }
        
        conversationsRv.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        updateDrawerConversations()
    }
    
    private fun updateDrawerConversations() {
        val conversationsRv = binding.navigationView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.conversationsRecyclerView)
        
        val convs = conversationManager.getConversations()
        
        // Preserve selection state if adapter exists
        val wasInSelectionMode = conversationAdapter?.isSelectionMode ?: false
        val previousSelections = conversationAdapter?.selectedItems?.toSet() ?: emptySet()
        
        conversationAdapter = ConversationAdapter(
            conversations = convs,
            currentId = currentConversationId,
            onClick = { conv -> loadConversation(conv.id) },
            onLongClick = { conv -> showConversationOptions(conv) }
        )
        
        // Restore selection state
        if (wasInSelectionMode) {
            conversationAdapter?.isSelectionMode = true
            conversationAdapter?.selectedItems?.addAll(previousSelections)
        }
        
        conversationsRv?.adapter = conversationAdapter
    }
    
    private fun showConversationOptions(conv: Conversation) {
        MaterialAlertDialogBuilder(this)
            .setTitle(conv.name)
            .setItems(arrayOf("Rename", "Delete", "Select Multiple")) { _, which ->
                when (which) {
                    0 -> renameConversation(conv)
                    1 -> deleteConversation(conv)
                    2 -> enterSelectionMode(conv.id)
                }
            }
            .show()
    }
    
    private fun enterSelectionMode(initialId: String) {
        conversationAdapter?.isSelectionMode = true
        conversationAdapter?.selectedItems?.add(initialId)
        conversationAdapter?.notifyDataSetChanged()
        
        val drawerView = binding.navigationView.getChildAt(0)
        val selectionActionsLayout = drawerView.findViewById<android.view.View>(R.id.selectionActionsLayout)
        selectionActionsLayout?.visibility = android.view.View.VISIBLE
    }
    
    private fun exitSelectionMode() {
        conversationAdapter?.clearSelection()
        
        val drawerView = binding.navigationView.getChildAt(0)
        val selectionActionsLayout = drawerView.findViewById<android.view.View>(R.id.selectionActionsLayout)
        selectionActionsLayout?.visibility = android.view.View.GONE
    }
    
    private fun deleteSelectedConversations() {
        try {
            val selected = conversationAdapter?.selectedItems?.toList() ?: emptyList()
            if (selected.isEmpty()) {
                Toast.makeText(this, "No conversations selected", Toast.LENGTH_SHORT).show()
                return
            }
            
            MaterialAlertDialogBuilder(this)
                .setTitle("Delete ${selected.size} Conversations")
                .setMessage("Are you sure?")
                .setPositiveButton("Delete") { _, _ ->
                    chatJob?.cancel()
                    chatJob = null
                    
                    selected.forEach { id ->
                        conversationManager.deleteConversation(id)
                    }
                    
                    if (selected.contains(currentConversationId)) {
                        val remaining = conversationManager.getConversations()
                        if (remaining.isEmpty()) {
                            val newConv = conversationManager.createNew("Chat 1")
                            loadConversation(newConv.id)
                        } else {
                            loadConversation(remaining.first().id)
                        }
                    }
                    
                    exitSelectionMode()
                    updateDrawerConversations()
                    Toast.makeText(this, "Deleted ${selected.size} conversations", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error deleting conversations", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun renameConversation(conv: Conversation) {
        val input = android.widget.EditText(this).apply {
            setText(conv.name)
            hint = "Chat name"
            setPadding(48, 32, 48, 32)
            selectAll()
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Rename Chat")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().ifBlank { conv.name }
                conversationManager.saveConversation(conv.copy(name = newName))
                updateDrawerConversations()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteConversation(conv: Conversation) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Chat")
            .setMessage("Delete \"${conv.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                try {
                    // Cancel any ongoing chat job
                    chatJob?.cancel()
                    chatJob = null
                    
                    conversationManager.deleteConversation(conv.id)
                    if (currentConversationId == conv.id) {
                        val remaining = conversationManager.getConversations()
                        if (remaining.isEmpty()) {
                            val newConv = conversationManager.createNew("Chat 1")
                            loadConversation(newConv.id)
                        } else {
                            loadConversation(remaining.first().id)
                        }
                    }
                    updateDrawerConversations()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Error deleting chat", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(chatMessages)
        chatAdapter.onEditClick = { position, currentText ->
            showEditDialog(position, currentText)
        }
        chatAdapter.onDeleteClick = { position ->
            deleteMessage(position)
        }
        binding.chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = chatAdapter
        }
        loadChatHistory()
        updateEmptyState()
    }
    
    private fun deleteMessage(position: Int) {
        try {
            if (position >= 0 && position < chatMessages.size) {
                val messageToDelete = chatMessages.getOrNull(position) ?: return
                MaterialAlertDialogBuilder(this)
                    .setTitle("Delete Message")
                    .setMessage("Delete this message?")
                    .setPositiveButton("Delete") { _, _ ->
                        try {
                            val currentPos = chatMessages.indexOf(messageToDelete)
                            if (currentPos >= 0 && currentPos < chatMessages.size) {
                                chatMessages.removeAt(currentPos)
                                chatAdapter.notifyItemRemoved(currentPos)
                                if (currentPos < chatMessages.size) {
                                    chatAdapter.notifyItemRangeChanged(currentPos, chatMessages.size - currentPos)
                                }
                                saveChatHistory()
                                updateEmptyState()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun showEditDialog(position: Int, currentText: String) {
        val input = android.widget.EditText(this)
        input.setText(currentText)
        input.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Edit Message")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newText = input.text.toString()
                if (newText.isNotBlank()) {
                    chatMessages[position] = chatMessages[position].copy(text = newText)
                    chatAdapter.notifyItemChanged(position)
                    saveChatHistory()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun updateEmptyState() {
        if (chatMessages.isEmpty()) {
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.chatRecyclerView.visibility = View.GONE
        } else {
            binding.emptyStateLayout.visibility = View.GONE
            binding.chatRecyclerView.visibility = View.VISIBLE
        }
    }
    
    private fun loadChatHistory() {
        chatMessages.clear()
        val savedMessages = conversationManager.getMessages(currentConversationId)
        if (savedMessages != null) {
            try {
                val jsonArray = JSONArray(savedMessages)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val message = ChatMessage(
                        text = obj.getString("text"),
                        isUser = obj.getBoolean("isUser"),
                        imageUri = if (obj.has("imageUri")) Uri.parse(obj.getString("imageUri")) else null,
                        generatedImageUrl = if (obj.has("generatedImageUrl")) obj.getString("generatedImageUrl") else null
                    )
                    chatMessages.add(message)
                }
                chatAdapter.notifyDataSetChanged()
            } catch (e: Exception) {}
        }
        updateEmptyState()
    }
    
    private fun saveChatHistory() {
        try {
            val jsonArray = JSONArray()
            chatMessages.filter { !it.isLoading }.forEach { message ->
                val obj = JSONObject().apply {
                    put("text", message.text)
                    put("isUser", message.isUser)
                    if (message.imageUri != null) put("imageUri", message.imageUri.toString())
                    if (message.generatedImageUrl != null) put("generatedImageUrl", message.generatedImageUrl)
                }
                jsonArray.put(obj)
            }
            conversationManager.saveMessages(currentConversationId, jsonArray.toString())
        } catch (e: Exception) {}
    }
    
    private fun loadConversation(convId: String) {
        try {
            chatJob?.cancel()
            chatJob = null
            
            // Save current scroll position
            val layoutManager = binding.chatRecyclerView.layoutManager as? LinearLayoutManager
            val scrollPosition = layoutManager?.findFirstVisibleItemPosition() ?: 0
            val scrollOffset = layoutManager?.findViewByPosition(scrollPosition)?.top ?: 0
            scrollPrefs.edit()
                .putInt("pos_$currentConversationId", scrollPosition)
                .putInt("offset_$currentConversationId", scrollOffset)
                .apply()
            
            saveChatHistory()
            currentConversationId = convId
            prefs.edit().putString("last_conv_id", convId).apply()
            chatMessages.clear()
            loadChatHistory()
            chatAdapter.notifyDataSetChanged()
            updateEmptyState()
            updateDrawerConversations()
            binding.drawerLayout.close()
            
            val conv = conversationManager.getConversations().find { it.id == convId }
            systemPrompt = conv?.systemPrompt ?: ""
            
            // Restore scroll position
            val savedPosition = scrollPrefs.getInt("pos_$convId", -1)
            val savedOffset = scrollPrefs.getInt("offset_$convId", 0)
            if (savedPosition >= 0 && savedPosition < chatMessages.size) {
                binding.chatRecyclerView.post {
                    layoutManager?.scrollToPositionWithOffset(savedPosition, savedOffset)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error loading conversation", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun createNewChat() {
        val input = android.widget.EditText(this).apply {
            hint = "Chat name"
            setPadding(48, 32, 48, 32)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("New Chat")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().ifBlank { "Chat ${conversationManager.getConversations().size + 1}" }
                val conv = conversationManager.createNew(name)
                loadConversation(conv.id)
                updateDrawerConversations()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun createNewChatQuick() {
        try {
            // Don't create new chat if current chat is empty
            if (chatMessages.isEmpty()) {
                Toast.makeText(this, "Current chat is empty", Toast.LENGTH_SHORT).show()
                return
            }
            
            val name = "Chat ${conversationManager.getConversations().size + 1}"
            val conv = conversationManager.createNew(name)
            loadConversation(conv.id)
            updateDrawerConversations()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error creating chat", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showSystemPromptDialog() {
        val input = android.widget.EditText(this).apply {
            setText(systemPrompt)
            hint = "System instructions"
            setPadding(48, 32, 48, 32)
            minLines = 3
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("System Instructions")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                systemPrompt = input.text.toString()
                val conv = conversationManager.getConversations().find { it.id == currentConversationId }
                if (conv != null) {
                    conversationManager.saveConversation(conv.copy(systemPrompt = systemPrompt))
                }
                binding.drawerLayout.close()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun exportChat() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "chat_${System.currentTimeMillis()}.json")
        }
        createFile.launch(intent)
    }
    
    private fun exportChatToUri(uri: Uri) {
        try {
            val conv = conversationManager.getConversations().find { it.id == currentConversationId }
            val messages = conversationManager.getMessages(currentConversationId) ?: "[]"
            val exportData = JSONObject().apply {
                put("name", conv?.name ?: "Imported Chat")
                put("systemPrompt", conv?.systemPrompt ?: "")
                put("messages", JSONArray(messages))
            }
            contentResolver.openOutputStream(uri)?.use { output ->
                output.write(exportData.toString().toByteArray())
            }
            Toast.makeText(this, "Exported successfully", Toast.LENGTH_SHORT).show()
            binding.drawerLayout.close()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun selectFileToImport() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        pickFile.launch(intent)
    }
    
    private fun importChat(uri: Uri) {
        try {
            val json = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return
            val data = JSONObject(json)
            val name = data.optString("name", "Imported Chat")
            val systemPrompt = data.optString("systemPrompt", "")
            val messages = data.optJSONArray("messages")?.toString() ?: json
            
            val newConv = conversationManager.createNew(name)
            if (systemPrompt.isNotEmpty()) {
                val updatedConv = newConv.copy(systemPrompt = systemPrompt)
                conversationManager.saveConversation(updatedConv)
            }
            conversationManager.saveMessages(newConv.id, messages)
            loadConversation(newConv.id)
            updateDrawerConversations()
            Toast.makeText(this, "Imported successfully", Toast.LENGTH_SHORT).show()
            binding.drawerLayout.close()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Import failed", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupListeners() {
        binding.sendButton.setOnClickListener { sendMessage() }
        binding.attachImageButton.setOnClickListener { checkPermissionAndSelectImage() }
        binding.removeImageButton.setOnClickListener { removeImage() }
        binding.generateImageButton.setOnClickListener { generateImage() }
        binding.modelButton.setOnClickListener { showModelSelectionDialog() }
        binding.newChatButton.setOnClickListener { createNewChatQuick() }
        binding.clearButton.setOnClickListener { clearChat() }
        binding.darkModeButton.setOnClickListener { 
            saveChatHistory()
            toggleDarkMode()
        }
        
        binding.messageInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (chatJob?.isActive != true) {
                    binding.sendButton.isEnabled = !s.isNullOrBlank()
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        
        binding.messageInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && chatMessages.isNotEmpty()) {
                val layoutManager = binding.chatRecyclerView.layoutManager as? LinearLayoutManager
                val lastVisible = layoutManager?.findLastVisibleItemPosition() ?: -1
                
                if (lastVisible >= chatMessages.size - 3) {
                    binding.chatRecyclerView.postDelayed({
                        layoutManager?.scrollToPositionWithOffset(chatMessages.size - 1, 0)
                        binding.chatRecyclerView.postDelayed({
                            binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size - 1)
                        }, 100)
                    }, 300)
                }
            }
        }
        
        var previousHeight = 0
        binding.root.viewTreeObserver.addOnGlobalLayoutListener {
            val currentHeight = binding.root.height
            if (previousHeight > 0 && currentHeight < previousHeight && chatMessages.isNotEmpty()) {
                val layoutManager = binding.chatRecyclerView.layoutManager as? LinearLayoutManager
                val lastVisible = layoutManager?.findLastVisibleItemPosition() ?: -1
                
                if (lastVisible >= chatMessages.size - 3) {
                    binding.chatRecyclerView.postDelayed({
                        layoutManager?.scrollToPositionWithOffset(chatMessages.size - 1, 0)
                        binding.chatRecyclerView.postDelayed({
                            binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size - 1)
                        }, 100)
                    }, 50)
                }
            }
            previousHeight = currentHeight
        }
        
        updateDarkModeIcon()
    }
    
    private fun updateDarkModeIcon() {
        val currentMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isDark = currentMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        binding.darkModeButton.setIconResource(if (isDark) R.drawable.ic_light_mode else R.drawable.ic_dark_mode)
    }
    
    private fun checkPermissionAndSelectImage() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            selectImage()
        } else {
            requestPermission.launch(if (android.os.Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    
    private fun selectImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImage.launch(intent)
    }
    
    private fun removeImage() {
        selectedImageUri = null
        binding.attachedImage.visibility = View.GONE
        binding.removeImageButton.visibility = View.GONE
    }
    
    private fun sendMessage() {
        val text = binding.messageInput.text.toString().trim()
        if (text.isEmpty()) return
        
        binding.sendButton.isEnabled = false
        
        try {
            val imageToSend = selectedImageUri
            val userMessage = ChatMessage(text, true, imageUri = imageToSend)
            chatMessages.add(userMessage)
            chatAdapter.notifyItemInserted(chatMessages.size - 1)
            binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size - 1)
            updateEmptyState()
            
            binding.messageInput.text?.clear()
            removeImage()
            
            // Add loading message
            val loadingMessage = ChatMessage("", false, isLoading = true)
            chatMessages.add(loadingMessage)
            val loadingPosition = chatMessages.size - 1
            chatAdapter.notifyItemInserted(loadingPosition)
            binding.chatRecyclerView.smoothScrollToPosition(loadingPosition)
            
            chatJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = callChatAPI(imageToSend)
                    withContext(Dispatchers.Main) {
                        try {
                            // Remove loading message
                            if (loadingPosition < chatMessages.size && chatMessages.getOrNull(loadingPosition)?.isLoading == true) {
                                chatMessages.removeAt(loadingPosition)
                                chatAdapter.notifyItemRemoved(loadingPosition)
                            }
                            
                            val botMessage = ChatMessage(response, false)
                            chatMessages.add(botMessage)
                            chatAdapter.notifyItemInserted(chatMessages.size - 1)
                            binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size - 1)
                            saveChatHistory()
                            binding.sendButton.isEnabled = !binding.messageInput.text.isNullOrBlank()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            binding.sendButton.isEnabled = !binding.messageInput.text.isNullOrBlank()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        try {
                            // Remove loading message
                            if (loadingPosition < chatMessages.size && chatMessages.getOrNull(loadingPosition)?.isLoading == true) {
                                chatMessages.removeAt(loadingPosition)
                                chatAdapter.notifyItemRemoved(loadingPosition)
                            }
                            Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            binding.sendButton.isEnabled = !binding.messageInput.text.isNullOrBlank()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            binding.sendButton.isEnabled = !binding.messageInput.text.isNullOrBlank()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            binding.sendButton.isEnabled = !binding.messageInput.text.isNullOrBlank()
        }
    }
    
    private fun generateImage() {
        val prompt = binding.messageInput.text.toString().trim()
        if (prompt.isEmpty()) {
            Toast.makeText(this, "Enter image description", Toast.LENGTH_SHORT).show()
            return
        }
        
        binding.sendButton.isEnabled = false
        
        try {
            val userMessage = ChatMessage("🎨 Generate: $prompt", true)
            chatMessages.add(userMessage)
            chatAdapter.notifyItemInserted(chatMessages.size - 1)
            binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size - 1)
            updateEmptyState()
            
            binding.messageInput.text?.clear()
            
            // Add loading message
            val loadingMessage = ChatMessage("Generating image...", false, isLoading = true)
            chatMessages.add(loadingMessage)
            val loadingPosition = chatMessages.size - 1
            chatAdapter.notifyItemInserted(loadingPosition)
            binding.chatRecyclerView.smoothScrollToPosition(loadingPosition)
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val imageUrl = callImageAPI(prompt)
                    withContext(Dispatchers.Main) {
                        try {
                            // Remove loading message
                            if (loadingPosition < chatMessages.size && chatMessages[loadingPosition].isLoading) {
                                chatMessages.removeAt(loadingPosition)
                                chatAdapter.notifyItemRemoved(loadingPosition)
                            }
                            
                            val botMessage = ChatMessage("Generated image:", false, generatedImageUrl = imageUrl)
                            chatMessages.add(botMessage)
                            chatAdapter.notifyItemInserted(chatMessages.size - 1)
                            binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size - 1)
                            saveChatHistory()
                            binding.sendButton.isEnabled = !binding.messageInput.text.isNullOrBlank()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        try {
                            // Remove loading message
                            if (loadingPosition < chatMessages.size && chatMessages[loadingPosition].isLoading) {
                                chatMessages.removeAt(loadingPosition)
                                chatAdapter.notifyItemRemoved(loadingPosition)
                            }
                            Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            binding.sendButton.isEnabled = !binding.messageInput.text.isNullOrBlank()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            binding.sendButton.isEnabled = !binding.messageInput.text.isNullOrBlank()
        }
    }
    
    private fun callChatAPI(imageUri: Uri?): String {
        val apiKey = prefs.getString("api_key", "") ?: ""
        val model = prefs.getString("model", "openai") ?: "openai"
        
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        
        val json = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                // Add system prompt if exists
                if (systemPrompt.isNotBlank()) {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                }
                
                // Add conversation history
                chatMessages.filter { !it.isLoading && it.generatedImageUrl == null }.forEach { msg ->
                    put(JSONObject().apply {
                        put("role", if (msg.isUser) "user" else "assistant")
                        put("content", msg.text)
                    })
                }
                
                // Add current message with image if present
                if (imageUri != null) {
                    val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                    val base64 = bitmapToBase64(bitmap)
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", chatMessages.last { it.isUser }.text)
                            })
                            put(JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().apply {
                                    put("url", "data:image/jpeg;base64,$base64")
                                })
                            })
                        })
                    })
                }
            })
        }
        
        val requestBuilder = Request.Builder()
            .url(if (apiKey.isEmpty()) PROXY_URL else API_URL)
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
    
    private fun callImageAPI(prompt: String): String {
        val apiKey = prefs.getString("api_key", "") ?: ""
        
        val client = OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
        
        // For image generation without API key, use direct pollinations.ai URL
        if (apiKey.isEmpty()) {
            val encodedPrompt = java.net.URLEncoder.encode(prompt, "UTF-8")
            return "https://image.pollinations.ai/prompt/$encodedPrompt?width=1024&height=1024&model=flux&nologo=true"
        }
        
        val json = JSONObject().apply {
            put("prompt", prompt)
            put("model", "flux")
            put("size", "1024x1024")
        }
        
        val requestBuilder = Request.Builder()
            .url(IMAGE_URL)
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
        
        requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        
        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                throw Exception("API error ${response.code}: $errorBody")
            }
            val body = response.body?.string() ?: throw Exception("Empty response")
            val jsonResponse = JSONObject(body)
            val dataArray = jsonResponse.getJSONArray("data")
            val firstItem = dataArray.getJSONObject(0)
            
            // Try to get URL first, if not available get b64_json
            return if (firstItem.has("url")) {
                firstItem.getString("url")
            } else if (firstItem.has("b64_json")) {
                "data:image/png;base64,${firstItem.getString("b64_json")}"
            } else {
                throw Exception("No image data in response")
            }
        }
    }
    
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val maxSize = 1024
        val ratio = minOf(maxSize.toFloat() / bitmap.width, maxSize.toFloat() / bitmap.height)
        val width = (bitmap.width * ratio).toInt()
        val height = (bitmap.height * ratio).toInt()
        val resized = Bitmap.createScaledBitmap(bitmap, width, height, true)
        
        val outputStream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        try {
            val method = menu.javaClass.getDeclaredMethod("setOptionalIconsVisible", Boolean::class.javaPrimitiveType)
            method.isAccessible = true
            method.invoke(menu, true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_system_prompt -> {
                showSystemPromptDialog()
                true
            }
            R.id.menu_api_key -> {
                showApiKeyDialog()
                true
            }
            R.id.menu_delete_conversation -> {
                deleteCurrentConversation()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun deleteCurrentConversation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Conversation")
            .setMessage("Delete this conversation? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                try {
                    chatJob?.cancel()
                    chatJob = null
                    conversationManager.deleteConversation(currentConversationId)
                    val conversations = conversationManager.getConversations()
                    if (conversations.isNotEmpty()) {
                        loadConversation(conversations[0].id)
                    } else {
                        val newConv = conversationManager.createNew("New Chat")
                        currentConversationId = newConv.id
                        systemPrompt = newConv.systemPrompt
                        chatMessages.clear()
                        chatAdapter.notifyDataSetChanged()
                        updateEmptyState()
                    }
                    updateDrawerConversations()
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    override fun onPause() {
        super.onPause()
        val layoutManager = binding.chatRecyclerView.layoutManager as? LinearLayoutManager
        val scrollPosition = layoutManager?.findFirstVisibleItemPosition() ?: 0
        val scrollOffset = layoutManager?.findViewByPosition(scrollPosition)?.top ?: 0
        scrollPrefs.edit()
            .putInt("pos_$currentConversationId", scrollPosition)
            .putInt("offset_$currentConversationId", scrollOffset)
            .apply()
        saveChatHistory()
    }
    
    override fun onStop() {
        super.onStop()
        saveChatHistory()
    }
    
    private fun toggleDarkMode() {
        val currentMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isDark = currentMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        
        prefs.edit()
            .putBoolean("dark_mode", !isDark)
            .putBoolean("was_at_bottom", true)
            .apply()
        
        val newMode = if (isDark) {
            AppCompatDelegate.MODE_NIGHT_NO
        } else {
            AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(newMode)
    }
    
    private fun clearChat() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Clear Chat")
            .setMessage("Delete all messages?")
            .setPositiveButton("Clear") { _, _ ->
                try {
                    chatJob?.cancel()
                    chatJob = null
                    chatMessages.clear()
                    chatAdapter.notifyDataSetChanged()
                    conversationManager.saveMessages(currentConversationId, "[]")
                    updateEmptyState()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showApiKeyDialog() {
        val dialogBinding = DialogApiKeyBinding.inflate(layoutInflater)
        val savedKey = prefs.getString("api_key", "") ?: ""
        dialogBinding.apiKeyInput.setText(savedKey)
        
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()
        
        dialogBinding.apiKeyInputLayout.isEndIconVisible = savedKey.isNotEmpty()
        
        dialogBinding.apiKeyInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                dialogBinding.apiKeyInputLayout.isEndIconVisible = !s.isNullOrEmpty()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        
        var clearClickCount = 0
        dialogBinding.apiKeyInputLayout.setEndIconOnClickListener {
            if (clearClickCount == 0) {
                clearClickCount = 1
                Toast.makeText(this, "Press again to clear API key", Toast.LENGTH_SHORT).show()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    clearClickCount = 0
                }, 3000)
            } else {
                dialogBinding.apiKeyInput.text?.clear()
                clearClickCount = 0
            }
        }
        
        dialogBinding.getApiKeyLink.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://enter.pollinations.ai"))
            startActivity(intent)
        }
        
        dialogBinding.balanceButton.setOnClickListener {
            val key = dialogBinding.apiKeyInput.text.toString()
            if (key.isEmpty()) {
                Toast.makeText(this, "Enter API key first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dialogBinding.balanceButton.isEnabled = false
            dialogBinding.balanceButton.text = "..."
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val balance = getApiBalance(key)
                    withContext(Dispatchers.Main) {
                        dialogBinding.balanceButton.isEnabled = true
                        dialogBinding.balanceButton.text = "Balance"
                        Toast.makeText(this@MainActivity, "Balance: $balance", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        dialogBinding.balanceButton.isEnabled = true
                        dialogBinding.balanceButton.text = "Balance"
                        Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        
        dialogBinding.saveButton.setOnClickListener {
            val key = dialogBinding.apiKeyInput.text.toString()
            prefs.edit().putString("api_key", key).apply()
            Toast.makeText(this, "API key saved", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        
        dialogBinding.cancelButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
    
    private fun showModelSelectionDialog() {
        val modelIds = arrayOf(
            "openai", "openai-fast", "qwen-coder", "mistral", "openai-audio", "gemini-fast", 
            "deepseek", "gemini-search", "midijourney", "claude-fast", "perplexity-fast", 
            "perplexity-reasoning", "kimi", "nova-fast", "glm", "minimax", "nomnom", "polly", 
            "qwen-safety", "step-3.5-flash", "qwen-character", "claude-airforce", "openai-seraphyn"
        )
        val modelNames = arrayOf(
            "OpenAI GPT-5 Mini - Fast & Balanced",
            "OpenAI GPT-5 Nano - Ultra Fast & Affordable",
            "Qwen3 Coder 30B - Specialized for Code Generation",
            "Mistral Small 3.2 24B - Efficient & Cost-Effective",
            "OpenAI GPT-4o Mini Audio - Voice Input & Output",
            "Google Gemini 2.5 Flash Lite - Ultra Fast & Cost-Effective",
            "DeepSeek V3.2 - Efficient Reasoning & Agentic AI",
            "Google Gemini 2.5 Flash Lite - With Google Search",
            "MIDIjourney - AI Music Composition Assistant",
            "Anthropic Claude Haiku 4.5 - Fast & Intelligent",
            "Perplexity Sonar - Fast & Affordable with Web Search",
            "Perplexity Sonar Reasoning - Advanced Reasoning with Web Search",
            "Moonshot Kimi K2.5 - Flagship Agentic Model with Vision & Multi-Agent",
            "Amazon Nova Micro - Ultra Fast & Ultra Cheap",
            "Z.ai GLM-5 - 744B MoE, Long Context Reasoning & Agentic Workflows",
            "MiniMax M2.5 - Coding, Agentic & Multi-Language",
            "NomNom by @Itachi-1824 - Web Research with Search, Scrape & Crawl (Alpha)",
            "Polly by @Itachi-1824 - Pollinations AI Assistant with GitHub, Code Search & Web Tools (Alpha)",
            "Qwen3Guard 8B - Content Safety & Moderation (OVH)",
            "Step 3.5 Flash (api.airforce) - Fast reasoning model",
            "Qwen Character (api.airforce) - roleplay & character chat",
            "Claude Sonnet 4.6 (api.airforce) - Anthropic's balanced model via community provider",
            "GPT-5.4 (seraphyn.ai) - OpenAI's latest model via community provider"
        )
        val currentModel = prefs.getString("model", "openai")
        val selectedIndex = modelIds.indexOf(currentModel)
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Select Model")
            .setSingleChoiceItems(modelNames, selectedIndex) { dialog, which ->
                prefs.edit().putString("model", modelIds[which]).apply()
                Toast.makeText(this, modelNames[which], Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun getApiBalance(apiKey: String): String {
        if (apiKey.isEmpty()) return "Using default API"
        
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        
        val request = Request.Builder()
            .url("https://gen.pollinations.ai/account/balance")
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return "Unknown"
            val body = response.body?.string() ?: return "Unknown"
            val jsonResponse = JSONObject(body)
            val balance = jsonResponse.optDouble("balance", -1.0)
            return if (balance >= 0) String.format("%.5f Pollen", balance) else "Active"
        }
    }
}
