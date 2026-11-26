package com.example.btchat.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.btchat.domain.ChatMessage
import com.example.btchat.domain.MessageStatus

@Entity(
    tableName = "messages",
    indices = [Index(value = ["deviceAddress", "timestamp"])]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceAddress: String,
    val deviceName: String,
    val text: String,
    val timestamp: Long,
    val isFromLocalUser: Boolean,
    val status: String
)

fun MessageEntity.toDomain() = ChatMessage(
    id = id,
    deviceAddress = deviceAddress,
    deviceName = deviceName,
    text = text,
    timestamp = timestamp,
    isFromLocalUser = isFromLocalUser,
    status = MessageStatus.valueOf(status)
)

fun ChatMessage.toEntity() = MessageEntity(
    id = id,
    deviceAddress = deviceAddress,
    deviceName = deviceName,
    text = text,
    timestamp = timestamp,
    isFromLocalUser = isFromLocalUser,
    status = status.name
)
