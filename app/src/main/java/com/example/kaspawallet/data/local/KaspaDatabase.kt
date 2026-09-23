package com.example.kaspawallet.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import com.example.kaspawallet.data.model.*

@Database(
    entities = [
        WalletEntity::class,
        AccountEntity::class,
        TransactionEntity::class,
        ContactEntity::class,
        UtxoEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class KaspaDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun contactDao(): ContactDao
    abstract fun utxoDao(): UtxoDao

    companion object {
        @Volatile
        private var INSTANCE: KaspaDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE wallets ADD COLUMN encryptedPassphrase TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS utxos (
                        accountId TEXT NOT NULL,
                        outpointTxId TEXT NOT NULL,
                        outpointIndex INTEGER NOT NULL,
                        amountSompi INTEGER NOT NULL,
                        scriptPublicKey TEXT NOT NULL,
                        blockDaaScore INTEGER NOT NULL,
                        isCoinbase INTEGER NOT NULL,
                        address TEXT NOT NULL DEFAULT '',
                        isSpent INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(accountId, outpointTxId, outpointIndex)
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): KaspaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KaspaDatabase::class.java,
                    "kaspa_wallet.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
