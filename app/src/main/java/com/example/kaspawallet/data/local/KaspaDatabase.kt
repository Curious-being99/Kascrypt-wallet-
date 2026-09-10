package com.example.kaspawallet.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.kaspawallet.data.model.*

@Database(
    entities = [
        WalletEntity::class,
        AccountEntity::class,
        TransactionEntity::class,
        ContactEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class KaspaDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun contactDao(): ContactDao

    companion object {
        @Volatile
        private var INSTANCE: KaspaDatabase? = null

        fun getDatabase(context: Context): KaspaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KaspaDatabase::class.java,
                    "kaspa_wallet.db"
                ).fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
