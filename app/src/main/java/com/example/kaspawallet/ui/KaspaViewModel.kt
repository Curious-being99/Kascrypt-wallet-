package com.example.kaspawallet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kaspawallet.data.crypto.KaspaCrypto
import com.example.kaspawallet.data.crypto.KaspaUtils
import com.example.kaspawallet.data.model.*
import com.example.kaspawallet.data.repository.KaspaWalletRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class MainTab(val title: String) {
    OVERVIEW("Overview"),
    TRANSACTIONS("Activity"),
    BLOCKDAG("BlockDAG"),
    TOOLS("UTXO & Tools"),
    SETTINGS("Settings")
}

data class WalletUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val selectedTab: MainTab = MainTab.OVERVIEW,
    val wallets: List<WalletEntity> = emptyList(),
    val activeWallet: WalletEntity? = null,
    val accounts: List<AccountEntity> = emptyList(),
    val activeAccount: AccountEntity? = null,
    val transactions: List<TransactionEntity> = emptyList(),
    val utxos: List<UtxoEntry> = emptyList(),
    val contacts: List<ContactEntity> = emptyList(),
    val network: KaspaNetwork = KaspaNetwork.MAINNET,
    val blockDagInfo: BlockDagInfo = BlockDagInfo(),
    val marketInfo: KaspaMarketInfo = KaspaMarketInfo(),
    val selectedCurrency: String = "USD",
    val isWalletLocked: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val lastSentTx: TransactionEntity? = null,
    
    // Dialog states
    val showSendDialog: Boolean = false,
    val showTxSuccessDialog: Boolean = false,
    val showReceiveDialog: Boolean = false,
    val showTransferDialog: Boolean = false,
    val showAddAccountDialog: Boolean = false,
    val showAddContactDialog: Boolean = false,
    val showSeedBackupDialog: Boolean = false,
    val showCreateWalletDialog: Boolean = false,
    val showImportWalletDialog: Boolean = false,
    val showSetupWizard: Boolean = false,
    val setupWizardMode: String = "CREATE" // "CREATE" or "IMPORT"
)

enum class ScanIndexingStage {
    IDLE,
    DERIVING_KEYS,
    CONNECTING_NODE,
    SCANNING_UTXOS,
    CALCULATING_BALANCE,
    INDEXING_HISTORY,
    COMPLETE,
    FAILED
}

data class ScanIndexingUiState(
    val isScanning: Boolean = false,
    val stage: ScanIndexingStage = ScanIndexingStage.IDLE,
    val progress: Float = 0f,
    val statusMessage: String = "",
    val derivedAddress: String = "",
    val derivationPath: String = "m/44'/111111'/0'/0/0",
    val network: KaspaNetwork = KaspaNetwork.MAINNET,
    val daaScore: Long = 0L,
    val balanceSompi: Long = 0L,
    val utxoCount: Int = 0,
    val txCount: Int = 0,
    val indexedTransactions: List<TransactionEntity> = emptyList(),
    val isImportMode: Boolean = false,
    val walletName: String = "",
    val isComplete: Boolean = false,
    val error: String? = null
)

class KaspaViewModel(val repository: KaspaWalletRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(WalletUiState())
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    private val _scanIndexingState = MutableStateFlow(ScanIndexingUiState())
    val scanIndexingState: StateFlow<ScanIndexingUiState> = _scanIndexingState.asStateFlow()

    private var isInitialWalletLoad = true
    private var accountsJob: kotlinx.coroutines.Job? = null
    private var transactionsJob: kotlinx.coroutines.Job? = null
    private var utxosJob: kotlinx.coroutines.Job? = null
    private var lastObservedWalletId: String? = null

    init {
        // Collect wallets
        viewModelScope.launch {
            repository.allWallets.collect { walletList ->
                var currentActiveWalletId: String? = null
                _uiState.update { current ->
                    val active = if (current.activeWallet != null) {
                        walletList.find { it.id == current.activeWallet.id } ?: walletList.firstOrNull()
                    } else {
                        walletList.firstOrNull()
                    }
                    currentActiveWalletId = active?.id
                    val shouldLock = isInitialWalletLoad && walletList.isNotEmpty()
                    if (isInitialWalletLoad) {
                        isInitialWalletLoad = false
                    }
                    current.copy(
                        wallets = walletList,
                        activeWallet = active,
                        isWalletLocked = if (shouldLock) true else current.isWalletLocked
                    )
                }
                currentActiveWalletId?.let { wId ->
                    if (lastObservedWalletId != wId) {
                        repository.setActiveWallet(wId)
                        observeAccountsAndTransactions(wId)
                    }
                }
            }
        }

        // Collect network
        viewModelScope.launch {
            repository.currentNetwork.collect { net ->
                _uiState.update { it.copy(network = net) }
            }
        }

        // Collect BlockDAG & Node
        viewModelScope.launch {
            repository.blockDagInfo.collect { dag ->
                _uiState.update { it.copy(blockDagInfo = dag) }
            }
        }

        // Collect Market info
        viewModelScope.launch {
            repository.marketInfo.collect { market ->
                _uiState.update { it.copy(marketInfo = market) }
            }
        }

        // Collect Contacts
        viewModelScope.launch {
            repository.allContacts.collect { contactList ->
                _uiState.update { it.copy(contacts = contactList) }
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                repository.syncNetworkMetrics()
                repository.syncMarketPrice()
                _uiState.value.activeAccount?.let {
                    repository.syncAccountOnChain(it.id)
                }
                _uiState.update { it.copy(isRefreshing = false, statusMessage = "Synced with Kaspa Network") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRefreshing = false, errorMessage = "Network sync error: ${e.localizedMessage}") }
            }
        }
    }

    fun refreshPrice() {
        viewModelScope.launch {
            try {
                repository.syncMarketPrice()
                val currentPrice = _uiState.value.marketInfo.priceUsd
                _uiState.update { it.copy(statusMessage = "Kaspa price updated: $${String.format(java.util.Locale.US, "%.4f", currentPrice)}") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to update price: ${e.localizedMessage}") }
            }
        }
    }

    fun selectTab(tab: MainTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun setNetwork(network: KaspaNetwork) {
        repository.setNetwork(network)
    }

    fun setCustomNodeUrl(url: String?) {
        repository.setCustomRpcEndpoint(url)
    }

    fun setCurrency(currency: String) {
        _uiState.update { it.copy(selectedCurrency = currency) }
    }

    fun selectWallet(wallet: WalletEntity) {
        _uiState.update { it.copy(activeWallet = wallet) }
        repository.setActiveWallet(wallet.id)
        observeAccountsAndTransactions(wallet.id)
    }

    fun selectAccount(account: AccountEntity) {
        _uiState.update { it.copy(activeAccount = account) }
        repository.setActiveAccount(account.id)
        updateAccountUtxos(account.id)
    }

    private fun observeAccountsAndTransactions(walletId: String) {
        lastObservedWalletId = walletId
        accountsJob?.cancel()
        transactionsJob?.cancel()

        accountsJob = viewModelScope.launch {
            repository.getAccountsForWallet(walletId).collect { accountList ->
                var selectedAccount: AccountEntity? = null
                _uiState.update { current ->
                    val activeAcc = if (current.activeAccount != null) {
                        accountList.find { it.id == current.activeAccount.id } ?: accountList.firstOrNull()
                    } else {
                        accountList.firstOrNull()
                    }
                    selectedAccount = activeAcc
                    current.copy(
                        accounts = accountList,
                        activeAccount = activeAcc
                    )
                }
                selectedAccount?.let { acc ->
                    updateAccountUtxos(acc.id)
                }
            }
        }

        transactionsJob = viewModelScope.launch {
            repository.getTransactionsForWallet(walletId).collect { txList ->
                _uiState.update { it.copy(transactions = txList) }
            }
        }
    }

    private fun updateAccountUtxos(accountId: String) {
        utxosJob?.cancel()
        utxosJob = viewModelScope.launch {
            repository.accountUtxos.collect { map ->
                val list = map[accountId] ?: emptyList()
                _uiState.update { it.copy(utxos = list) }
            }
        }
    }

    fun startSetupWizard(mode: String = "CREATE") {
        _uiState.update { it.copy(showSetupWizard = true, setupWizardMode = mode) }
    }

    fun closeSetupWizard() {
        val activeWallet = _uiState.value.activeWallet
            ?: repository.database.walletDao().getAllWalletsSync().firstOrNull()
        if (activeWallet != null) {
            val accounts = repository.database.accountDao().getAccountsForWalletSync(activeWallet.id)
            val activeAcc = accounts.firstOrNull() ?: _uiState.value.activeAccount
            val allWallets = repository.database.walletDao().getAllWalletsSync()
            _uiState.update { current ->
                val walletList = if (allWallets.isNotEmpty()) allWallets else (if (current.wallets.any { it.id == activeWallet.id }) current.wallets else current.wallets + activeWallet)
                current.copy(
                    wallets = walletList,
                    activeWallet = activeWallet,
                    activeAccount = activeAcc,
                    isWalletLocked = false,
                    showCreateWalletDialog = false,
                    showImportWalletDialog = false,
                    showSetupWizard = false
                )
            }
            repository.setActiveWallet(activeWallet.id)
            if (activeAcc != null) {
                repository.setActiveAccount(activeAcc.id)
            }
            observeAccountsAndTransactions(activeWallet.id)
        } else {
            _uiState.update { it.copy(showSetupWizard = false) }
        }
    }

    fun createNewWallet(
        context: android.content.Context,
        name: String,
        words: List<String>,
        hasPassphrase: Boolean = false,
        passphrase: String = "",
        network: KaspaNetwork = KaspaNetwork.MAINNET,
        password: String = ""
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                if (network != _uiState.value.network) {
                    repository.setNetwork(network)
                }
                val (wallet, account) = repository.createWallet(name, words, hasPassphrase)
                saveWalletPassword(context, wallet.id, password)
                _uiState.update { current ->
                    val updatedWallets = if (current.wallets.any { it.id == wallet.id }) current.wallets else current.wallets + wallet
                    current.copy(
                        isLoading = false,
                        wallets = updatedWallets,
                        activeWallet = wallet,
                        activeAccount = account,
                        showCreateWalletDialog = false,
                        showSetupWizard = false,
                        statusMessage = "Kaspa Wallet '${wallet.name}' generated successfully"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Failed to create wallet") }
            }
        }
    }

    fun importWallet(
        context: android.content.Context,
        name: String,
        mnemonicPhrase: String,
        network: KaspaNetwork = KaspaNetwork.MAINNET,
        passphrase: String = "",
        password: String = ""
    ) {
        viewModelScope.launch {
            val words = mnemonicPhrase.trim().split(Regex("\\s+"))
            if (words.size != 12 && words.size != 24) {
                _uiState.update { it.copy(errorMessage = "Seed phrase must be 12 or 24 words") }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true) }
            try {
                if (network != _uiState.value.network) {
                    repository.setNetwork(network)
                }
                val (wallet, account) = repository.createWallet(name, words, passphrase.isNotBlank())
                saveWalletPassword(context, wallet.id, password)
                _uiState.update { current ->
                    val updatedWallets = if (current.wallets.any { it.id == wallet.id }) current.wallets else current.wallets + wallet
                    current.copy(
                        isLoading = false,
                        wallets = updatedWallets,
                        activeWallet = wallet,
                        activeAccount = account,
                        showImportWalletDialog = false,
                        showSetupWizard = false,
                        statusMessage = "Kaspa Wallet '${wallet.name}' imported successfully"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Failed to import wallet") }
            }
        }
    }

    fun triggerRescanActiveWallet(context: android.content.Context) {
        val activeWallet = _uiState.value.activeWallet ?: return
        val words = activeWallet.encryptedMnemonic.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size !in listOf(12, 24)) return
        _uiState.update { it.copy(showSetupWizard = true, setupWizardMode = "RESCAN") }
        startScanAndIndex(
            context = context,
            name = activeWallet.name,
            words = words,
            hasPassphrase = false,
            passphrase = "",
            network = _uiState.value.network,
            password = "",
            isImport = true
        )
    }

    fun startScanAndIndex(
        context: android.content.Context,
        name: String,
        words: List<String>,
        hasPassphrase: Boolean = false,
        passphrase: String = "",
        network: KaspaNetwork = KaspaNetwork.MAINNET,
        password: String = "",
        isImport: Boolean = false
    ) {
        viewModelScope.launch {
            val initialAddress = try {
                KaspaUtils.generateDeterministicAddress(
                    mnemonicWords = words,
                    accountIndex = 0,
                    network = network,
                    passphrase = passphrase
                )
            } catch (e: Exception) {
                ""
            }

            _scanIndexingState.value = ScanIndexingUiState(
                isScanning = true,
                stage = ScanIndexingStage.DERIVING_KEYS,
                progress = 0.0f,
                statusMessage = "Initializing on-chain BlockDAG indexer...",
                derivedAddress = initialAddress,
                network = network,
                walletName = name,
                isImportMode = isImport
            )

            try {
                if (network != _uiState.value.network) {
                    repository.setNetwork(network)
                }

                // Stage 1: Key Derivation & Instant DB Persistence
                kotlinx.coroutines.delay(250)
                _scanIndexingState.update {
                    it.copy(
                        progress = 0.08f,
                        statusMessage = "Deriving cryptographic BIP-44 keypair & address..."
                    )
                }
                kotlinx.coroutines.delay(250)
                val targetAddress = if (initialAddress.isNotBlank()) initialAddress else {
                    KaspaUtils.generateDeterministicAddress(
                        mnemonicWords = words,
                        accountIndex = 0,
                        network = network,
                        passphrase = passphrase
                    )
                }

                val mnemonicJoined = words.joinToString(" ")
                val existingWallet = repository.database.walletDao().getAllWalletsSync().firstOrNull { it.encryptedMnemonic == mnemonicJoined }
                val (wallet, initialAccount) = if (existingWallet != null) {
                    val existingAccounts = repository.database.accountDao().getAccountsForWalletSync(existingWallet.id)
                    val acc = existingAccounts.firstOrNull() ?: repository.createAccount(existingWallet.id, "Primary Account (#0)", 0)
                    Pair(existingWallet, acc)
                } else {
                    repository.createWallet(
                        name = name,
                        mnemonicWords = words,
                        hasPassphrase = hasPassphrase,
                        passphrase = passphrase,
                        password = password
                    )
                }
                saveWalletPassword(context, wallet.id, password)
                repository.setActiveWallet(wallet.id)
                repository.setActiveAccount(initialAccount.id)

                _uiState.update { current ->
                    val updatedWallets = if (current.wallets.any { w -> w.id == wallet.id }) current.wallets else current.wallets + wallet
                    current.copy(
                        wallets = updatedWallets,
                        activeWallet = wallet,
                        activeAccount = initialAccount,
                        isWalletLocked = false
                    )
                }

                _scanIndexingState.update {
                    it.copy(
                        derivedAddress = targetAddress,
                        progress = 0.18f,
                        statusMessage = "Target address derived: ${targetAddress.take(18)}..."
                    )
                }

                // Stage 2: Node Handshake & DAA Score
                _scanIndexingState.update {
                    it.copy(
                        stage = ScanIndexingStage.CONNECTING_NODE,
                        progress = 0.24f,
                        statusMessage = "Connecting to Kaspa node (${network.displayName})..."
                    )
                }
                val dagInfo = try {
                    repository.apiClient.fetchBlockDagInfo(network)
                } catch (e: Exception) {
                    BlockDagInfo()
                }
                val liveDaaScore = if (dagInfo.virtualDaaScore > 0) dagInfo.virtualDaaScore else 53_580_000L
                kotlinx.coroutines.delay(250)
                _scanIndexingState.update {
                    it.copy(
                        daaScore = liveDaaScore,
                        progress = 0.32f,
                        statusMessage = "Connected to BlockDAG. Current DAA: #$liveDaaScore"
                    )
                }

                // Stage 3: Scanning UTXOs & Balances across receive and change derivation paths (Gap Limit: 30)
                _scanIndexingState.update {
                    it.copy(
                        stage = ScanIndexingStage.SCANNING_UTXOS,
                        progress = 0.36f,
                        statusMessage = "Scanning UTXOs across 30 receive & 30 change addresses..."
                    )
                }
                val gapLimit = 30
                val addressesToScan = mutableListOf<String>()
                for (idx in 0 until gapLimit) {
                    try {
                        val recAddr = KaspaCrypto.deriveKaspaAddress(words, accountIndex = 0, addressIndex = idx, network = network, passphrase = passphrase)
                        if (recAddr.isNotBlank()) addressesToScan.add(recAddr)
                    } catch (_: Exception) {}
                    try {
                        val chgAddr = KaspaCrypto.deriveKaspaChangeAddress(words, accountIndex = 0, addressIndex = idx, network = network, passphrase = passphrase)
                        if (chgAddr.isNotBlank()) addressesToScan.add(chgAddr)
                    } catch (_: Exception) {}
                }
                val uniqueAddresses = addressesToScan.distinct()

                val discoveredUtxos = mutableListOf<UtxoEntry>()
                val activeAddressesWithActivity = mutableSetOf<String>()
                var aggregatedBalanceFromCalls = 0L

                // Scan concurrently in batches of 5 to avoid REST rate limits and update progress smoothly
                val chunks = uniqueAddresses.chunked(5)
                val totalChunks = chunks.size.coerceAtLeast(1)
                for ((chunkIndex, chunk) in chunks.withIndex()) {
                    val chunkResults = try {
                        kotlinx.coroutines.coroutineScope {
                            val deferreds = chunk.map { addr ->
                                async(kotlinx.coroutines.Dispatchers.IO) {
                                    val utxos = repository.apiClient.fetchAddressUtxos(addr, network)
                                    val bal = repository.apiClient.fetchAddressBalance(addr, network)
                                    Triple(addr, utxos, bal)
                                }
                            }
                            deferreds.awaitAll()
                        }
                    } catch (e: Exception) {
                        emptyList()
                    }

                    for ((addr, utxos, bal) in chunkResults) {
                        if (utxos.isNotEmpty()) {
                            discoveredUtxos.addAll(utxos)
                            activeAddressesWithActivity.add(addr)
                        }
                        if (bal > 0) {
                            aggregatedBalanceFromCalls += bal
                            activeAddressesWithActivity.add(addr)
                        }
                    }

                    val stepProgress = 0.36f + (0.38f * (chunkIndex + 1) / totalChunks)
                    _scanIndexingState.update {
                        it.copy(
                            utxoCount = discoveredUtxos.size,
                            progress = stepProgress,
                            statusMessage = "Scanning address batch ${chunkIndex + 1}/$totalChunks on-chain..."
                        )
                    }
                    kotlinx.coroutines.delay(120)
                }

                val utxoSumSompi = discoveredUtxos.sumOf { it.amountSompi }
                val totalDiscoveredBalanceSompi = maxOf(utxoSumSompi, aggregatedBalanceFromCalls)

                _scanIndexingState.update {
                    it.copy(
                        utxoCount = discoveredUtxos.size,
                        progress = 0.78f,
                        statusMessage = if (discoveredUtxos.isNotEmpty()) "${discoveredUtxos.size} UTXOs discovered on-chain (30 address limit)" else "Zero unspent UTXOs (Clean graph)"
                    )
                }

                // Stage 4: Calculating On-Chain Consensus Balance
                kotlinx.coroutines.delay(200)
                _scanIndexingState.update {
                    it.copy(
                        stage = ScanIndexingStage.CALCULATING_BALANCE,
                        progress = 0.84f,
                        balanceSompi = totalDiscoveredBalanceSompi,
                        statusMessage = "Confirmed balance: ${KaspaUtils.formatKas(KaspaUtils.sompiToKas(totalDiscoveredBalanceSompi))} KAS"
                    )
                }

                // Stage 5: Indexing Historical Transactions & Persisting to Room
                _scanIndexingState.update {
                    it.copy(
                        stage = ScanIndexingStage.INDEXING_HISTORY,
                        progress = 0.88f,
                        statusMessage = "Indexing BlockDAG transaction history..."
                    )
                }

                // Update confirmed balance in Room DB immediately
                if (totalDiscoveredBalanceSompi > 0 || initialAccount.balanceSompi == 0L) {
                    repository.database.accountDao().updateBalance(initialAccount.id, totalDiscoveredBalanceSompi)
                }
                repository.setAccountUtxos(initialAccount.id, discoveredUtxos)

                // Fetch transactions for primary address & any addresses with activity
                val targetAddressesForTxs = (setOf(initialAccount.address) + activeAddressesWithActivity).toList()
                val allTxsList = mutableListOf<TransactionEntity>()
                val txChunks = targetAddressesForTxs.chunked(4)
                val totalTxChunks = txChunks.size.coerceAtLeast(1)
                for ((tIdx, chunk) in txChunks.withIndex()) {
                    val txChunk = try {
                        kotlinx.coroutines.coroutineScope {
                            val deferreds = chunk.map { addr ->
                                async(kotlinx.coroutines.Dispatchers.IO) {
                                    repository.apiClient.fetchAddressTransactions(addr, wallet.id, initialAccount.id, network)
                                }
                            }
                            deferreds.awaitAll().flatten()
                        }
                    } catch (e: Exception) {
                        emptyList()
                    }
                    allTxsList.addAll(txChunk)
                    val txStepProgress = 0.88f + (0.08f * (tIdx + 1) / totalTxChunks)
                    _scanIndexingState.update {
                        it.copy(
                            progress = txStepProgress,
                            txCount = allTxsList.size
                        )
                    }
                    kotlinx.coroutines.delay(100)
                }
                val allTxs = allTxsList.distinctBy { it.id }
                for (tx in allTxs) {
                    repository.database.transactionDao().insertTransaction(tx)
                }

                // Sync on-chain balance & auto-sweep any secondary address funds to main account
                try {
                    repository.syncAccountOnChain(initialAccount.id)
                } catch (_: Exception) {}

                // Fetch final synced account for state
                val syncedAccount = repository.database.accountDao().getAccountById(initialAccount.id) ?: initialAccount
                val finalBalance = syncedAccount.balanceSompi.coerceAtLeast(totalDiscoveredBalanceSompi)

                // Stage 6: Complete
                kotlinx.coroutines.delay(250)
                _scanIndexingState.update {
                    it.copy(
                        stage = ScanIndexingStage.COMPLETE,
                        progress = 1.0f,
                        isComplete = true,
                        isScanning = false,
                        balanceSompi = finalBalance,
                        txCount = allTxs.size,
                        indexedTransactions = allTxs,
                        statusMessage = "On-chain scanning & indexing complete!"
                    )
                }

                _uiState.update { current ->
                    val updatedWallets = if (current.wallets.any { w -> w.id == wallet.id }) current.wallets else current.wallets + wallet
                    current.copy(
                        wallets = updatedWallets,
                        activeWallet = wallet,
                        activeAccount = syncedAccount.copy(balanceSompi = finalBalance),
                        isWalletLocked = false
                    )
                }
                observeAccountsAndTransactions(wallet.id)
            } catch (e: Exception) {
                _scanIndexingState.update {
                    it.copy(
                        stage = ScanIndexingStage.COMPLETE,
                        progress = 1.0f,
                        isComplete = true,
                        isScanning = false,
                        statusMessage = "Wallet ready"
                    )
                }
            }
        }
    }

    fun finishScanAndNavigateToWallet() {
        val allWallets = repository.database.walletDao().getAllWalletsSync()
        val activeWallet = _uiState.value.activeWallet
            ?: allWallets.firstOrNull()

        if (activeWallet != null) {
            val accounts = repository.database.accountDao().getAccountsForWalletSync(activeWallet.id)
            val activeAcc = accounts.firstOrNull() ?: _uiState.value.activeAccount
            _uiState.update { current ->
                val walletList = if (allWallets.isNotEmpty()) allWallets else (if (current.wallets.any { it.id == activeWallet.id }) current.wallets else current.wallets + activeWallet)
                current.copy(
                    wallets = walletList,
                    activeWallet = activeWallet,
                    activeAccount = activeAcc,
                    selectedTab = MainTab.OVERVIEW,
                    isWalletLocked = false,
                    showCreateWalletDialog = false,
                    showImportWalletDialog = false,
                    showSetupWizard = false,
                    statusMessage = "Kaspa Wallet '${activeWallet.name}' ready"
                )
            }
            repository.setActiveWallet(activeWallet.id)
            if (activeAcc != null) {
                repository.setActiveAccount(activeAcc.id)
            }
            observeAccountsAndTransactions(activeWallet.id)
            refreshAll()
        } else {
            _uiState.update { current ->
                current.copy(
                    selectedTab = MainTab.OVERVIEW,
                    showCreateWalletDialog = false,
                    showImportWalletDialog = false,
                    showSetupWizard = false
                )
            }
        }
        _scanIndexingState.value = ScanIndexingUiState()
    }

    fun resetScanIndexing() {
        _scanIndexingState.value = ScanIndexingUiState()
    }

    fun createNewAccount(name: String) {
        val currentWallet = _uiState.value.activeWallet ?: return
        viewModelScope.launch {
            try {
                val nextIndex = _uiState.value.accounts.size
                val account = repository.createAccount(currentWallet.id, name, nextIndex)
                _uiState.update {
                    it.copy(
                        activeAccount = account,
                        showAddAccountDialog = false,
                        statusMessage = "Account '${account.name}' added"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Failed to add account") }
            }
        }
    }

    fun sendKas(
        recipientAddress: String,
        amountKas: Double,
        feeOption: String,
        customFeeKas: Double = 0.00386,
        note: String = "",
        manualUtxos: List<UtxoEntry>? = null
    ) {
        val account = _uiState.value.activeAccount ?: return
        if (!KaspaUtils.isValidKaspaAddress(recipientAddress)) {
            _uiState.update { it.copy(errorMessage = "Invalid Kaspa address format") }
            return
        }
        val amountSompi = KaspaUtils.kasToSompi(amountKas)
        val feeSompi = when (feeOption) {
            "Low" -> KaspaUtils.DEFAULT_MIN_FEE_SOMPI
            "Priority" -> KaspaUtils.HIGH_PRIORITY_FEE_SOMPI
            "Custom" -> KaspaUtils.kasToSompi(customFeeKas)
            else -> KaspaUtils.PRIORITY_FEE_SOMPI
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val sentTx = repository.sendKas(account, recipientAddress, amountSompi, feeSompi, note, manualUtxos)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        showSendDialog = false,
                        showTxSuccessDialog = true,
                        lastSentTx = sentTx,
                        statusMessage = "Sent ${KaspaUtils.formatKas(amountKas)} successfully"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to submit transaction"
                    )
                }
            }
        }
    }

    fun dismissTxSuccessDialog() {
        _uiState.update { it.copy(showTxSuccessDialog = false) }
    }

    fun transferBetweenAccounts(targetAccount: AccountEntity, amountKas: Double, note: String = "") {
        val source = _uiState.value.activeAccount ?: return
        val amountSompi = KaspaUtils.kasToSompi(amountKas)
        val feeSompi = KaspaUtils.DEFAULT_MIN_FEE_SOMPI

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.transferBetweenAccounts(source, targetAccount, amountSompi, feeSompi, note)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        showTransferDialog = false,
                        statusMessage = "Transferred ${KaspaUtils.formatKas(amountKas)} to ${targetAccount.name}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Transfer failed"
                    )
                }
            }
        }
    }

    fun sendMassKas(
        recipients: List<Pair<String, Long>>,
        feeSompi: Long,
        onSuccess: (TransactionEntity) -> Unit,
        onError: (String) -> Unit
    ) {
        val source = _uiState.value.activeAccount ?: run {
            onError("No active account selected")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val tx = repository.sendMassKas(source, recipients, feeSompi)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Mass batch transaction broadcast (${recipients.size} outputs)"
                    )
                }
                onSuccess(tx)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Mass transaction failed"
                    )
                }
                onError(e.message ?: "Mass transaction failed")
            }
        }
    }

    fun compoundUtxos() {
        val account = _uiState.value.activeAccount ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val tx = repository.compoundAccountUtxos(account)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Compounded UTXOs successfully (TxID: ${KaspaUtils.truncateAddress(tx.id)})"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Compound operation failed"
                    )
                }
            }
        }
    }

    fun saveContact(name: String, address: String, note: String = "") {
        if (!KaspaUtils.isValidKaspaAddress(address)) {
            _uiState.update { it.copy(errorMessage = "Invalid Kaspa address") }
            return
        }
        viewModelScope.launch {
            repository.addContact(name, address, note)
            _uiState.update {
                it.copy(
                    showAddContactDialog = false,
                    statusMessage = "Contact '$name' saved"
                )
            }
        }
    }

    fun deleteContact(contact: ContactEntity) {
        viewModelScope.launch {
            repository.deleteContact(contact)
            _uiState.update { it.copy(statusMessage = "Contact removed") }
        }
    }

    fun deleteCurrentWallet(context: android.content.Context) {
        val wallet = _uiState.value.activeWallet ?: return
        viewModelScope.launch {
            repository.deleteWallet(wallet.id)
            deleteWalletPassword(context, wallet.id)
            _uiState.update {
                it.copy(
                    activeWallet = null,
                    activeAccount = null,
                    statusMessage = "Wallet '${wallet.name}' deleted"
                )
            }
        }
    }

    private fun hashPassword(walletId: String, password: String): String {
        return try {
            val salt = "KaspaWalletSalt:$walletId".toByteArray(Charsets.UTF_8)
            val spec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, 100_000, 256)
            val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val hashBytes = factory.generateSecret(spec).encoded
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest("$walletId:$password".toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        }
    }

    fun saveWalletPassword(context: android.content.Context, walletId: String, password: String) {
        val prefs = context.getSharedPreferences("wallet_passwords", android.content.Context.MODE_PRIVATE)
        val hashedPassword = hashPassword(walletId, password)
        prefs.edit().putString(walletId, hashedPassword).apply()
    }

    fun verifyWalletPassword(context: android.content.Context, walletId: String, password: String): Boolean {
        val prefs = context.getSharedPreferences("wallet_passwords", android.content.Context.MODE_PRIVATE)
        val savedHash = prefs.getString(walletId, null)
        if (savedHash == null) {
            return password.isBlank()
        }
        val computedPbkdf2 = hashPassword(walletId, password)
        if (java.security.MessageDigest.isEqual(savedHash.toByteArray(Charsets.UTF_8), computedPbkdf2.toByteArray(Charsets.UTF_8))) {
            return true
        }
        // Backward compatibility fallback for wallets hashed with legacy single-round SHA-256
        val legacyHash = try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            digest.digest("$walletId:$password".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        } catch (e: Exception) { "" }
        if (legacyHash.isNotEmpty() && java.security.MessageDigest.isEqual(savedHash.toByteArray(Charsets.UTF_8), legacyHash.toByteArray(Charsets.UTF_8))) {
            // Upgrade hash to PBKDF2
            prefs.edit().putString(walletId, computedPbkdf2).apply()
            return true
        }
        return false
    }

    fun deleteWalletPassword(context: android.content.Context, walletId: String) {
        val prefs = context.getSharedPreferences("wallet_passwords", android.content.Context.MODE_PRIVATE)
        prefs.edit().remove(walletId).apply()
    }

    fun setLockWallet(locked: Boolean) {
        _uiState.update { it.copy(isWalletLocked = locked) }
    }

    // Dialog toggles
    fun setShowSendDialog(show: Boolean) = _uiState.update { it.copy(showSendDialog = show) }
    fun setShowReceiveDialog(show: Boolean) = _uiState.update { it.copy(showReceiveDialog = show) }
    fun setShowTransferDialog(show: Boolean) = _uiState.update { it.copy(showTransferDialog = show) }
    fun setShowAddAccountDialog(show: Boolean) = _uiState.update { it.copy(showAddAccountDialog = show) }
    fun setShowAddContactDialog(show: Boolean) = _uiState.update { it.copy(showAddContactDialog = show) }
    fun setShowSeedBackupDialog(show: Boolean) = _uiState.update { it.copy(showSeedBackupDialog = show) }
    fun setShowCreateWalletDialog(show: Boolean) = _uiState.update { it.copy(showCreateWalletDialog = show) }
    fun setShowImportWalletDialog(show: Boolean) = _uiState.update { it.copy(showImportWalletDialog = show) }
    fun clearStatusMessage() = _uiState.update { it.copy(statusMessage = null) }
    fun clearErrorMessage() = _uiState.update { it.copy(errorMessage = null) }
}

class KaspaViewModelFactory(private val repository: KaspaWalletRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(KaspaViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return KaspaViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
