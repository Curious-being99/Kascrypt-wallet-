package com.example.kaspawallet.data.repository

import android.util.Log
import com.example.kaspawallet.data.api.KaspaApiClient
import com.example.kaspawallet.data.crypto.KaspaCrypto
import com.example.kaspawallet.data.crypto.KaspaMlDsa
import com.example.kaspawallet.data.crypto.KaspaSigner
import com.example.kaspawallet.data.crypto.KaspaUtils
import com.example.kaspawallet.data.local.KaspaDatabase
import com.example.kaspawallet.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class KaspaWalletRepository(
    val database: KaspaDatabase,
    val apiClient: KaspaApiClient = KaspaApiClient()
) {
    private val sendTransactionMutex = Mutex()
    val allWallets: Flow<List<WalletEntity>> = database.walletDao().getAllWallets()
    val allContacts: Flow<List<ContactEntity>> = database.contactDao().getAllContacts()

    private val _currentNetwork = MutableStateFlow(KaspaNetwork.MAINNET)
    val currentNetwork: StateFlow<KaspaNetwork> = _currentNetwork.asStateFlow()

    private val _blockDagInfo = MutableStateFlow(BlockDagInfo())
    val blockDagInfo: StateFlow<BlockDagInfo> = _blockDagInfo.asStateFlow()

    private val _marketInfo = MutableStateFlow(KaspaMarketInfo())
    val marketInfo: StateFlow<KaspaMarketInfo> = _marketInfo.asStateFlow()

    private val _activeWalletId = MutableStateFlow<String?>(null)
    val activeWalletId: StateFlow<String?> = _activeWalletId.asStateFlow()

    private val _activeAccountId = MutableStateFlow<String?>(null)
    val activeAccountId: StateFlow<String?> = _activeAccountId.asStateFlow()

    // UTXOs state mapped by accountId
    private val _accountUtxos = MutableStateFlow<Map<String, List<UtxoEntry>>>(emptyMap())
    val accountUtxos: StateFlow<Map<String, List<UtxoEntry>>> = _accountUtxos.asStateFlow()

    // Outpoints currently spent in recently broadcasted transactions (key: "txId:index", value: timestamp)
    private val pendingSpentOutpoints = ConcurrentHashMap<String, Long>()

    // Pending change UTXOs mapped by accountId -> list of UtxoEntry
    private val pendingChangeUtxos = ConcurrentHashMap<String, MutableList<UtxoEntry>>()

    @Volatile
    private var activeWebSocket: okhttp3.WebSocket? = null

    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var activeSessionPassword: String = ""

    fun setActiveSessionPassword(pwd: String) {
        activeSessionPassword = pwd
    }

    fun getWalletMnemonicWords(wallet: WalletEntity?, explicitPassword: String? = null): List<String> {
        val stored = wallet?.encryptedMnemonic ?: return emptyList()
        val pwd = explicitPassword ?: activeSessionPassword
        val words = KaspaCrypto.decryptMnemonic(stored, pwd)
        if (words.isNotEmpty()) return words
        // Fallback: try decrypting with empty password in case it was stored with device key
        return KaspaCrypto.decryptMnemonic(stored, "")
    }

    fun getWalletPassphrase(wallet: WalletEntity?, explicitPassword: String? = null): String {
        if (wallet == null || !wallet.hasPassphrase || wallet.encryptedPassphrase.isBlank()) return ""
        val pwd = explicitPassword ?: activeSessionPassword
        return KaspaCrypto.decryptPassphrase(wallet.encryptedPassphrase, pwd)
    }

    fun getWalletSeed(wallet: WalletEntity?, explicitPassword: String? = null, explicitWords: List<String>? = null): ByteArray {
        val words = if (explicitWords != null && explicitWords.isNotEmpty()) explicitWords else getWalletMnemonicWords(wallet, explicitPassword)
        if (words.isEmpty()) {
            throw IllegalStateException("Unable to decrypt wallet seed phrase. Please unlock your wallet with your password or seed phrase.")
        }
        val passphrase = getWalletPassphrase(wallet, explicitPassword)
        return KaspaCrypto.mnemonicToSeed(words, passphrase)
    }

    init {
        // Start background live sync for BlockDAG metrics and Market Price
        startPeriodicSync()
    }

    private fun startPeriodicSync() {
        // Real-time WebSocket connection to stream address UTXO/Tx events
        repositoryScope.launch {
            combine(_currentNetwork, _activeAccountId) { net, accId -> Pair(net, accId) }
                .collectLatest { (network, accId) ->
                    restartWebSocket(network, accId)
                }
        }

        repositoryScope.launch {
            while (isActive) {
                try {
                    syncNetworkMetrics()
                    syncMarketPrice()

                    // If active account exists, sync its live on-chain balance & UTXOs
                    val currentAccId = _activeAccountId.value
                    if (currentAccId != null) {
                        syncAccountOnChain(currentAccId)
                    }
                } catch (e: Exception) {
                    Log.w("KaspaWalletRepository", "Periodic sync warning: ${e.message}")
                }
                delay(4000) // Responsive 4-second polling for quick on-chain updates
            }
        }
    }

    private fun restartWebSocket(network: KaspaNetwork, accId: String?) {
        try {
            activeWebSocket?.close(1000, "Switching account/network")
            activeWebSocket = null
        } catch (e: Exception) {
            Log.d("KaspaWalletRepository", "WS close note: ${e.message}")
        }

        if (accId == null) return

        val account = database.accountDao().getAccountById(accId) ?: return
        val address = KaspaUtils.formatAddressForNetwork(account.address, network)

        val listener = object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
                Log.i("KaspaWalletRepository", "WebSocket connected to $network for $address")
                try {
                    val subMsg = JSONObject().apply {
                        put("type", "subscribe")
                        put("topic", "utxos-changed")
                        put("address", address)
                    }
                    webSocket.send(subMsg.toString())
                } catch (e: Exception) {
                    Log.d("KaspaWalletRepository", "WS subscribe error: ${e.message}")
                }
            }

            override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                Log.d("KaspaWalletRepository", "WS event received for $address: $text")
                repositoryScope.launch {
                    syncAccountOnChain(accId)
                }
            }

            override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) {
                Log.d("KaspaWalletRepository", "WS disconnected/failed: ${t.message}")
            }
        }

        activeWebSocket = apiClient.openWebSocket(network, listener)
    }

    suspend fun syncNetworkMetrics() {
        try {
            val liveDag = apiClient.fetchBlockDagInfo(_currentNetwork.value)
            if (liveDag.blockCount > 0) {
                _blockDagInfo.value = liveDag
            }
        } catch (e: Exception) {
            Log.w("KaspaWalletRepository", "Network metrics update skipped: ${e.message}")
        }
    }

    suspend fun syncMarketPrice() {
        val liveMarket = apiClient.fetchMarketPrice()
        if (liveMarket.priceUsd > 0) {
            _marketInfo.value = liveMarket
        }
    }

    fun setAccountUtxos(accountId: String, utxos: List<UtxoEntry>) {
        _accountUtxos.update { current ->
            current + (accountId to utxos)
        }
    }

    suspend fun syncAccountOnChain(accountId: String) = withContext(Dispatchers.IO) {
        val account = database.accountDao().getAccountById(accountId) ?: return@withContext
        val network = _currentNetwork.value
        val primaryAddress = KaspaUtils.formatAddressForNetwork(account.address, network)
        val allDiscoveredUtxos = mutableListOf<UtxoEntry>()

        // 1. Fetch live UTXOs & real balance from primary address
        val primaryUtxos = apiClient.fetchAddressUtxos(primaryAddress, network)
        allDiscoveredUtxos.addAll(primaryUtxos)
        val primaryBal = apiClient.fetchAddressBalance(primaryAddress, network)

        // Derive known account addresses to correctly categorize SEND vs RECEIVE transactions
        val wallet = database.walletDao().getWalletById(account.walletId)
        val words = getWalletMnemonicWords(wallet)
        val passphrase = getWalletPassphrase(wallet)
        val knownAddresses = mutableSetOf(account.address, primaryAddress)
        if (words.isNotEmpty()) {
            for (branch in 0..1) {
                for (idx in 0 until 30) {
                    val addr = if (branch == 0) {
                        KaspaCrypto.deriveKaspaAddress(words, account.accountIndex, idx, network, passphrase)
                    } else {
                        KaspaCrypto.deriveKaspaChangeAddress(words, account.accountIndex, idx, network, passphrase)
                    }
                    if (addr.isNotBlank()) {
                        knownAddresses.add(addr)
                        knownAddresses.add(KaspaUtils.formatAddressForNetwork(addr, network))
                    }
                }
            }
        }

        // 2. Fetch real Transactions for primary address
        val liveTxs = apiClient.fetchAddressTransactions(primaryAddress, account.walletId, accountId, network, knownAddresses)
        if (liveTxs.isNotEmpty()) {
            for (tx in liveTxs) {
                saveOrMergeTransaction(tx)
            }
        }

        // 3. Scan & Auto-recover funds sitting on secondary address indices (up to 30 limit)
        var totalSecondarySompi = 0L
        try {
            val wallet = database.walletDao().getWalletById(account.walletId)
            val words = getWalletMnemonicWords(wallet)
            val passphrase = getWalletPassphrase(wallet)
            if (words.isNotEmpty()) {
                val seed = getWalletSeed(wallet)
                try {
                    val gapLimit = 30
                    val branchesToScan = listOf(
                        0 to (1 until gapLimit).toList(), // m/44'/111111'/0'/0/1..29
                        1 to (0 until gapLimit).toList()  // m/44'/111111'/0'/1/0..29
                    )

                    for ((branch, indices) in branchesToScan) {
                        for (addrIdx in indices) {
                            val derivedAddr = if (branch == 0) {
                                KaspaCrypto.deriveKaspaAddress(words, account.accountIndex, addrIdx, network, passphrase)
                            } else {
                                KaspaCrypto.deriveKaspaChangeAddress(words, account.accountIndex, addrIdx, network, passphrase)
                            }

                            if (derivedAddr.isNotBlank() && derivedAddr != account.address && derivedAddr != primaryAddress) {
                                val utxos = apiClient.fetchAddressUtxos(derivedAddr, network)
                                if (utxos.isNotEmpty()) {
                                    allDiscoveredUtxos.addAll(utxos)
                                    val totalSompi = utxos.sumOf { it.amountSompi }
                                    totalSecondarySompi += totalSompi
                                    val mass = KaspaSigner.calculateTransactionMass(utxos.size, 1)
                                    val feeSompi = KaspaSigner.calculateMinimumFeeSompi(mass)
                                    if (totalSompi > feeSompi) {
                                        val sweepAmount = totalSompi - feeSompi
                                        val (signedSweepTx, sweepTxId) = KaspaSigner.createAndSignTransaction(
                                            seed = seed,
                                            accountIndex = account.accountIndex,
                                            inputs = utxos,
                                            recipientAddress = account.address,
                                            amountSompi = sweepAmount,
                                            feeSompi = feeSompi,
                                            changeAddress = account.address,
                                            network = network,
                                            inputBranch = branch,
                                            inputAddressIndex = addrIdx
                                        )
                                        val (sweepOk, _) = apiClient.broadcastTransaction(signedSweepTx, network)
                                        if (sweepOk) {
                                            Log.i("KaspaWalletRepository", "Swept funds from $derivedAddr (branch $branch idx $addrIdx) to primary: $sweepTxId")
                                        }
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    seed.fill(0)
                }
            }
        } catch (e: Exception) {
            Log.d("KaspaWalletRepository", "Multi-index auto-recovery check: ${e.message}")
        }

        // 4. Update confirmed balance and UTXO pool
        // Prune pending spent outpoints older than 20 seconds (fast, adaptive release)
        val now = System.currentTimeMillis()
        val expiredThreshold = now - 20_000L
        pendingSpentOutpoints.entries.removeIf { it.value < expiredThreshold }

        // If any pending change outputs for this account have arrived in allDiscoveredUtxos, remove them from pendingChangeUtxos
        val accountChangeList = pendingChangeUtxos[accountId]
        if (accountChangeList != null) {
            synchronized(accountChangeList) {
                accountChangeList.removeAll { change ->
                    allDiscoveredUtxos.any { it.outpointTxId == change.outpointTxId && it.outpointIndex == change.outpointIndex }
                }
            }
        }

        // Filter out UTXOs that are in pendingSpentOutpoints (prevents lagging API from resurrecting spent UTXOs)
        val unspentUtxos = allDiscoveredUtxos.filterNot {
            pendingSpentOutpoints.containsKey("${it.outpointTxId}:${it.outpointIndex}")
        }.toMutableList()

        // Include any pending change UTXOs that haven't arrived yet from the indexer
        val remainingPendingChange = pendingChangeUtxos[accountId]?.toList() ?: emptyList()
        for (change in remainingPendingChange) {
            if (!unspentUtxos.any { it.outpointTxId == change.outpointTxId && it.outpointIndex == change.outpointIndex }) {
                unspentUtxos.add(change)
            }
        }

        val distinctUtxos = unspentUtxos.distinctBy { "${it.outpointTxId}:${it.outpointIndex}" }
        val utxoSum = distinctUtxos.sumOf { it.amountSompi }

        // If there are active pending local operations (spents or change), prioritize utxoSum to prevent stale API balance from overwriting deducted balance
        val hasPendingLocalOps = pendingSpentOutpoints.isNotEmpty() || (pendingChangeUtxos[accountId]?.isNotEmpty() == true)
        val finalBalance = if (hasPendingLocalOps) utxoSum else maxOf(primaryBal ?: 0L, utxoSum)

        database.accountDao().updateBalance(accountId, finalBalance)

        _accountUtxos.update { current ->
            current + (accountId to distinctUtxos)
        }
    }

    suspend fun recoverChangeAddressFunds(
        account: AccountEntity,
        branch: Int = 1,
        addressIndex: Int = 0
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val wallet = database.walletDao().getWalletById(account.walletId)
            ?: return@withContext Pair(false, "Wallet not found")
        val words = getWalletMnemonicWords(wallet)
        val passphrase = getWalletPassphrase(wallet)
        if (words.isEmpty()) {
            return@withContext Pair(false, "Cannot decrypt seed words")
        }
        val network = _currentNetwork.value
        val derivedAddr = if (branch == 0) {
            KaspaCrypto.deriveKaspaAddress(words, account.accountIndex, addressIndex, network, passphrase)
        } else {
            KaspaCrypto.deriveKaspaChangeAddress(words, account.accountIndex, addressIndex, network, passphrase)
        }

        val utxos = apiClient.fetchAddressUtxos(derivedAddr, network)
        if (utxos.isEmpty()) {
            return@withContext Pair(false, "No unspent funds found on $derivedAddr")
        }

        val totalSompi = utxos.sumOf { it.amountSompi }
        val mass = KaspaSigner.calculateTransactionMass(utxos.size, 1)
        val feeSompi = KaspaSigner.calculateMinimumFeeSompi(mass)
        if (totalSompi <= feeSompi) {
            return@withContext Pair(false, "Balance (${KaspaUtils.formatSompi(totalSompi)} KAS) is too low to cover network fee (${KaspaUtils.formatSompi(feeSompi)} KAS)")
        }

        val sweepAmount = totalSompi - feeSompi
        val seed = getWalletSeed(wallet)
        val (signedTx, txId) = try {
            KaspaSigner.createAndSignTransaction(
                seed = seed,
                accountIndex = account.accountIndex,
                inputs = utxos,
                recipientAddress = account.address,
                amountSompi = sweepAmount,
                feeSompi = feeSompi,
                changeAddress = account.address,
                network = network,
                inputBranch = branch,
                inputAddressIndex = addressIndex
            )
        } finally {
            seed.fill(0)
        }

        val (success, msg) = apiClient.broadcastTransaction(signedTx, network)
        if (success) {
            val finalTxId = if (msg.length == 64 && !msg.contains(" ")) msg else txId
            val sweepTx = TransactionEntity(
                id = finalTxId,
                walletId = account.walletId,
                accountId = account.id,
                txType = TransactionType.RECEIVE,
                amountSompi = sweepAmount,
                feeSompi = feeSompi,
                senderAddress = derivedAddr,
                recipientAddress = account.address,
                timestamp = System.currentTimeMillis(),
                daaScore = _blockDagInfo.value.virtualDaaScore + 1,
                status = TransactionStatus.PENDING,
                note = "Swept change index #$addressIndex to primary"
            )
            saveOrMergeTransaction(sweepTx)
            syncAccountOnChain(account.id)
            Pair(true, "Recovered ${KaspaUtils.formatSompi(sweepAmount)} KAS! Tx: ${finalTxId.take(12)}...")
        } else {
            Pair(false, "Broadcast rejected: $msg")
        }
    }

    suspend fun recoverAllChangeAddresses(account: AccountEntity): Pair<Int, Long> = withContext(Dispatchers.IO) {
        val wallet = database.walletDao().getWalletById(account.walletId) ?: return@withContext Pair(0, 0L)
        val words = getWalletMnemonicWords(wallet)
        val passphrase = getWalletPassphrase(wallet)
        if (words.isEmpty()) return@withContext Pair(0, 0L)
        val network = _currentNetwork.value
        var recoveredCount = 0
        var totalRecoveredSompi = 0L

        for (branch in listOf(1, 0)) {
            val startIdx = if (branch == 0) 1 else 0
            for (idx in startIdx until 30) {
                val derivedAddr = if (branch == 0) {
                    KaspaCrypto.deriveKaspaAddress(words, account.accountIndex, idx, network, passphrase)
                } else {
                    KaspaCrypto.deriveKaspaChangeAddress(words, account.accountIndex, idx, network, passphrase)
                }
                val utxos = apiClient.fetchAddressUtxos(derivedAddr, network)
                if (utxos.isNotEmpty()) {
                    val res = recoverChangeAddressFunds(account, branch, idx)
                    if (res.first) {
                        recoveredCount++
                        totalRecoveredSompi += utxos.sumOf { it.amountSompi }
                    }
                }
            }
        }
        if (recoveredCount > 0) {
            syncAccountOnChain(account.id)
        }
        Pair(recoveredCount, totalRecoveredSompi)
    }

    suspend fun saveOrMergeTransaction(tx: TransactionEntity) = withContext(Dispatchers.IO) {
        val existing = database.transactionDao().getTransactionById(tx.id)
        if (existing != null) {
            val mergedTx = tx.copy(
                note = if (existing.note.isNotBlank() && !existing.note.startsWith("Mass:")) existing.note else tx.note,
                txType = if (existing.txType == TransactionType.SEND || existing.txType == TransactionType.COMPOUND) existing.txType else tx.txType,
                recipientAddress = if (existing.recipientAddress.isNotBlank() && existing.recipientAddress != existing.senderAddress) existing.recipientAddress else tx.recipientAddress,
                amountSompi = if (existing.txType == TransactionType.SEND && existing.amountSompi > 0) existing.amountSompi else tx.amountSompi,
                feeSompi = if (existing.feeSompi > 0) existing.feeSompi else tx.feeSompi,
                status = TransactionStatus.CONFIRMED
            )
            database.transactionDao().insertTransaction(mergedTx)
        } else {
            database.transactionDao().insertTransaction(tx)
        }
    }

    fun trackTransactionConfirmationRealtime(txId: String, accountId: String, spentOutpoints: List<String>) {
        repositoryScope.launch {
            var attempts = 0
            var confirmed = false
            while (attempts < 15 && !confirmed) {
                delay(1000)
                attempts++
                try {
                    val network = _currentNetwork.value
                    val txInfo = apiClient.fetchTransaction(txId, network)
                    val isAccepted = txInfo != null && (txInfo.optBoolean("is_accepted", false) || txInfo.has("block_time") || txInfo.has("transaction_id"))
                    if (isAccepted) {
                        confirmed = true
                        Log.i("KaspaWalletRepository", "Tx $txId confirmed on-chain in $attempts seconds! Releasing pending outpoints.")
                        for (op in spentOutpoints) {
                            pendingSpentOutpoints.remove(op)
                        }
                        val existing = database.transactionDao().getTransactionById(txId)
                        if (existing != null) {
                            val blockDaa = txInfo?.optLong("block_daa_score", existing.daaScore) ?: existing.daaScore
                            database.transactionDao().insertTransaction(existing.copy(status = TransactionStatus.CONFIRMED, daaScore = blockDaa))
                        }
                        syncAccountOnChain(accountId)
                        break
                    }
                } catch (e: Exception) {
                    // Continue checking
                }
            }

            // If 15 seconds elapsed and not confirmed, release outpoints to avoid locking user funds
            if (!confirmed) {
                for (op in spentOutpoints) {
                    pendingSpentOutpoints.remove(op)
                }
                syncAccountOnChain(accountId)
            }
        }
    }

    fun getAccountsForWallet(walletId: String): Flow<List<AccountEntity>> {
        return database.accountDao().getAccountsForWallet(walletId)
    }

    fun getTransactionsForWallet(walletId: String): Flow<List<TransactionEntity>> {
        return database.transactionDao().getTransactionsForWallet(walletId)
    }

    fun getTransactionsForAccount(accountId: String): Flow<List<TransactionEntity>> {
        return database.transactionDao().getTransactionsForAccount(accountId)
    }

    fun setNetwork(network: KaspaNetwork) {
        _currentNetwork.value = network
        repositoryScope.launch {
            syncNetworkMetrics()
            val currentAcc = _activeAccountId.value
            if (currentAcc != null) {
                syncAccountOnChain(currentAcc)
            }
        }
    }

    fun setCustomRpcEndpoint(url: String?) {
        apiClient.customEndpoint = url
        repositoryScope.launch {
            syncNetworkMetrics()
        }
    }

    fun setActiveWallet(walletId: String?) {
        _activeWalletId.value = walletId
    }

    fun setActiveAccount(accountId: String?) {
        _activeAccountId.value = accountId
        if (accountId != null) {
            repositoryScope.launch {
                syncAccountOnChain(accountId)
            }
        }
    }

    suspend fun createWallet(
        name: String,
        mnemonicWords: List<String>,
        hasPassphrase: Boolean = false,
        passphrase: String = "",
        password: String = ""
    ): Pair<WalletEntity, AccountEntity> = withContext(Dispatchers.IO) {
        val walletId = UUID.randomUUID().toString()
        if (password.isNotBlank()) {
            activeSessionPassword = password
        }
        val wallet = WalletEntity(
            id = walletId,
            name = name,
            encryptedMnemonic = KaspaCrypto.encryptMnemonic(mnemonicWords, password),
            wordCount = mnemonicWords.size,
            hasPassphrase = hasPassphrase,
            encryptedPassphrase = if (hasPassphrase && passphrase.isNotBlank()) KaspaCrypto.encryptPassphrase(passphrase, password) else "",
            isLocked = false
        )
        database.walletDao().insertWallet(wallet)

        // Create default Primary account (#0)
        val accountId = UUID.randomUUID().toString()
        val address = KaspaUtils.generateDeterministicAddress(
            mnemonicWords = mnemonicWords,
            accountIndex = 0,
            network = _currentNetwork.value,
            passphrase = passphrase
        )
        
        // Fetch real on-chain balance (0 Sompi for fresh or real balance if imported)
        val realOnChainBalance = apiClient.fetchAddressBalance(address, _currentNetwork.value) ?: 0L

        val primaryAccount = AccountEntity(
            id = accountId,
            walletId = walletId,
            accountIndex = 0,
            name = "Primary Account (#0)",
            address = address,
            balanceSompi = realOnChainBalance,
            accountType = "BIP44 Standard",
            derivationPath = "m/44'/111111'/0'/0/0",
            colorIndex = 0
        )
        database.accountDao().insertAccount(primaryAccount)

        // Fetch UTXOs and txs from live network
        val liveUtxos = apiClient.fetchAddressUtxos(address, _currentNetwork.value)
        _accountUtxos.update { current ->
            current + (accountId to liveUtxos)
        }

        val liveTxs = apiClient.fetchAddressTransactions(address, walletId, accountId, _currentNetwork.value)
        for (tx in liveTxs) {
            database.transactionDao().insertTransaction(tx)
        }

        _activeWalletId.value = walletId
        _activeAccountId.value = accountId

        Pair(wallet, primaryAccount)
    }

    suspend fun createAccount(
        walletId: String,
        name: String,
        accountIndex: Int
    ): AccountEntity = withContext(Dispatchers.IO) {
        val wallet = database.walletDao().getWalletById(walletId)
            ?: throw IllegalStateException("Wallet not found")

        val words = getWalletMnemonicWords(wallet)
        val address = KaspaUtils.generateDeterministicAddress(words, accountIndex, _currentNetwork.value)
        val accountId = UUID.randomUUID().toString()

        val realOnChainBalance = apiClient.fetchAddressBalance(address, _currentNetwork.value) ?: 0L

        val newAccount = AccountEntity(
            id = accountId,
            walletId = walletId,
            accountIndex = accountIndex,
            name = name,
            address = address,
            balanceSompi = realOnChainBalance,
            accountType = "BIP44 Sub-Account",
            derivationPath = "m/44'/111111'/$accountIndex'/0/0",
            colorIndex = accountIndex % 5
        )
        database.accountDao().insertAccount(newAccount)

        val liveUtxos = apiClient.fetchAddressUtxos(address, _currentNetwork.value)
        _accountUtxos.update { current ->
            current + (accountId to liveUtxos)
        }

        _activeAccountId.value = accountId
        newAccount
    }

    suspend fun getAccountChangeAddress(accountId: String, index: Int = 0): String = withContext(Dispatchers.IO) {
        val account = database.accountDao().getAccountById(accountId) ?: return@withContext ""
        val wallet = database.walletDao().getWalletById(account.walletId) ?: return@withContext account.address
        val words = getWalletMnemonicWords(wallet)
        if (words.isEmpty()) return@withContext account.address
        val passphrase = getWalletPassphrase(wallet)
        KaspaCrypto.deriveKaspaChangeAddress(
            mnemonic = words,
            accountIndex = account.accountIndex,
            addressIndex = index,
            network = _currentNetwork.value,
            passphrase = passphrase
        )
    }

    suspend fun deriveAddressForAccount(accountId: String, branch: Int, index: Int): String = withContext(Dispatchers.IO) {
        val account = database.accountDao().getAccountById(accountId) ?: return@withContext ""
        val wallet = database.walletDao().getWalletById(account.walletId) ?: return@withContext account.address
        val words = getWalletMnemonicWords(wallet)
        if (words.isEmpty()) return@withContext account.address
        val seed = getWalletSeed(wallet)
        try {
            KaspaSigner.deriveKaspaAddressFromSeed(
                seed = seed,
                accountIndex = account.accountIndex,
                branch = branch,
                addressIndex = index,
                network = _currentNetwork.value
            )
        } finally {
            seed.fill(0)
        }
    }

    suspend fun sendKas(
        senderAccount: AccountEntity,
        recipientAddress: String,
        amountSompi: Long,
        feeSompi: Long,
        note: String,
        manualUtxos: List<UtxoEntry>? = null,
        explicitPassword: String? = null,
        explicitWords: List<String>? = null
    ): TransactionEntity = withContext(Dispatchers.IO) {
        if (!explicitPassword.isNullOrBlank()) {
            activeSessionPassword = explicitPassword
        }
        sendTransactionMutex.withLock {
            val totalDebit = amountSompi + feeSompi

            val wallet = database.walletDao().getWalletById(senderAccount.walletId)
            val words = if (explicitWords != null && explicitWords.isNotEmpty()) explicitWords else getWalletMnemonicWords(wallet, explicitPassword)
            val passphrase = getWalletPassphrase(wallet, explicitPassword)
            val seed = getWalletSeed(wallet, explicitPassword, explicitWords)

            // Change output returns directly to the sender's account address
            val changeAddress = senderAccount.address

            // Always fetch live UTXOs directly from the Kaspa network to guarantee we only spend active, valid on-chain UTXOs
            val livePrimary = apiClient.fetchAddressUtxos(senderAccount.address, _currentNetwork.value)
            val availableUtxos = mutableListOf<UtxoEntry>()
            availableUtxos.addAll(livePrimary)

            // If primary address UTXOs are insufficient, check change & secondary address indices live directly from network
            if (availableUtxos.sumOf { it.amountSompi } < totalDebit && words.isNotEmpty()) {
                for (branch in listOf(1, 0)) {
                    for (idx in 0 until 30) {
                        val addr = if (branch == 0) {
                            KaspaCrypto.deriveKaspaAddress(words, senderAccount.accountIndex, idx, _currentNetwork.value, passphrase)
                        } else {
                            KaspaCrypto.deriveKaspaChangeAddress(words, senderAccount.accountIndex, idx, _currentNetwork.value, passphrase)
                        }
                        if (addr.isNotBlank() && addr != senderAccount.address) {
                            val extraUtxos = apiClient.fetchAddressUtxos(addr, _currentNetwork.value)
                            for (u in extraUtxos) {
                                if (!availableUtxos.any { it.outpointTxId == u.outpointTxId && it.outpointIndex == u.outpointIndex }) {
                                    availableUtxos.add(u)
                                }
                            }
                            if (availableUtxos.sumOf { it.amountSompi } >= totalDebit) break
                        }
                    }
                    if (availableUtxos.sumOf { it.amountSompi } >= totalDebit) break
                }
            }

            val selectedUtxos = if (manualUtxos != null && manualUtxos.isNotEmpty()) {
                // Ensure every manually selected UTXO actually exists in the live unspent set to avoid orphan errors
                val verifiedManual = manualUtxos.filter { m ->
                    availableUtxos.any { it.outpointTxId == m.outpointTxId && it.outpointIndex == m.outpointIndex }
                }
                val selectedSum = verifiedManual.sumOf { it.amountSompi }
                if (selectedSum < totalDebit) {
                    throw IllegalArgumentException("Selected UTXOs are insufficient or have already been spent on-chain. Live available: ${KaspaUtils.formatSompi(selectedSum)}, Required: ${KaspaUtils.formatSompi(totalDebit)}")
                }
                verifiedManual
            } else if (availableUtxos.isNotEmpty()) {
                // Optimal Coin Selection:
                // 1. Prefer single UTXO (smallest UTXO that covers totalDebit) to minimize transaction mass & fees
                val singleMatch = availableUtxos
                    .filter { it.amountSompi >= totalDebit }
                    .minByOrNull { it.amountSompi }

                if (singleMatch != null) {
                    listOf(singleMatch)
                } else {
                    // 2. Sort descending (largest first) to minimize input count and transaction mass
                    val sorted = availableUtxos.sortedByDescending { it.amountSompi }
                    val selected = mutableListOf<UtxoEntry>()
                    var accumulated = 0L
                    for (u in sorted) {
                        selected.add(u)
                        accumulated += u.amountSompi
                        if (accumulated >= totalDebit) break
                    }
                    if (accumulated < totalDebit) {
                        throw IllegalStateException("Insufficient confirmed UTXOs on Kaspa ${_currentNetwork.value.displayName}. Available: ${KaspaUtils.formatSompi(accumulated)}, Required: ${KaspaUtils.formatSompi(totalDebit)}")
                    }
                    selected
                }
            } else {
                throw IllegalStateException("No confirmed UTXOs found for address ${senderAccount.address} on Kaspa ${_currentNetwork.value.displayName}. Please fund this address before sending.")
            }

            // Cryptographically sign transaction using BIP340 Schnorr and Kaspa Blake2b Sighash
            val (signedTxJson, txId) = try {
                KaspaSigner.createAndSignTransaction(
                    seed = seed,
                    accountIndex = senderAccount.accountIndex,
                    inputs = selectedUtxos,
                    recipientAddress = recipientAddress,
                    amountSompi = amountSompi,
                    feeSompi = feeSompi,
                    changeAddress = changeAddress,
                    network = _currentNetwork.value
                )
            } finally {
                seed.fill(0)
            }

            // Broadcast authentic cryptographically signed transaction to Kaspa network
            val (broadcastSuccess, responseMsg) = apiClient.broadcastTransaction(signedTxJson, _currentNetwork.value)
            Log.i("KaspaWalletRepository", "Broadcast result: $broadcastSuccess ($responseMsg)")

            if (!broadcastSuccess) {
                // If broadcast was rejected by node (e.g. orphan / spent), immediately refresh on-chain state to purge stale UTXOs
                repositoryScope.launch {
                    syncAccountOnChain(senderAccount.id)
                }
                throw IllegalStateException("Transaction broadcast rejected by Kaspa network: $responseMsg")
            }

            val finalTxId = if (responseMsg.length == 64 && !responseMsg.contains(" ")) responseMsg else txId
            val currentDaa = _blockDagInfo.value.virtualDaaScore + 1

            // Record spent outpoints to prevent lagging REST API indexer from resurrecting them
            val now = System.currentTimeMillis()
            for (u in selectedUtxos) {
                pendingSpentOutpoints["${u.outpointTxId}:${u.outpointIndex}"] = now
            }

            val totalInputSompi = selectedUtxos.sumOf { it.amountSompi }
            val changeSompi = totalInputSompi - totalDebit
            val changeScript = KaspaSigner.addressToScriptPublicKey(changeAddress)

            val optimisticChangeList = mutableListOf<UtxoEntry>()
            if (changeSompi >= 500L) {
                val changeUtxo = UtxoEntry(
                    outpointTxId = finalTxId,
                    outpointIndex = 1,
                    amountSompi = changeSompi,
                    scriptPublicKey = changeScript,
                    blockDaaScore = currentDaa,
                    isCoinbase = false
                )
                optimisticChangeList.add(changeUtxo)
                val existing = pendingChangeUtxos.getOrPut(senderAccount.id) { mutableListOf() }
                synchronized(existing) {
                    existing.removeAll { it.outpointTxId == finalTxId }
                    existing.add(changeUtxo)
                }
            }

            val newSenderBalance = maxOf(0L, senderAccount.balanceSompi - totalDebit)
            database.accountDao().updateBalance(senderAccount.id, newSenderBalance)

            // Remove spent UTXOs from local cache and include optimistic change immediately
            val remaining = availableUtxos.filterNot { selectedUtxos.contains(it) }.toMutableList()
            remaining.addAll(optimisticChangeList)
            val distinctRemaining = remaining.distinctBy { "${it.outpointTxId}:${it.outpointIndex}" }
            _accountUtxos.update { current ->
                current + (senderAccount.id to distinctRemaining)
            }

            repositoryScope.launch {
                syncAccountOnChain(senderAccount.id)
            }

            val tx = TransactionEntity(
                id = finalTxId,
                walletId = senderAccount.walletId,
                accountId = senderAccount.id,
                txType = TransactionType.SEND,
                amountSompi = amountSompi,
                feeSompi = feeSompi,
                senderAddress = senderAccount.address,
                recipientAddress = recipientAddress,
                timestamp = System.currentTimeMillis(),
                daaScore = currentDaa,
                status = TransactionStatus.PENDING,
                note = note
            )
            database.transactionDao().insertTransaction(tx)

            // Actively track on-chain blockDAG confirmation in real-time
            trackTransactionConfirmationRealtime(
                txId = finalTxId,
                accountId = senderAccount.id,
                spentOutpoints = selectedUtxos.map { "${it.outpointTxId}:${it.outpointIndex}" }
            )

            tx
        }
    }

    suspend fun transferBetweenAccounts(
        sourceAccount: AccountEntity,
        targetAccount: AccountEntity,
        amountSompi: Long,
        feeSompi: Long,
        note: String,
        explicitPassword: String? = null
    ): TransactionEntity = withContext(Dispatchers.IO) {
        val transferNote = if (note.isBlank()) "Transfer to ${targetAccount.name}" else note
        val tx = sendKas(
            senderAccount = sourceAccount,
            recipientAddress = targetAccount.address,
            amountSompi = amountSompi,
            feeSompi = feeSompi,
            note = transferNote,
            explicitPassword = explicitPassword
        )
        val transferTx = tx.copy(txType = TransactionType.TRANSFER)
        database.transactionDao().insertTransaction(transferTx)
        transferTx
    }

    suspend fun sendMassKas(
        senderAccount: AccountEntity,
        recipients: List<Pair<String, Long>>,
        feeSompi: Long,
        explicitPassword: String? = null,
        explicitWords: List<String>? = null
    ): TransactionEntity = withContext(Dispatchers.IO) {
        if (!explicitPassword.isNullOrBlank()) {
            activeSessionPassword = explicitPassword
        }
        sendTransactionMutex.withLock {
            val totalPayment = recipients.sumOf { it.second }
            val totalDebit = totalPayment + feeSompi

            val wallet = database.walletDao().getWalletById(senderAccount.walletId)
            val words = if (explicitWords != null && explicitWords.isNotEmpty()) explicitWords else getWalletMnemonicWords(wallet, explicitPassword)
            val passphrase = getWalletPassphrase(wallet, explicitPassword)
            val seed = getWalletSeed(wallet, explicitPassword, explicitWords)

            val changeAddress = senderAccount.address
            val livePrimary = apiClient.fetchAddressUtxos(senderAccount.address, _currentNetwork.value)
            val availableUtxos = mutableListOf<UtxoEntry>()
            availableUtxos.addAll(livePrimary)

            if (availableUtxos.sumOf { it.amountSompi } < totalDebit && words.isNotEmpty()) {
                for (branch in listOf(1, 0)) {
                    for (idx in 0 until 30) {
                        val addr = if (branch == 0) {
                            KaspaCrypto.deriveKaspaAddress(words, senderAccount.accountIndex, idx, _currentNetwork.value, passphrase)
                        } else {
                            KaspaCrypto.deriveKaspaChangeAddress(words, senderAccount.accountIndex, idx, _currentNetwork.value, passphrase)
                        }
                        if (addr.isNotBlank() && addr != senderAccount.address) {
                            val extraUtxos = apiClient.fetchAddressUtxos(addr, _currentNetwork.value)
                            for (u in extraUtxos) {
                                if (!availableUtxos.any { it.outpointTxId == u.outpointTxId && it.outpointIndex == u.outpointIndex }) {
                                    availableUtxos.add(u)
                                }
                            }
                            if (availableUtxos.sumOf { it.amountSompi } >= totalDebit) break
                        }
                    }
                    if (availableUtxos.sumOf { it.amountSompi } >= totalDebit) break
                }
            }

            val singleMatch = availableUtxos
                .filter { it.amountSompi >= totalDebit }
                .minByOrNull { it.amountSompi }

            val selected = if (singleMatch != null) {
                listOf(singleMatch)
            } else {
                val sorted = availableUtxos.sortedByDescending { it.amountSompi }
                val accList = mutableListOf<UtxoEntry>()
                var accumulated = 0L
                for (u in sorted) {
                    accList.add(u)
                    accumulated += u.amountSompi
                    if (accumulated >= totalDebit) break
                }
                if (accumulated < totalDebit) {
                    throw IllegalStateException("Insufficient confirmed UTXOs on Kaspa ${_currentNetwork.value.displayName}. Available: ${KaspaUtils.formatSompi(accumulated)}, Required: ${KaspaUtils.formatSompi(totalDebit)}")
                }
                accList
            }

            val (signedTxJson, txId) = try {
                KaspaSigner.createAndSignMultiOutputTransaction(
                    seed = seed,
                    accountIndex = senderAccount.accountIndex,
                    inputs = selected,
                    recipients = recipients,
                    feeSompi = feeSompi,
                    changeAddress = changeAddress,
                    network = _currentNetwork.value
                )
            } finally {
                seed.fill(0)
            }

            val (broadcastSuccess, responseMsg) = apiClient.broadcastTransaction(signedTxJson, _currentNetwork.value)
            if (!broadcastSuccess) {
                throw IllegalStateException("Mass transaction broadcast rejected by Kaspa network: $responseMsg")
            }

            val finalTxId = if (responseMsg.length == 64 && !responseMsg.contains(" ")) responseMsg else txId

            val now = System.currentTimeMillis()
            for (u in selected) {
                pendingSpentOutpoints["${u.outpointTxId}:${u.outpointIndex}"] = now
            }

            val totalInputSompi = selected.sumOf { it.amountSompi }
            val changeSompi = totalInputSompi - totalDebit
            val changeScript = KaspaSigner.addressToScriptPublicKey(changeAddress)

            val optimisticChangeList = mutableListOf<UtxoEntry>()
            if (changeSompi >= 500L) {
                val changeUtxo = UtxoEntry(
                    outpointTxId = finalTxId,
                    outpointIndex = recipients.size,
                    amountSompi = changeSompi,
                    scriptPublicKey = changeScript,
                    blockDaaScore = _blockDagInfo.value.virtualDaaScore + 1,
                    isCoinbase = false
                )
                optimisticChangeList.add(changeUtxo)
                val existing = pendingChangeUtxos.getOrPut(senderAccount.id) { mutableListOf() }
                synchronized(existing) {
                    existing.removeAll { it.outpointTxId == finalTxId }
                    existing.add(changeUtxo)
                }
            }

            val newSenderBalance = maxOf(0L, senderAccount.balanceSompi - totalDebit)
            database.accountDao().updateBalance(senderAccount.id, newSenderBalance)

            val tx = TransactionEntity(
                id = finalTxId,
                walletId = senderAccount.walletId,
                accountId = senderAccount.id,
                txType = TransactionType.SEND,
                amountSompi = totalPayment,
                feeSompi = feeSompi,
                senderAddress = senderAccount.address,
                recipientAddress = "${recipients.size} Batch Recipients",
                status = TransactionStatus.CONFIRMED,
                timestamp = System.currentTimeMillis(),
                daaScore = _blockDagInfo.value.virtualDaaScore + 1,
                note = "Batch Send to ${recipients.size} outputs"
            )
            saveOrMergeTransaction(tx)

            val updatedUtxos = availableUtxos.filterNot { selected.contains(it) }.toMutableList()
            updatedUtxos.addAll(optimisticChangeList)
            val distinctUpdated = updatedUtxos.distinctBy { "${it.outpointTxId}:${it.outpointIndex}" }
            _accountUtxos.update { current ->
                current + (senderAccount.id to distinctUpdated)
            }
            trackTransactionConfirmationRealtime(
                txId = finalTxId,
                accountId = senderAccount.id,
                spentOutpoints = selected.map { "${it.outpointTxId}:${it.outpointIndex}" }
            )
            tx
        }
    }

    suspend fun compoundAccountUtxos(account: AccountEntity): TransactionEntity = withContext(Dispatchers.IO) {
        val liveUtxos = apiClient.fetchAddressUtxos(account.address, _currentNetwork.value)
        if (liveUtxos.isEmpty()) {
            throw IllegalStateException("No unspent outputs (UTXOs) available to compound for address ${KaspaUtils.truncateAddress(account.address)}")
        }
        if (liveUtxos.size <= 1) {
            throw IllegalStateException("UTXOs are already consolidated for address ${KaspaUtils.truncateAddress(account.address)}")
        }

        val wallet = database.walletDao().getWalletById(account.walletId)
            ?: throw IllegalStateException("Wallet not found")
        val words = getWalletMnemonicWords(wallet)
        if (words.size < 12) {
            throw IllegalStateException("Invalid wallet seed words")
        }
        val seed = getWalletSeed(wallet)
        // Cap compounding inputs to 80 to guarantee transaction mass stays below standard node mempool limits
        val utxosToCompound = liveUtxos.take(80)

        val totalInput = utxosToCompound.sumOf { it.amountSompi }
        val mass = KaspaSigner.calculateTransactionMass(utxosToCompound.size, 1)
        val feeSompi = KaspaSigner.calculateMinimumFeeSompi(mass)

        if (totalInput <= feeSompi) {
            throw IllegalStateException("Balance too low ($totalInput Sompi) to cover consensus network fee ($feeSompi Sompi)")
        }

        val totalAmount = totalInput - feeSompi

        val (signedTxJson, txId) = try {
            KaspaSigner.createAndSignTransaction(
                seed = seed,
                accountIndex = account.accountIndex,
                inputs = utxosToCompound,
                recipientAddress = account.address,
                amountSompi = totalAmount,
                feeSompi = feeSompi,
                changeAddress = account.address,
                network = _currentNetwork.value
            )
        } finally {
            seed.fill(0)
        }

        val (broadcastSuccess, responseMsg) = apiClient.broadcastTransaction(signedTxJson, _currentNetwork.value)
        Log.i("KaspaWalletRepository", "Compound broadcast result: $broadcastSuccess ($responseMsg)")
        if (!broadcastSuccess) {
            throw IllegalStateException("Transaction broadcast rejected by Kaspa network: $responseMsg")
        }

        val finalTxId = if (responseMsg.length == 64 && !responseMsg.contains(" ")) responseMsg else txId
        val currentDaa = _blockDagInfo.value.virtualDaaScore + 1

        val now = System.currentTimeMillis()
        for (u in utxosToCompound) {
            pendingSpentOutpoints["${u.outpointTxId}:${u.outpointIndex}"] = now
        }

        val compoundUtxo = UtxoEntry(
            outpointTxId = finalTxId,
            outpointIndex = 0,
            amountSompi = totalAmount,
            scriptPublicKey = KaspaSigner.addressToScriptPublicKey(account.address),
            blockDaaScore = currentDaa,
            isCoinbase = false
        )
        val existing = pendingChangeUtxos.getOrPut(account.id) { mutableListOf() }
        synchronized(existing) {
            existing.removeAll { it.outpointTxId == finalTxId }
            existing.add(compoundUtxo)
        }

        database.accountDao().updateBalance(account.id, totalAmount)

        val tx = TransactionEntity(
            id = finalTxId,
            walletId = account.walletId,
            accountId = account.id,
            txType = TransactionType.COMPOUND,
            amountSompi = totalAmount,
            feeSompi = feeSompi,
            senderAddress = account.address,
            recipientAddress = account.address,
            timestamp = System.currentTimeMillis(),
            daaScore = currentDaa,
            status = TransactionStatus.CONFIRMED,
            note = "Consolidated ${utxosToCompound.size} UTXOs • Mass: $mass grams"
        )
        database.transactionDao().insertTransaction(tx)

        _accountUtxos.update { current ->
            current + (account.id to listOf(compoundUtxo))
        }

        trackTransactionConfirmationRealtime(
            txId = finalTxId,
            accountId = account.id,
            spentOutpoints = utxosToCompound.map { "${it.outpointTxId}:${it.outpointIndex}" }
        )

        tx
    }

    suspend fun addContact(name: String, address: String, note: String): ContactEntity = withContext(Dispatchers.IO) {
        val contact = ContactEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            address = address,
            note = note,
            createdAt = System.currentTimeMillis()
        )
        database.contactDao().insertContact(contact)
        contact
    }

    suspend fun deleteContact(contact: ContactEntity) = withContext(Dispatchers.IO) {
        database.contactDao().deleteContact(contact)
    }

    suspend fun deleteWallet(walletId: String) = withContext(Dispatchers.IO) {
        database.transactionDao().deleteTransactionsForWallet(walletId)
        database.accountDao().deleteAccountsForWallet(walletId)
        database.walletDao().deleteWallet(walletId)

        if (_activeWalletId.value == walletId) {
            _activeWalletId.value = null
            _activeAccountId.value = null
        }
    }

    /**
     * NIST FIPS 204 (ML-DSA-65) keypair derivation from current active wallet.
     */
    suspend fun deriveActiveWalletMlDsaKeyPair(accountIndex: Int = 0, explicitPassword: String? = null): KaspaMlDsa.MlDsaKeyPair? = withContext(Dispatchers.IO) {
        val walletId = _activeWalletId.value ?: return@withContext null
        val wallet = database.walletDao().getWalletById(walletId) ?: return@withContext null
        val words = getWalletMnemonicWords(wallet, explicitPassword)
        if (words.isEmpty()) return@withContext null
        KaspaMlDsa.deriveKeyPairFromMnemonic(words, accountIndex)
    }

    fun generateRandomMlDsaKeyPair(): KaspaMlDsa.MlDsaKeyPair {
        return KaspaMlDsa.generateRandomKeyPair()
    }
}
