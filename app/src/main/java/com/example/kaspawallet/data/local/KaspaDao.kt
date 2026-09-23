package com.example.kaspawallet.data.local

import androidx.room.*
import com.example.kaspawallet.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {
    @Query("SELECT * FROM wallets ORDER BY createdAt DESC")
    fun getAllWallets(): Flow<List<WalletEntity>>

    @Query("SELECT * FROM wallets")
    fun getAllWalletsSync(): List<WalletEntity>

    @Query("SELECT * FROM wallets WHERE id = :walletId LIMIT 1")
    fun getWalletById(walletId: String): WalletEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertWallet(wallet: WalletEntity): Long

    @Update
    fun updateWallet(wallet: WalletEntity): Int

    @Query("DELETE FROM wallets WHERE id = :walletId")
    fun deleteWallet(walletId: String): Int
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE walletId = :walletId ORDER BY accountIndex ASC")
    fun getAccountsForWallet(walletId: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE walletId = :walletId ORDER BY accountIndex ASC")
    fun getAccountsForWalletSync(walletId: String): List<AccountEntity>

    @Query("SELECT * FROM accounts")
    fun getAllAccountsSync(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :accountId LIMIT 1")
    fun getAccountById(accountId: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAccount(account: AccountEntity): Long

    @Update
    fun updateAccount(account: AccountEntity): Int

    @Query("UPDATE accounts SET balanceSompi = :balance WHERE id = :accountId")
    fun updateBalance(accountId: String, balance: Long): Int

    @Query("DELETE FROM accounts WHERE id = :accountId")
    fun deleteAccount(accountId: String): Int

    @Query("DELETE FROM accounts WHERE walletId = :walletId")
    fun deleteAccountsForWallet(walletId: String): Int
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE walletId = :walletId ORDER BY timestamp DESC")
    fun getTransactionsForWallet(walletId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY timestamp DESC")
    fun getTransactionsForAccount(accountId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :txId LIMIT 1")
    fun getTransactionById(txId: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTransaction(transaction: TransactionEntity): Long

    @Query("DELETE FROM transactions WHERE walletId = :walletId")
    fun deleteTransactionsForWallet(walletId: String): Int
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertContact(contact: ContactEntity): Long

    @Delete
    fun deleteContact(contact: ContactEntity): Int
}

@Dao
interface UtxoDao {
    @Query("SELECT * FROM utxos WHERE accountId = :accountId AND isSpent = 0 ORDER BY amountSompi DESC")
    fun getUnspentUtxosForAccount(accountId: String): Flow<List<UtxoEntity>>

    @Query("SELECT * FROM utxos WHERE accountId = :accountId AND isSpent = 0 ORDER BY amountSompi DESC")
    fun getUnspentUtxosForAccountSync(accountId: String): List<UtxoEntity>

    @Query("SELECT * FROM utxos WHERE accountId = :accountId ORDER BY amountSompi DESC")
    fun getAllUtxosForAccount(accountId: String): Flow<List<UtxoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertUtxos(utxos: List<UtxoEntity>)

    @Query("UPDATE utxos SET isSpent = 1, updatedAt = :timestamp WHERE accountId = :accountId AND outpointTxId = :txId AND outpointIndex = :index")
    fun markSpent(accountId: String, txId: String, index: Int, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM utxos WHERE accountId = :accountId AND outpointTxId = :txId AND outpointIndex = :index")
    fun deleteUtxo(accountId: String, txId: String, index: Int)

    @Query("DELETE FROM utxos WHERE accountId = :accountId AND isSpent = 1")
    fun deleteSpentForAccount(accountId: String)

    @Query("DELETE FROM utxos WHERE accountId = :accountId")
    fun deleteAllForAccount(accountId: String)
}
