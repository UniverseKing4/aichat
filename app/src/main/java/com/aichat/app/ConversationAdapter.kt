package com.aichat.app

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aichat.app.databinding.ItemConversationBinding

class ConversationAdapter(
    private val conversations: List<Conversation>,
    private val currentId: String,
    private val onClick: (Conversation) -> Unit,
    private val onLongClick: (Conversation) -> Unit
) : RecyclerView.Adapter<ConversationAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemConversationBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(conv: Conversation) {
            binding.conversationName.text = conv.name
            binding.conversationName.textSize = 14f
            if (conv.id == currentId) {
                binding.conversationName.setTypeface(null, Typeface.BOLD)
                binding.root.alpha = 1.0f
            } else {
                binding.conversationName.setTypeface(null, Typeface.NORMAL)
                binding.root.alpha = 0.7f
            }
            binding.root.setOnClickListener { onClick(conv) }
            binding.root.setOnLongClickListener { onLongClick(conv); true }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemConversationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(conversations[position])

    override fun getItemCount() = conversations.size
}
