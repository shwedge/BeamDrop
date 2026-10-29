package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.TransferDao
import com.example.data.model.TransferRecord

@Database(entities = [TransferRecord::class], version = 2, exportSchema = false)
abstract class BeamDropDatabase : RoomDatabase() {
    abstract fun transferDao(): TransferDao

    companion object {
        @Volatile
        private var INSTANCE: BeamDropDatabase? = null

        fun getDatabase(context: Context): BeamDropDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BeamDropDatabase::class.java,
                    "beamdrop_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
