package com.example.tars

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tars.databinding.ItemMessageBinding

class MessageAdapter : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {
    private val messages = mutableListOf<Message>()

    fun addMessage(message: Message) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }
    
    fun removeMessage(message: Message) {
        val position = messages.indexOf(message)
        if (position != -1) {
            messages.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount() = messages.size

    class MessageViewHolder(private val binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            binding.messageText.text = message.text
            binding.timestampText.text = message.timestamp
            
            // Set different background colors for user and bot messages
            binding.messageCard.setCardBackgroundColor(
                if (message.isUser) 0xFF2196F3.toInt() else 0xFF424242.toInt()
            )
            
            // Align messages differently
            binding.messageCard.layoutParams = (binding.messageCard.layoutParams as ViewGroup.MarginLayoutParams).apply {
                marginStart = if (message.isUser) 64 else 16
                marginEnd = if (message.isUser) 16 else 64
            }
        }
    }
} 