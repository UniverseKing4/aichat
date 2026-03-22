package com.aichat.app

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aichat.app.databinding.ItemConversationBinding

class ConversationAdapter(
    private val conversations: List<Conversation>,
    private val currentId: String,
    private val onClick: (Conversation) -> Unit,
    private val onLongClick: (Conversation) -> Unit
) : RecyclerView.Adapter<ConversationAdapter.ViewHolder>() {

    var isSelectionMode = false
    val selectedItems = mutableSetOf<String>()

    inner class ViewHolder(private val binding: ItemConversationBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(conv: Conversation) {
            binding.conversationName.text = conv.name
            binding.conversationName.textSize = 14f
            
            if (conv.id == currentId && !isSelectionMode) {
                binding.conversationName.setTypeface(null, Typeface.BOLD)
                binding.root.alpha = 1.0f
            } else {
                binding.conversationName.setTypeface(null, Typeface.NORMAL)
                binding.root.alpha = 0.7f
            }
            
            binding.conversationCheckbox.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            binding.conversationCheckbox.isChecked = selectedItems.contains(conv.id)
            
            binding.conversationCheckbox.setOnClickListener {
                toggleSelection(conv.id)
                notifyItemChanged(adapterPosition)
            }
            
            binding.root.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(conv.id)
                    notifyItemChanged(adapterPosition)
                } else {
                    onClick(conv)
                }
            }
            
            binding.root.setOnLongClickListener {
                if (!isSelectionMode) {
                    onLongClick(conv)
                }
                true
            }
        }
    }
    
    fun toggleSelection(id: String) {
        if (selectedItems.contains(id)) {
            selectedItems.remove(id)
        } else {
            selectedItems.add(id)
        }
    }
    
    fun clearSelection() {
        selectedItems.clear()
        isSelectionMode = false
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemConversationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(conversations[position])

    override fun getItemCount() = conversations.size
}
