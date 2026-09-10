package com.example.kaspawallet.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.example.kaspawallet.ui.theme.noRippleClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kaspawallet.data.crypto.KaspaUtils
import com.example.kaspawallet.ui.KaspaViewModel
import com.example.kaspawallet.ui.MainTab
import com.example.kaspawallet.ui.WalletUiState
import com.example.kaspawallet.ui.dialogs.*
import com.example.kaspawallet.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: WalletUiState,
    viewModel: KaspaViewModel
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showWalletMenu by remember { mutableStateOf(false) }

    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(message = msg, withDismissAction = true)
            viewModel.clearErrorMessage()
        }
    }

    // Locked Screen Overlay if locked
    if (state.isWalletLocked) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(KaspaBackground)
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(KaspaPrimary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = KaspaPrimary, modifier = Modifier.size(40.dp))
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text("Wallet Locked", color = KaspaTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Unlock to access your accounts and transactions", color = KaspaTextSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(28.dp))
                Button(
                    onClick = { viewModel.setLockWallet(false) },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KaspaPrimary, contentColor = Color(0xFF003731))
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlock Wallet", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
        return
    }

    Scaffold(
        containerColor = KaspaBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (state.selectedTab == MainTab.OVERVIEW) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.noRippleClickable { showWalletMenu = true }
                        ) {
                            Text(
                                state.activeWallet?.name ?: "Kaspa Wallet",
                                color = KaspaTextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Switch wallet", tint = KaspaPrimary)
                        }

                        // Wallet Switch Dropdown
                        DropdownMenu(
                            expanded = showWalletMenu,
                            onDismissRequest = { showWalletMenu = false },
                            modifier = Modifier.background(KaspaSurface)
                        ) {
                            Text(
                                "Your Wallets",
                                color = KaspaTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            state.wallets.forEach { w ->
                                DropdownMenuItem(
                                    text = { Text(w.name, color = if (w.id == state.activeWallet?.id) KaspaPrimaryGlow else KaspaTextPrimary) },
                                    onClick = {
                                        viewModel.selectWallet(w)
                                        showWalletMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.AccountBalanceWallet,
                                            contentDescription = null,
                                            tint = if (w.id == state.activeWallet?.id) KaspaPrimary else KaspaTextSecondary
                                        )
                                    }
                                )
                            }
                            HorizontalDivider(color = KaspaCardBorder)
                            DropdownMenuItem(
                                text = { Text("Create New Wallet", color = KaspaPrimary) },
                                onClick = {
                                    showWalletMenu = false
                                    viewModel.setShowCreateWalletDialog(true)
                                },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = KaspaPrimary) }
                            )
                            DropdownMenuItem(
                                text = { Text("Import Seed Phrase", color = KaspaPrimary) },
                                onClick = {
                                    showWalletMenu = false
                                    viewModel.setShowImportWalletDialog(true)
                                },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = KaspaPrimary) }
                            )
                        }
                    } else {
                        Text(
                            text = state.selectedTab.title,
                            color = KaspaTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    if (state.selectedTab == MainTab.OVERVIEW) {
                        IconButton(
                            onClick = { viewModel.refreshAll() },
                            enabled = !state.isRefreshing
                        ) {
                            if (state.isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = KaspaPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = KaspaPrimary)
                            }
                        }
                        IconButton(onClick = { viewModel.setLockWallet(true) }) {
                            Icon(Icons.Outlined.Lock, contentDescription = "Lock", tint = KaspaTextSecondary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = KaspaBackground,
                    titleContentColor = KaspaTextPrimary
                )
            )
        },
        bottomBar = {
            Surface(
                color = KaspaBackground,
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .height(80.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tabs = listOf(
                        Triple(MainTab.OVERVIEW, Icons.Filled.AccountBalanceWallet to Icons.Outlined.AccountBalanceWallet, "Wallet"),
                        Triple(MainTab.TRANSACTIONS, Icons.Filled.ReceiptLong to Icons.Outlined.ReceiptLong, "Activity"),
                        Triple(MainTab.BLOCKDAG, Icons.Filled.Hub to Icons.Outlined.Hub, "BlockDAG"),
                        Triple(MainTab.TOOLS, Icons.Filled.Construction to Icons.Outlined.Construction, "UTXO"),
                        Triple(MainTab.SETTINGS, Icons.Filled.Settings to Icons.Outlined.Settings, "Settings")
                    )

                    tabs.forEach { (tab, icons, label) ->
                        val isSelected = state.selectedTab == tab
                        val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = { viewModel.selectTab(tab) }
                                )
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) icons.first else icons.second,
                                contentDescription = label,
                                tint = if (isSelected) KaspaPrimaryGlow else KaspaTextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                color = if (isSelected) KaspaPrimaryGlow else KaspaTextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab Content
            Box(modifier = Modifier.weight(1f)) {
                when (state.selectedTab) {
                    MainTab.OVERVIEW -> OverviewTab(state = state, viewModel = viewModel)
                    MainTab.TRANSACTIONS -> TransactionsTab(state = state)
                    MainTab.BLOCKDAG -> BlockDagTab(state = state, viewModel = viewModel)
                    MainTab.TOOLS -> ToolsTab(state = state, viewModel = viewModel)
                    MainTab.SETTINGS -> SettingsTab(state = state, viewModel = viewModel)
                }
            }
        }
    }

    // Dialogs
    if (state.showSendDialog) {
        SendKasDialog(
            viewModel = viewModel,
            activeAccount = state.activeAccount,
            contacts = state.contacts,
            utxos = state.utxos,
            onDismiss = { viewModel.setShowSendDialog(false) }
        )
    }

    if (state.showReceiveDialog) {
        ReceiveKasDialog(
            activeAccount = state.activeAccount,
            activeWallet = state.activeWallet,
            network = state.network,
            onDismiss = { viewModel.setShowReceiveDialog(false) }
        )
    }

    if (state.showTransferDialog) {
        TransferDialog(
            viewModel = viewModel,
            accounts = state.accounts,
            activeAccount = state.activeAccount,
            onDismiss = { viewModel.setShowTransferDialog(false) }
        )
    }

    if (state.showAddAccountDialog) {
        AddAccountDialog(
            viewModel = viewModel,
            accountsCount = state.accounts.size,
            onDismiss = { viewModel.setShowAddAccountDialog(false) }
        )
    }

    if (state.showAddContactDialog) {
        AddContactDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.setShowAddContactDialog(false) }
        )
    }

    if (state.showSeedBackupDialog) {
        state.activeWallet?.let { w ->
            SeedBackupDialog(
                mnemonicPhrase = w.encryptedMnemonic,
                onDismiss = { viewModel.setShowSeedBackupDialog(false) }
            )
        }
    }

    if (state.showTxSuccessDialog && state.lastSentTx != null) {
        TransactionSuccessDialog(
            transaction = state.lastSentTx,
            network = state.network,
            onDismiss = { viewModel.dismissTxSuccessDialog() }
        )
    }

    if (state.showSetupWizard || state.showCreateWalletDialog || state.showImportWalletDialog) {
        WalletSetupWizard(
            viewModel = viewModel,
            initialMode = if (state.showImportWalletDialog || state.setupWizardMode == "IMPORT") "IMPORT" else "CREATE",
            onDismiss = {
                viewModel.closeSetupWizard()
                viewModel.setShowCreateWalletDialog(false)
                viewModel.setShowImportWalletDialog(false)
            }
        )
    }
}
