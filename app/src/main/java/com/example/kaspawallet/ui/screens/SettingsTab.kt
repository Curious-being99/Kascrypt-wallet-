package com.example.kaspawallet.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.example.kaspawallet.ui.theme.noRippleClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.kaspawallet.data.crypto.KaspaUtils
import com.example.kaspawallet.data.security.BiometricAuthManager
import com.example.kaspawallet.data.model.KaspaNetwork
import com.example.kaspawallet.ui.KaspaViewModel
import com.example.kaspawallet.ui.WalletUiState
import com.example.kaspawallet.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    state: WalletUiState,
    viewModel: KaspaViewModel
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deletePasswordInput by remember { mutableStateOf("") }
    var isDeletePasswordVisible by remember { mutableStateOf(false) }
    var showSeedPhraseAuthDialog by remember { mutableStateOf(false) }
    var seedPhrasePasswordInput by remember { mutableStateOf("") }
    var isSeedPhrasePasswordVisible by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
    ) {
        // Address Book / Contacts
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = KaspaSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(KaspaPrimary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Contacts, contentDescription = null, tint = KaspaPrimary, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Address Book", color = KaspaTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("${state.contacts.size} contacts saved", color = KaspaTextSecondary, fontSize = 11.sp)
                            }
                        }
                        Button(
                            onClick = { viewModel.setShowAddContactDialog(true) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KaspaPrimary, contentColor = Color(0xFF003731))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Contact", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (state.contacts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(KaspaSurfaceVariant)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No contacts saved yet. Add frequently used Kaspa addresses for quick access.",
                                color = KaspaTextSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.contacts.forEach { contact ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = KaspaSurfaceVariant),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(KaspaPrimary.copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    contact.name.take(1).uppercase(),
                                                    color = KaspaPrimaryGlow,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(contact.name, color = KaspaTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                                Text(
                                                    KaspaUtils.truncateAddress(contact.address, 12, 8),
                                                    color = KaspaTextSecondary,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                if (contact.note.isNotBlank()) {
                                                    Text(contact.note, color = KaspaTextMuted, fontSize = 10.sp)
                                                }
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    clipboard.setPrimaryClip(ClipData.newPlainText("Kaspa Address", contact.address))
                                                    Toast.makeText(context, "Contact address copied", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = KaspaTextSecondary, modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(
                                                onClick = { viewModel.deleteContact(contact) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = KaspaError, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Network Selection
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = KaspaSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Kaspa Network", color = KaspaTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))

                    KaspaNetwork.entries.forEach { net ->
                        val isSelected = state.network == net
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .noRippleClickable { viewModel.setNetwork(net) }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    net.displayName,
                                    color = if (isSelected) KaspaPrimaryGlow else KaspaTextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                                Text("Prefix: ${net.prefix}", color = KaspaTextMuted, fontSize = 11.sp)
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.setNetwork(net) },
                                colors = RadioButtonDefaults.colors(selectedColor = KaspaPrimary)
                            )
                        }
                    }
                }
            }
        }

        // Currency Preference
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = KaspaSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Display Fiat Currency", color = KaspaTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("USD", "EUR", "GBP", "CAD", "JPY").forEach { curr ->
                            val isSelected = state.selectedCurrency == curr
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setCurrency(curr) },
                                label = { Text(curr) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = KaspaPrimary,
                                    selectedLabelColor = Color(0xFF003731),
                                    containerColor = KaspaSurfaceVariant,
                                    labelColor = KaspaTextSecondary
                                )
                            )
                        }
                    }
                }
            }
        }

        // Security & Backup
        item {
            val isBiometricAvailable = remember { BiometricAuthManager.isBiometricAvailable(context) }
            val fragmentActivity = context as? FragmentActivity

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = KaspaSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Security & Biometrics", color = KaspaTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)

                    // Biometric Status Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(KaspaSurfaceVariant)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = if (isBiometricAvailable) KaspaPrimaryGlow else KaspaTextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    "Biometric Protection",
                                    color = KaspaTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (isBiometricAvailable) "Fingerprint / Face Unlock Ready" else "Not Configured on Device",
                                    color = if (isBiometricAvailable) KaspaPrimaryGlow else KaspaTextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        if (isBiometricAvailable) {
                            TextButton(
                                onClick = {
                                    if (fragmentActivity != null) {
                                        BiometricAuthManager.promptBiometric(
                                            activity = fragmentActivity,
                                            title = "Test Biometric Verification",
                                            subtitle = "Verify fingerprint or face recognition",
                                            onSuccess = {
                                                Toast.makeText(context, "Biometric verification successful!", Toast.LENGTH_SHORT).show()
                                            },
                                            onError = { err ->
                                                Toast.makeText(context, "Authentication error: $err", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Test", color = KaspaPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Rescan On-Chain
                    Button(
                        onClick = {
                            viewModel.triggerRescanActiveWallet(context)
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KaspaSurfaceVariant, contentColor = KaspaPrimaryGlow)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = KaspaPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Rescan On-Chain (30 Gap Limit)", fontWeight = FontWeight.SemiBold)
                    }

                    // View Seed Phrase
                    Button(
                        onClick = {
                            val isBiometricAvailable = BiometricAuthManager.isBiometricAvailable(context)
                            val fragmentActivity = context as? FragmentActivity
                            if (isBiometricAvailable && fragmentActivity != null) {
                                BiometricAuthManager.promptBiometric(
                                    activity = fragmentActivity,
                                    title = "Unlock Secret Phrase",
                                    subtitle = "Authenticate to view your secret recovery phrase",
                                    onSuccess = {
                                        viewModel.setShowSeedBackupDialog(true)
                                    },
                                    onError = {
                                        showSeedPhraseAuthDialog = true
                                    }
                                )
                            } else {
                                showSeedPhraseAuthDialog = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KaspaSurfaceVariant, contentColor = KaspaTextPrimary)
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = KaspaPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View Secret Recovery Phrase", fontWeight = FontWeight.SemiBold)
                    }

                    // Lock Wallet
                    Button(
                        onClick = {
                            viewModel.setLockWallet(true)
                            Toast.makeText(context, "Wallet locked", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KaspaSurfaceVariant, contentColor = KaspaTextPrimary)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = KaspaPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Lock Wallet Now", fontWeight = FontWeight.SemiBold)
                    }

                    // Delete Wallet
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = KaspaError),
                        border = ButtonDefaults.outlinedButtonBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(KaspaError))
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete Current Wallet", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // About & Version Info
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = KaspaSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("KASCRYPT Wallet", color = KaspaTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Version 1.0.0 • KASCRYPT Core", color = KaspaTextSecondary, fontSize = 12.sp)
                    Text("Consensus: GHOSTDAG (PoW BlockDAG)", color = KaspaTextMuted, fontSize = 11.sp)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        val isBiometricAvailable = remember { BiometricAuthManager.isBiometricAvailable(context) }
        val fragmentActivity = context as? FragmentActivity
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { 
                showDeleteConfirm = false 
                deletePasswordInput = ""
            },
            sheetState = sheetState,
            containerColor = KaspaSurface,
            scrimColor = Color.Black.copy(alpha = 0.65f),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(KaspaCardBorder)
                )
            },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(KaspaError.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = KaspaError, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Delete Wallet", color = KaspaTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Remove from device", color = KaspaTextSecondary, fontSize = 12.sp)
                        }
                    }
                    IconButton(
                        onClick = { 
                            showDeleteConfirm = false 
                            deletePasswordInput = ""
                        }, 
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = KaspaTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Are you sure you want to remove '${state.activeWallet?.name}'? Make sure you have backed up your recovery seed phrase.\n\nPlease authenticate to confirm wallet deletion.",
                    color = KaspaTextPrimary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = deletePasswordInput,
                    onValueChange = { deletePasswordInput = it },
                    label = { Text("Wallet Password") },
                    placeholder = { Text("Enter password to confirm") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (isDeletePasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isDeletePasswordVisible = !isDeletePasswordVisible }) {
                            Icon(
                                imageVector = if (isDeletePasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle password visibility",
                                tint = KaspaTextSecondary
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = KaspaTextPrimary,
                        unfocusedTextColor = KaspaTextPrimary,
                        focusedContainerColor = KaspaSurfaceVariant,
                        unfocusedContainerColor = KaspaSurfaceVariant
                    )
                )

                if (isBiometricAvailable) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (fragmentActivity != null) {
                                BiometricAuthManager.promptBiometric(
                                    activity = fragmentActivity,
                                    title = "Confirm Wallet Deletion",
                                    subtitle = "Authenticate to delete the wallet securely",
                                    onSuccess = {
                                        viewModel.deleteCurrentWallet(context)
                                        showDeleteConfirm = false
                                        deletePasswordInput = ""
                                        Toast.makeText(context, "Wallet deleted", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, "Authentication failed: $err", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = KaspaSurfaceVariant, contentColor = KaspaPrimaryGlow),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Confirm with Biometrics", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Button(
                    onClick = {
                        val walletId = state.activeWallet?.id ?: ""
                        if (viewModel.verifyWalletPassword(context, walletId, deletePasswordInput)) {
                            viewModel.deleteCurrentWallet(context)
                            showDeleteConfirm = false
                            deletePasswordInput = ""
                            Toast.makeText(context, "Wallet deleted", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Incorrect password", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = deletePasswordInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KaspaError, contentColor = Color.White)
                ) {
                    Text("Delete Wallet", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }

    if (showSeedPhraseAuthDialog) {
        AlertDialog(
            onDismissRequest = {
                showSeedPhraseAuthDialog = false
                seedPhrasePasswordInput = ""
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = KaspaPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlock Secret Phrase", color = KaspaTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Please enter your wallet password to reveal the secret recovery phrase.",
                        color = KaspaTextSecondary,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = seedPhrasePasswordInput,
                        onValueChange = { seedPhrasePasswordInput = it },
                        label = { Text("Wallet Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = if (isSeedPhrasePasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isSeedPhrasePasswordVisible = !isSeedPhrasePasswordVisible }) {
                                Icon(
                                    imageVector = if (isSeedPhrasePasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility",
                                    tint = KaspaTextSecondary
                                )
                            }
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val activeWallet = state.activeWallet
                        if (activeWallet != null && viewModel.verifyWalletPassword(context, activeWallet.id, seedPhrasePasswordInput.trim())) {
                            showSeedPhraseAuthDialog = false
                            seedPhrasePasswordInput = ""
                            viewModel.setShowSeedBackupDialog(true)
                        } else {
                            Toast.makeText(context, "Incorrect wallet password", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = seedPhrasePasswordInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = KaspaPrimary, contentColor = Color(0xFF003731)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Unlock & View", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSeedPhraseAuthDialog = false
                    seedPhrasePasswordInput = ""
                }) {
                    Text("Cancel", color = KaspaTextSecondary)
                }
            },
            containerColor = KaspaSurface
        )
    }
}
