package com.example.kaspawallet.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.example.kaspawallet.ui.theme.noRippleClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.kaspawallet.R
import com.example.kaspawallet.data.model.WalletEntity
import com.example.kaspawallet.data.security.BiometricAuthManager
import com.example.kaspawallet.ui.KaspaViewModel
import com.example.kaspawallet.ui.WalletUiState
import com.example.kaspawallet.ui.theme.*

/**
 * Authentic Kaspa Wallet Login / Keystore Unlock Screen
 * Matching original Kaspa Web Wallet & Rusty Kaspa desktop login flow
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletUnlockScreen(
    state: WalletUiState,
    viewModel: KaspaViewModel,
    onCreateNewWallet: () -> Unit,
    onImportSeed: () -> Unit
) {
    val context = LocalContext.current
    val fragmentActivity = context as? FragmentActivity
    val isBiometricAvailable = remember { BiometricAuthManager.isBiometricAvailable(context) }

    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showWalletSelector by remember { mutableStateOf(false) }

    val activeWallet = state.activeWallet ?: state.wallets.firstOrNull()

    fun launchBiometricUnlock() {
        if (fragmentActivity != null && isBiometricAvailable) {
            BiometricAuthManager.promptBiometric(
                activity = fragmentActivity,
                title = "Kaspa Wallet Biometric Unlock",
                subtitle = "Authenticate using fingerprint or face unlock",
                onSuccess = {
                    viewModel.setLockWallet(false)
                    Toast.makeText(context, "Wallet unlocked via biometrics", Toast.LENGTH_SHORT).show()
                },
                onError = { err ->
                    errorMessage = err
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        if (isBiometricAvailable) {
            launchBiometricUnlock()
        }
    }

    fun attemptUnlock() {
        if (password.isBlank()) {
            errorMessage = "Please enter your wallet password"
            return
        }
        isVerifying = true
        errorMessage = null
        
        val walletId = activeWallet?.id ?: state.wallets.firstOrNull()?.id ?: ""
        if (viewModel.verifyWalletPassword(context, walletId, password)) {
            viewModel.setLockWallet(false)
            isVerifying = false
            Toast.makeText(context, "Wallet unlocked", Toast.LENGTH_SHORT).show()
        } else {
            isVerifying = false
            errorMessage = "Incorrect password"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KaspaBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Official Kaspa Teal >> Chevron Logo
        Image(
            painter = painterResource(id = R.drawable.ic_kaspa_teal_logo),
            contentDescription = "KASCRYPT Logo",
            modifier = Modifier.size(76.dp)
        )

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            "KASCRYPT",
            color = KaspaTextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp
        )

        Text(
            "Encrypted Keystore Locked",
            color = KaspaTextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Wallet Selection Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .noRippleClickable { if (state.wallets.size > 1) showWalletSelector = true },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = KaspaSurface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(KaspaPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = KaspaPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            activeWallet?.name ?: "Kaspa Wallet",
                            color = KaspaTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${activeWallet?.wordCount ?: 12} Words • ${state.network.displayName}",
                            color = KaspaTextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                if (state.wallets.size > 1) {
                    Icon(Icons.Default.UnfoldMore, contentDescription = "Switch Wallet", tint = KaspaPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Password Input
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                if (errorMessage != null) errorMessage = null
            },
            label = { Text("Wallet Password") },
            placeholder = { Text("Enter your password to unlock") },
            leadingIcon = {
                Icon(Icons.Default.Lock, contentDescription = null, tint = KaspaPrimary)
            },
            trailingIcon = {
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(
                        if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle password visibility",
                        tint = KaspaTextSecondary
                    )
                }
            },
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { attemptUnlock() }),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("wallet_password_input"),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = KaspaTextPrimary,
                unfocusedTextColor = KaspaTextPrimary
            )
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = KaspaError, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(errorMessage ?: "", color = KaspaError, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isBiometricAvailable) {
            OutlinedButton(
                onClick = { launchBiometricUnlock() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("biometric_unlock_button"),
                shape = RoundedCornerShape(14.dp),
                border = ButtonDefaults.outlinedButtonBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(KaspaPrimary)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = KaspaPrimary)
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Biometric / Face Unlock", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Unlock Button
        Button(
            onClick = { attemptUnlock() },
            enabled = !isVerifying,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("unlock_wallet_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = KaspaPrimary,
                contentColor = Color(0xFF003731)
            )
        ) {
            if (isVerifying) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF003731), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Decrypting Keystore...", fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.LockOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Unlock Wallet", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Switch Wallet Modal
    if (showWalletSelector) {
        AlertDialog(
            onDismissRequest = { showWalletSelector = false },
            title = { Text("Select Kaspa Wallet", color = KaspaTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.wallets.forEach { w ->
                        val isSelected = w.id == activeWallet?.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .noRippleClickable {
                                    viewModel.selectWallet(w)
                                    showWalletSelector = false
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) KaspaPrimary.copy(alpha = 0.15f) else KaspaSurfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(w.name, color = if (isSelected) KaspaPrimaryGlow else KaspaTextPrimary, fontWeight = FontWeight.Bold)
                                    Text("${w.wordCount} Words", color = KaspaTextMuted, fontSize = 11.sp)
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = KaspaPrimary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWalletSelector = false }) {
                    Text("Close", color = KaspaPrimary)
                }
            },
            containerColor = KaspaSurface
        )
    }
}
