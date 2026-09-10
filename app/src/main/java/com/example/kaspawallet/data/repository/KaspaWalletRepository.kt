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
import java.util.UUID

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

    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var activeSessionPassword: String = ""

    fun setActiveSessionPassword(pwd: String) {
        activeSessionPassword = pwd
    }

    fun getWalletMnemonicWords(wallet: WalletEntity?, explicitPassword: String? = null): List<String> {
        val stored = wallet?.encryptedMnemonic ?: return emptyList()
        val pwd = explicitPassword ?: activeSessionPassword
        return KaspaCrypto.decryptMnemonic(stored, pwd)
    }

    init {
        // Start background live sync for BlockDAG metrics and Market Price
        startPeriodicSync()
    }

    private fun startPeriodicSync() {
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
                delay(12000) // Poll real network every 12 seconds
            }
        }
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
        val allDiscoveredUtxos = mutableListOf<UtxoEntry>()

        // 1. Fetch live UTXOs & real balance from primary address
        val primaryUtxos = apiClient.fetchAddressUtxos(account.address, network)
        allDiscoveredUtxos.addAll(primaryUtxos)
        val primaryBal = apiClient.fetchAddressBalance(account.address, network)

        // Derive known account addresses to correctly categorize SEND vs RECEIVE transactions
        val wallet = database.walletDao().getWalletById(account.walletId)
        val words = getWalletMnemonicWords(wallet)
        val knownAddresses = mutableSetOf(account.address)
        if (words.size in listOf(12, 24)) {
            for (branch in 0..1) {
                for (idx in 0 until 30) {
                    val addr = if (branch == 0) {
                        KaspaCrypto.deriveKaspaAddress(words, account.accountIndex, idx, network)
                    } else {
                        KaspaCrypto.deriveKaspaChangeAddress(words, account.accountIndex, idx, network)
                    }
                    if (addr.isNotBlank()) knownAddresses.add(addr)
                }
            }
        }

        // 2. Fetch real Transactions for primary address
        val liveTxs = apiClient.fetchAddressTransactions(account.address, account.walletId, accountId, network, knownAddresses)
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
            if (words.size in listOf(12, 24)) {
                val seed = KaspaCrypto.mnemonicToSeed(words)
                val gapLimit = 30
                val branchesToScan = listOf(
                    0 to (1 until gapLimit).toList(), // m/44'/111111'/0'/0/1..29
                    1 to (0 until gapLimit).toList()  // m/44'/111111'/0'/1/0..29
                )

                for ((branch, indices) in branchesToScan) {
                    for (addrIdx in indices) {
                        val derivedAddr = if (branch == 0) {
                            KaspaCrypto.deriveKaspaAddress(words, account.accountIndex, addrIdx, network)
                        } else {
                            KaspaCrypto.deriveKaspaChangeAddress(words, account.accountIndex, addrIdx, network)
                        }

                        if (derivedAddr.isNotBlank() && derivedAddr != account.address) {
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
            }
        } catch (e: Exception) {
            Log.d("KaspaWalletRepository", "Multi-index auto-recovery check: ${e.message}")
        }

        // 4. Update confirmed balance and UTXO pool
        val effectiveCalculatedBalance = maxOf(primaryBal, allDiscoveredUtxos.sumOf { it.amountSompi })
        val finalBalance = maxOf(effectiveCalculatedBalance, primaryBal)
        database.accountDao().updateBalance(accountId, finalBalance)
        _accountUtxos.update { current ->
            current + (accountId to allDiscoveredUtxos.distinctBy { "${it.outpointTxId}:${it.outpointIndex}" })
        }
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
        val realOnChainBalance = apiClient.fetchAddressBalance(address, _currentNetwork.value)

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

        val realOnChainBalance = apiClient.fetchAddressBalance(address, _currentNetwork.value)

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
        KaspaCrypto.deriveKaspaChangeAddress(
            mnemonic = words,
            accountIndex = account.accountIndex,
            addressIndex = index,
            network = _currentNetwork.value
        )
    }

    suspend fun deriveAddressForAccount(accountId: String, branch: Int, index: Int): String = withContext(Dispatchers.IO) {
        val account = database.accountDao().getAccountById(accountId) ?: return@withContext ""
        val wallet = database.walletDao().getWalletById(account.walletId) ?: return@withContext account.address
        val words = getWalletMnemonicWords(wallet)
        if (words.isEmpty()) return@withContext account.address
        val seed = KaspaCrypto.mnemonicToSeed(words)
        KaspaSigner.deriveKaspaAddressFromSeed(
            seed = seed,
            accountIndex = account.accountIndex,
            branch = branch,
            addressIndex = index,
            network = _currentNetwork.value
        )
    }

    suspend fun sendKas(
        senderAccount: AccountEntity,
        recipientAddress: String,
        amountSompi: Long,
        feeSompi: Long,
        note: String,
        manualUtxos: List<UtxoEntry>? = null
    ): TransactionEntity = withContext(Dispatchers.IO) {
        sendTransactionMutex.withLock {
            val totalDebit = amountSompi + feeSompi

            val wallet = database.walletDao().getWalletById(senderAccount.walletId)
            val words = getWalletMnemonicWords(wallet)
            val seed = if (words.isNotEmpty()) KaspaCrypto.mnemonicToSeed(words) else ByteArray(64)

            // Change output returns directly to the sender's account address
            val changeAddress = senderAccount.address

            // Always fetch live UTXOs directly from the Kaspa network to guarantee we only spend active, valid on-chain UTXOs
            val livePrimary = apiClient.fetchAddressUtxos(senderAccount.address, _currentNetwork.value)
            val availableUtxos = mutableListOf<UtxoEntry>()
            availableUtxos.addAll(livePrimary)

            // If primary address UTXOs are insufficient, check change & secondary address indices live
            if (availableUtxos.sumOf { it.amountSompi } < totalDebit && words.isNotEmpty()) {
                for (branch in listOf(1, 0)) {
                    for (idx in 0 until 15) {
                        val addr = if (branch == 0) {
                            KaspaCrypto.deriveKaspaAddress(words, senderAccount.accountIndex, idx, _currentNetwork.value)
                        } else {
                            KaspaCrypto.deriveKaspaChangeAddress(words, senderAccount.accountIndex, idx, _currentNetwork.value)
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
                val selectedSum = manualUtxos.sumOf { it.amountSompi }
                if (selectedSum < totalDebit) {
                    throw IllegalArgumentException("Selected UTXOs sum is insufficient. Selected: ${KaspaUtils.formatSompi(selectedSum)}, Required: ${KaspaUtils.formatSompi(totalDebit)}")
                }
                manualUtxos
            } else if (availableUtxos.isNotEmpty()) {
                val selected = mutableListOf<UtxoEntry>()
                var accumulated = 0L
                for (u in availableUtxos) {
                    selected.add(u)
                    accumulated += u.amountSompi
                    if (accumulated >= totalDebit) break
                }
                if (accumulated < totalDebit) {
                    throw IllegalStateException("Insufficient confirmed UTXOs on Kaspa ${_currentNetwork.value.displayName}. Available: ${KaspaUtils.formatSompi(accumulated)}, Required: ${KaspaUtils.formatSompi(totalDebit)}")
                }
                selected
            } else {
                throw IllegalStateException("No confirmed UTXOs found for address ${senderAccount.address} on Kaspa ${_currentNetwork.value.displayName}. Please fund this address before sending.")
            }

            // Cryptographically sign transaction using BIP340 Schnorr and Kaspa Blake2b Sighash
            val (signedTxJson, txId) = KaspaSigner.createAndSignTransaction(
                seed = seed,
                accountIndex = senderAccount.accountIndex,
                inputs = selectedUtxos,
                recipientAddress = recipientAddress,
                amountSompi = amountSompi,
                feeSompi = feeSompi,
                changeAddress = changeAddress,
                network = _currentNetwork.value
            )

            // Broadcast authentic cryptographically signed transaction to Kaspa network
            val (broadcastSuccess, responseMsg) = apiClient.broadcastTransaction(signedTxJson, _currentNetwork.value)
            Log.i("KaspaWalletRepository", "Broadcast result: $broadcastSuccess ($responseMsg)")

            if (!broadcastSuccess) {
                throw IllegalStateException("Transaction broadcast rejected by Kaspa network: $responseMsg")
            }

            val finalTxId = if (responseMsg.length == 64 && !responseMsg.contains(" ")) responseMsg else txId

            val newSenderBalance = maxOf(0L, senderAccount.balanceSompi - totalDebit)
            database.accountDao().updateBalance(senderAccount.id, newSenderBalance)

            val currentDaa = _blockDagInfo.value.virtualDaaScore + 1

            // Remove spent UTXOs from local cache and trigger background sync once broadcasted
            val updatedUtxos = availableUtxos.filterNot { selectedUtxos.contains(it) }
            _accountUtxos.update { current ->
                current + (senderAccount.id to updatedUtxos)
            }

            repositoryScope.launch {
                kotlinx.coroutines.delay(1500)
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

            // Re-sync on-chain balance after broadcast
            repositoryScope.launch {
                delay(3000)
                syncAccountOnChain(senderAccount.id)
            }

            tx
        }
    }

    suspend fun transferBetweenAccounts(
        sourceAccount: AccountEntity,
        targetAccount: AccountEntity,
        amountSompi: Long,
        feeSompi: Long,
        note: String
    ): TransactionEntity = withContext(Dispatchers.IO) {
        val transferNote = if (note.isBlank()) "Transfer to ${targetAccount.name}" else note
        val tx = sendKas(
            senderAccount = sourceAccount,
            recipientAddress = targetAccount.address,
            amountSompi = amountSompi,
            feeSompi = feeSompi,
            note = transferNote
        )
        val transferTx = tx.copy(txType = TransactionType.TRANSFER)
        database.transactionDao().insertTransaction(transferTx)
        transferTx
    }

    suspend fun sendMassKas(
        senderAccount: AccountEntity,
        recipients: List<Pair<String, Long>>,
        feeSompi: Long
    ): TransactionEntity = withContext(Dispatchers.IO) {
        sendTransactionMutex.withLock {
            val totalPayment = recipients.sumOf { it.second }
            val totalDebit = totalPayment + feeSompi

            val wallet = database.walletDao().getWalletById(senderAccount.walletId)
            val words = getWalletMnemonicWords(wallet)
            val seed = if (words.isNotEmpty()) KaspaCrypto.mnemonicToSeed(words) else ByteArray(64)

            val changeAddress = senderAccount.address
            val livePrimary = apiClient.fetchAddressUtxos(senderAccount.address, _currentNetwork.value)
            val availableUtxos = mutableListOf<UtxoEntry>()
            availableUtxos.addAll(livePrimary)

            if (availableUtxos.sumOf { it.amountSompi } < totalDebit && words.isNotEmpty()) {
                for (branch in listOf(1, 0)) {
                    for (idx in 0 until 15) {
                        val addr = if (branch == 0) {
                            KaspaCrypto.deriveKaspaAddress(words, senderAccount.accountIndex, idx, _currentNetwork.value)
                        } else {
                            KaspaCrypto.deriveKaspaChangeAddress(words, senderAccount.accountIndex, idx, _currentNetwork.value)
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

            val selected = mutableListOf<UtxoEntry>()
            var accumulated = 0L
            for (u in availableUtxos) {
                selected.add(u)
                accumulated += u.amountSompi
                if (accumulated >= totalDebit) break
            }
            if (accumulated < totalDebit) {
                throw IllegalStateException("Insufficient confirmed UTXOs on Kaspa ${_currentNetwork.value.displayName}. Available: ${KaspaUtils.formatSompi(accumulated)}, Required: ${KaspaUtils.formatSompi(totalDebit)}")
            }

            val (signedTxJson, txId) = KaspaSigner.createAndSignMultiOutputTransaction(
                seed = seed,
                accountIndex = senderAccount.accountIndex,
                inputs = selected,
                recipients = recipients,
                feeSompi = feeSompi,
                changeAddress = changeAddress,
                network = _currentNetwork.value
            )

            val (broadcastSuccess, responseMsg) = apiClient.broadcastTransaction(signedTxJson, _currentNetwork.value)
            if (!broadcastSuccess) {
                throw IllegalStateException("Mass transaction broadcast rejected by Kaspa network: $responseMsg")
            }

            val finalTxId = if (responseMsg.length == 64 && !responseMsg.contains(" ")) responseMsg else txId
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

            val updatedUtxos = availableUtxos.filterNot { selected.contains(it) }
            _accountUtxos.update { current ->
                current + (senderAccount.id to updatedUtxos)
            }
            repositoryScope.launch {
                delay(1500)
                syncAccountOnChain(senderAccount.id)
            }
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
        val seed = KaspaCrypto.mnemonicToSeed(words)
        // Cap compounding inputs to 80 to guarantee transaction mass stays below standard node mempool limits
        val utxosToCompound = liveUtxos.take(80)

        val totalInput = utxosToCompound.sumOf { it.amountSompi }
        val mass = KaspaSigner.calculateTransactionMass(utxosToCompound.size, 1)
        val feeSompi = KaspaSigner.calculateMinimumFeeSompi(mass)

        if (totalInput <= feeSompi) {
            throw IllegalStateException("Balance too low ($totalInput Sompi) to cover consensus network fee ($feeSompi Sompi)")
        }

        val totalAmount = totalInput - feeSompi

        val (signedTxJson, txId) = KaspaSigner.createAndSignTransaction(
            seed = seed,
            accountIndex = account.accountIndex,
            inputs = utxosToCompound,
            recipientAddress = account.address,
            amountSompi = totalAmount,
            feeSompi = feeSompi,
            changeAddress = account.address,
            network = _currentNetwork.value
        )

        val (broadcastSuccess, responseMsg) = apiClient.broadcastTransaction(signedTxJson, _currentNetwork.value)
        Log.i("KaspaWalletRepository", "Compound broadcast result: $broadcastSuccess ($responseMsg)")
        if (!broadcastSuccess) {
            throw IllegalStateException("Transaction broadcast rejected by Kaspa network: $responseMsg")
        }

        val finalTxId = if (responseMsg.length == 64 && !responseMsg.contains(" ")) responseMsg else txId
        val currentDaa = _blockDagInfo.value.virtualDaaScore + 1

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

        repositoryScope.launch {
            kotlinx.coroutines.delay(1500)
            syncAccountOnChain(account.id)
        }

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
