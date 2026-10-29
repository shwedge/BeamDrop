package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.TransferRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: TransferRecord): Long

    @Query("SELECT * FROM transfers ORDER BY timestamp DESC")
    fun getAllTransfers(): Flow<List<TransferRecord>>

    @Query("SELECT * FROM transfers WHERE direction = :direction ORDER BY timestamp DESC")
    fun getTransfersByDirection(direction: String): Flow<List<TransferRecord>>

    @Query("SELECT * FROM transfers WHERE status = :status ORDER BY timestamp DESC")
    fun getTransfersByStatus(status: String): Flow<List<TransferRecord>>

    @Query("DELETE FROM transfers WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transfers")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM transfers")
    suspend fun getTransferCount(): Int

    @Query("SELECT SUM(fileSize) FROM transfers WHERE status = 'COMPLETED'")
    fun getTotalBytesTransferred(): Flow<Long?>
}
