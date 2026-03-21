package com.aichat.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aichat.app.databinding.ItemMessageUserBinding
import com.aichat.app.databinding.ItemMessageBotBinding
import com.bumptech.glide.Glide

class ChatAdapter(private val messages: List<ChatMessage>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    
    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_BOT = 2
    }
    
    var onEditClick: ((Int, String) -> Unit)? = null
    
    override fun getItemViewType(position: Int) = if (messages[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_BOT
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_USER) {
            val binding = ItemMessageUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            UserViewHolder(binding)
        } else {
            val binding = ItemMessageBotBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            BotViewHolder(binding)
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        if (holder is UserViewHolder) {
            holder.bind(message, position, onEditClick)
        } else if (holder is BotViewHolder) {
            holder.bind(message, position, onEditClick)
        }
    }
    
    override fun getItemCount() = messages.size
    
    class UserViewHolder(private val binding: ItemMessageUserBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage, position: Int, onEditClick: ((Int, String) -> Unit)?) {
            binding.messageText.text = message.text
            if (message.imageUri != null) {
                binding.attachedImage.visibility = View.VISIBLE
                binding.attachedImage.setImageURI(message.imageUri)
            } else {
                binding.attachedImage.visibility = View.GONE
            }
            
            binding.editButton.setOnClickListener {
                onEditClick?.invoke(position, message.text)
            }
            
            binding.copyButton.setOnClickListener {
                val clipboard = binding.root.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Message", message.text)
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(binding.root.context, "Copied", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    class BotViewHolder(private val binding: ItemMessageBotBinding) : RecyclerView.ViewHolder(binding.root) {
        private val markwon by lazy { 
            io.noties.markwon.Markwon.builder(binding.root.context)
                .usePlugin(io.noties.markwon.linkify.LinkifyPlugin.create())
                .build()
        }
        
        fun bind(message: ChatMessage, position: Int, onEditClick: ((Int, String) -> Unit)?) {
            if (message.isLoading) {
                binding.messageText.visibility = View.GONE
                binding.generatedImage.visibility = View.GONE
                binding.loadingIndicator.visibility = View.VISIBLE
                binding.editButton.visibility = View.GONE
                binding.copyButton.visibility = View.GONE
            } else {
                binding.messageText.visibility = View.VISIBLE
                markwon.setMarkdown(binding.messageText, message.text)
                binding.loadingIndicator.visibility = View.GONE
                binding.editButton.visibility = View.VISIBLE
                binding.copyButton.visibility = View.VISIBLE
                if (message.generatedImageUrl != null) {
                    binding.generatedImage.visibility = View.VISIBLE
                    Glide.with(binding.root.context)
                        .load(message.generatedImageUrl)
                        .into(binding.generatedImage)
                } else {
                    binding.generatedImage.visibility = View.GONE
                }
            }
            
            binding.editButton.setOnClickListener {
                onEditClick?.invoke(position, message.text)
            }
            
            binding.copyButton.setOnClickListener {
                val clipboard = binding.root.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Message", message.text)
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(binding.root.context, "Copied", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}
