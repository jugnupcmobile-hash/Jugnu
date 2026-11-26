package com.example.btchat.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM messages WHERE deviceAddress = :address ORDER BY timestamp ASC")
    fun observeMessagesForDevice(address: String): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT m.* FROM messages m
        INNER JOIN (
            SELECT deviceAddress, MAX(timestamp) AS maxTime
            FROM messages
            GROUP BY deviceAddress
        ) latest
        ON m.deviceAddress = latest.deviceAddress AND m.timestamp = latest.maxTime
        ORDER BY m.timestamp DESC
        """
    )
    fun observeRecentConversations(): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateMessageStatus(id: Long, status: String)
}
