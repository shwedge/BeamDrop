package com.example.data.repository

import com.example.data.dao.TransferDao
import com.example.data.model.TransferRecord
import kotlinx.coroutines.flow.Flow

class TransferRepository(private val transferDao: TransferDao) {
    val allTransfers: Flow<List<TransferRecord>> = transferDao.getAllTransfers()

    val totalBytesTransferred: Flow<Long?> = transferDao.getTotalBytesTransferred()

    suspend fun logTransfer(record: TransferRecord): Long {
        return transferDao.insert(record)
    }

    suspend fun deleteRecord(id: Long) {
        transferDao.deleteById(id)
    }

    suspend fun clearAllHistory() {
        transferDao.clearAll()
    }
}
