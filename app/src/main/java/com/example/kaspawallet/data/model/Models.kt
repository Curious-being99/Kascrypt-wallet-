package com.example.kaspawallet.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class KaspaNetwork(val displayName: String, val prefix: String, val defaultRpc: String) {
    MAINNET("Kaspa Mainnet", "kaspa:", "https://api.kaspa.org"),
    TESTNET_10("Testnet 10", "kaspatest:", "https://api-tn10.kaspa.org"),
    TESTNET_11("Testnet 11 (10bps)", "kaspatest:", "https://api-tn11.kaspa.org"),
    DEVNET("Devnet", "kaspadev:", "http://10.0.2.2:16210"),
    SIMNET("Simnet", "kaspasim:", "http://10.0.2.2:16510");

    fun formatAddress(rawAddress: String): String {
        return if (rawAddress.startsWith(prefix)) rawAddress else "$prefix$rawAddress"
    }
}

@Entity(tableName = "wallets")
@Serializable
data class WalletEntity(
    @PrimaryKey val id: String,
    val name: String,
    val encryptedMnemonic: String,
    val wordCount: Int = 12,
    val hasPassphrase: Boolean = false,
    val isLocked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "accounts")
@Serializable
data class AccountEntity(
    @PrimaryKey val id: String,
    val walletId: String,
    val accountIndex: Int,
    val name: String,
    val address: String,
    val balanceSompi: Long = 0L,
    val accountType: String = "BIP44 Standard",
    val derivationPath: String = "m/44'/111111'/0'/0/0",
    val colorIndex: Int = 0
)

enum class TransactionType {
    SEND,
    RECEIVE,
    TRANSFER,
    COMPOUND
}

enum class TransactionStatus {
    PENDING,
    CONFIRMED,
    FAILED
}

@Entity(tableName = "transactions")
@Serializable
data class TransactionEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val walletId: String,
    val txType: TransactionType,
    val amountSompi: Long,
    val feeSompi: Long,
    val recipientAddress: String,
    val senderAddress: String,
    val daaScore: Long,
    val timestamp: Long,
    val note: String = "",
    val status: TransactionStatus = TransactionStatus.CONFIRMED,
    val network: String = KaspaNetwork.MAINNET.name
)

@Entity(tableName = "contacts")
@Serializable
data class ContactEntity(
    @PrimaryKey val id: String,
    val name: String,
    val address: String,
    val network: String = KaspaNetwork.MAINNET.name,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class UtxoEntry(
    val outpointTxId: String,
    val outpointIndex: Int,
    val amountSompi: Long,
    val scriptPublicKey: String,
    val blockDaaScore: Long,
    val isCoinbase: Boolean = false
)

@Serializable
data class BlockDagInfo(
    val networkName: String = "kaspa-mainnet",
    val blockCount: Long = 0L,
    val headerCount: Long = 0L,
    val tipHashes: List<String> = emptyList(),
    val difficulty: Double = 0.0,
    val pastMedianTime: Long = System.currentTimeMillis(),
    val virtualDaaScore: Long = 0L,
    val hashratePhPerSec: Double = 0.0,
    val currentRewardKas: Double = 0.0,
    val connectedPeers: Int = 0,
    val nodeLatencyMs: Long = 0L,
    val nodeVersion: String = "v2.0.1 (Rusty Kaspa)"
)

@Serializable
data class KaspaMarketInfo(
    val priceUsd: Double = 0.0361,
    val change24hPercent: Double = -0.71,
    val marketCapUsd: Long = 910000000L,
    val volume24hUsd: Long = 42000000L,
    val circulatingSupply: Double = 25200000000.0,
    val rank: Int = 28
)
