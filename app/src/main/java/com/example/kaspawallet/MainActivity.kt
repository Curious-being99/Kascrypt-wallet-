package com.example.kaspawallet

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.kaspawallet.data.security.BiometricAuthManager
import com.example.kaspawallet.ui.KaspaViewModel
import com.example.kaspawallet.ui.KaspaViewModelFactory
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.kaspawallet.ui.screens.MainScreen
import com.example.kaspawallet.ui.screens.SplashScreen
import com.example.kaspawallet.ui.screens.WalletSetupWizard
import com.example.kaspawallet.ui.screens.WalletUnlockScreen
import com.example.kaspawallet.ui.screens.WelcomeScreen
import com.example.kaspawallet.ui.theme.KaspaBackground
import com.example.kaspawallet.ui.theme.KaspaWalletTheme

class MainActivity : FragmentActivity() {

    private val viewModel: KaspaViewModel by viewModels {
        val app = application as KaspaApplication
        KaspaViewModelFactory(app.repository)
    }

    private val lockHandler = Handler(Looper.getMainLooper())
    private val lockRunnable = Runnable {
        viewModel.setLockWallet(true)
    }

    private fun resetLockTimer() {
        lockHandler.removeCallbacks(lockRunnable)
        val state = viewModel.uiState.value
        val hasWallet = state.wallets.isNotEmpty() || state.activeWallet != null
        if (hasWallet && !state.isWalletLocked) {
            lockHandler.postDelayed(lockRunnable, 5 * 60 * 1000) // 5 minutes in milliseconds
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        resetLockTimer()
    }

    override fun onStop() {
        super.onStop()
        val state = viewModel.uiState.value
        val hasWallet = state.wallets.isNotEmpty() || state.activeWallet != null
        if (hasWallet) {
            viewModel.setLockWallet(true)
        }
    }

    override fun onResume() {
        super.onResume()
        val state = viewModel.uiState.value
        val hasWallet = state.wallets.isNotEmpty() || state.activeWallet != null
        if (hasWallet && state.isWalletLocked && BiometricAuthManager.isBiometricAvailable(this)) {
            BiometricAuthManager.promptBiometric(
                activity = this,
                title = "Kaspa Wallet Biometric Unlock",
                subtitle = "Authenticate using fingerprint or face unlock",
                onSuccess = {
                    viewModel.setLockWallet(false)
                    Toast.makeText(this, "Wallet unlocked via biometrics", Toast.LENGTH_SHORT).show()
                },
                onError = { err ->
                    // Fallback to manual entry, no need to show intrusive error
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Protect sensitive wallet memory and screens from task switcher thumbnails and screen capture
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        enableEdgeToEdge()

        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                val hasWallet = state.wallets.isNotEmpty() || state.activeWallet != null
                if (hasWallet && !state.isWalletLocked) {
                    resetLockTimer()
                } else {
                    lockHandler.removeCallbacks(lockRunnable)
                }
            }
        }

        setContent {
            KaspaWalletTheme {
                var showSplash by androidx.compose.runtime.remember { mutableStateOf(true) }
                val state by viewModel.uiState.collectAsState()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = KaspaBackground
                ) {
                    val hasWallet = state.wallets.isNotEmpty() || state.activeWallet != null

                    if (showSplash) {
                        SplashScreen(
                            onSplashFinished = { showSplash = false }
                        )
                    } else if (!hasWallet && !state.showSetupWizard) {
                        WelcomeScreen(
                            viewModel = viewModel,
                            onCreateWallet = { viewModel.startSetupWizard("CREATE") },
                            onImportWallet = { viewModel.startSetupWizard("IMPORT") }
                        )
                    } else if (state.showSetupWizard || (!hasWallet && (state.showCreateWalletDialog || state.showImportWalletDialog))) {
                        WalletSetupWizard(
                            viewModel = viewModel,
                            initialMode = state.setupWizardMode,
                            onDismiss = {
                                viewModel.closeSetupWizard()
                                viewModel.setShowCreateWalletDialog(false)
                                viewModel.setShowImportWalletDialog(false)
                            }
                        )
                    } else if (state.isWalletLocked) {
                        WalletUnlockScreen(
                            state = state,
                            viewModel = viewModel,
                            onCreateNewWallet = { viewModel.startSetupWizard("CREATE") },
                            onImportSeed = { viewModel.startSetupWizard("IMPORT") }
                        )
                    } else {
                        MainScreen(
                            state = state,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}
