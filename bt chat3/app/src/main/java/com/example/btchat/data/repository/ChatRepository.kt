package com.example.btchat.data.repository

import com.example.btchat.data.local.ChatDao
import com.example.btchat.data.local.toDomain
import com.example.btchat.data.local.toEntity
import com.example.btchat.domain.ChatMessage
import com.example.btchat.domain.MessageStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepository(private val chatDao: ChatDao) {

    fun observeConversations(): Flow<List<ChatMessage>> =
        chatDao.observeRecentConversations().map { list -> list.map { it.toDomain() } }

    fun observeMessages(deviceAddress: String): Flow<List<ChatMessage>> =
        chatDao.observeMessagesForDevice(deviceAddress).map { list -> list.map { it.toDomain() } }

    suspend fun saveMessage(message: ChatMessage): Long =
        chatDao.insertMessage(message.toEntity())

    suspend fun updateStatus(messageId: Long, status: MessageStatus) {
        chatDao.updateMessageStatus(messageId, status.name)
    }
}
