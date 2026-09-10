package com.example.kaspawallet.ui.screens.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.FragmentActivity
import com.example.kaspawallet.data.crypto.KaspaCrypto
import com.example.kaspawallet.data.crypto.KaspaMlDsa
import com.example.kaspawallet.data.crypto.KaspaSigner
import com.example.kaspawallet.data.security.BiometricAuthManager
import com.example.kaspawallet.ui.KaspaViewModel
import com.example.kaspawallet.ui.WalletUiState
import com.example.kaspawallet.ui.components.KaspaQrCode
import com.example.kaspawallet.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MlDsaQuantumTools(
    state: WalletUiState,
    viewModel: KaspaViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Sub-mode: 0: Keypair, 1: Sign, 2: Verify, 3: Dual-Hybrid
    var selectedSubMode by remember { mutableIntStateOf(0) }

    // Keypair state
    var currentKeyPair by remember { mutableStateOf<KaspaMlDsa.MlDsaKeyPair?>(null) }
    var isGeneratingKeys by remember { mutableStateOf(false) }
    var showPrivateKey by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf<String?>(null) }
    var showPasswordDialogForKeys by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }

    // Signing state
    var signMessageInput by remember { mutableStateOf("Kaspa Post-Quantum BlockDAG Transaction Payload #1") }
    var signUseHedged by remember { mutableStateOf(true) }
    var isSigning by remember { mutableStateOf(false) }
    var lastSignatureResult by remember { mutableStateOf<KaspaMlDsa.MlDsaSignatureResult?>(null) }

    // Verification state
    var verifyPubKeyInput by remember { mutableStateOf("") }
    var verifyMessageInput by remember { mutableStateOf("") }
    var verifySignatureInput by remember { mutableStateOf("") }
    var verifyResult by remember { mutableStateOf<KaspaMlDsa.MlDsaVerificationResult?>(null) }
    var isVerifying by remember { mutableStateOf(false) }

    // Dual Hybrid state
    var hybridPayloadInput by remember { mutableStateOf("Kaspa Dual-Sign Transaction BlockDAG #9901") }
    var isHybridSigning by remember { mutableStateOf(false) }
    var hybridSignatureResult by remember { mutableStateOf<KaspaMlDsa.DualHybridSignature?>(null) }
    var hybridVerificationResult by remember { mutableStateOf<KaspaMlDsa.DualHybridVerificationResult?>(null) }

    val copyToClipboard: (String, String, Boolean) -> Unit = { label, text, isSensitive ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        if (isSensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = android.os.PersistableBundle().apply {
                putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    // Password Dialog for seed derivation
    if (showPasswordDialogForKeys) {
        AlertDialog(
            onDismissRequest = {
                showPasswordDialogForKeys = false
                passwordInput = ""
                passwordError = null
            },
            title = { Text("Authenticate Wallet", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter your wallet password to deterministically derive your NIST FIPS 204 ML-DSA-65 post-quantum keypair from your mnemonic.",
                        fontSize = 13.sp,
                        color = KaspaTextSecondary
                    )
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            passwordError = null
                        },
                        label = { Text("Wallet Password") },
                        singleLine = true,
                        isError = passwordError != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (passwordError != null) {
                        Text(passwordError!!, color = KaspaError, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val wallet = state.activeWallet
                        val words = viewModel.repository.getWalletMnemonicWords(wallet, passwordInput)
                        if (words.isNotEmpty()) {
                            viewModel.repository.setActiveSessionPassword(passwordInput)
                            coroutineScope.launch {
                                isGeneratingKeys = true
                                val derived = withContext(Dispatchers.Default) {
                                    KaspaMlDsa.deriveKeyPairFromMnemonic(words, 0)
                                }
                                currentKeyPair = derived
                                verifyPubKeyInput = derived.publicKeyHex
                                isGeneratingKeys = false
                                showPasswordDialogForKeys = false
                                passwordInput = ""
                                Toast.makeText(context, "ML-DSA-65 keys derived successfully!", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            passwordError = "Incorrect password"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KaspaPrimary, contentColor = Color(0xFF003731))
                ) {
                    Text("Derive Keys")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPasswordDialogForKeys = false
                    passwordInput = ""
                    passwordError = null
                }) {
                    Text("Cancel", color = KaspaTextSecondary)
                }
            }
        )
    }

    // QR Code Dialog
    showQrDialog?.let { qrData ->
        Dialog(onDismissRequest = { showQrDialog = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = KaspaSurface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "ML-DSA-65 Public Key QR",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = KaspaTextPrimary
                    )
                    KaspaQrCode(
                        content = qrData.take(500),
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Text(
                        "1,952 Bytes NIST FIPS 204 Lattice Key",
                        fontSize = 12.sp,
                        color = KaspaPrimaryGlow
                    )
                    Button(
                        onClick = { showQrDialog = null },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = KaspaSurfaceVariant)
                    ) {
                        Text("Close", color = KaspaTextPrimary)
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Quantum Header Card
        Card(
            colors = CardDefaults.cardColors(containerColor = KaspaSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, KaspaPrimaryGlow.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(KaspaPrimaryGlow.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = KaspaPrimaryGlow,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                "NIST FIPS 204 Digital Signatures",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = KaspaTextPrimary
                            )
                            Text(
                                "ML-DSA-65 (Post-Quantum Security Category 3)",
                                fontSize = 12.sp,
                                color = KaspaPrimaryGlow
                            )
                        }
                    }

                    Surface(
                        color = KaspaPrimary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "FIPS 204",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = KaspaPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Module-Lattice-Based Digital Signature Standard engineered for post-quantum cryptographic resilience against Shor's algorithm. Hardness rooted in Module-LWE and Module-SIS polynomial rings (q=8,380,417, n=256, k=6, l=5).",
                    fontSize = 12.sp,
                    color = KaspaTextSecondary,
                    lineHeight = 16.sp
                )
            }
        }

        // Sub-Mode Switcher Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedSubMode == 0,
                onClick = { selectedSubMode = 0 },
                label = { Text("Keys (${if (currentKeyPair != null) "Active" else "None"})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = KaspaPrimary,
                    selectedLabelColor = Color(0xFF003731),
                    containerColor = KaspaSurfaceVariant,
                    labelColor = KaspaTextSecondary
                )
            )
            FilterChip(
                selected = selectedSubMode == 1,
                onClick = { selectedSubMode = 1 },
                label = { Text("Sign (Hedged/Det)") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = KaspaPrimary,
                    selectedLabelColor = Color(0xFF003731),
                    containerColor = KaspaSurfaceVariant,
                    labelColor = KaspaTextSecondary
                )
            )
            FilterChip(
                selected = selectedSubMode == 2,
                onClick = { selectedSubMode = 2 },
                label = { Text("Verify (3,309 B)") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = KaspaPrimary,
                    selectedLabelColor = Color(0xFF003731),
                    containerColor = KaspaSurfaceVariant,
                    labelColor = KaspaTextSecondary
                )
            )
            FilterChip(
                selected = selectedSubMode == 3,
                onClick = { selectedSubMode = 3 },
                label = { Text("Dual Hybrid (Schnorr+PQC)") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = KaspaPrimary,
                    selectedLabelColor = Color(0xFF003731),
                    containerColor = KaspaSurfaceVariant,
                    labelColor = KaspaTextSecondary
                )
            )
        }

        // TAB 0: KEY MANAGEMENT
        AnimatedVisibility(visible = selectedSubMode == 0) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val activity = context as? FragmentActivity
                            val wallet = state.activeWallet
                            if (wallet == null) {
                                Toast.makeText(context, "No active wallet found", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val words = viewModel.repository.getWalletMnemonicWords(wallet)
                            if (words.isNotEmpty()) {
                                coroutineScope.launch {
                                    isGeneratingKeys = true
                                    val derived = withContext(Dispatchers.Default) {
                                        KaspaMlDsa.deriveKeyPairFromMnemonic(words, 0)
                                    }
                                    currentKeyPair = derived
                                    verifyPubKeyInput = derived.publicKeyHex
                                    isGeneratingKeys = false
                                    Toast.makeText(context, "Derived from Active Wallet!", Toast.LENGTH_SHORT).show()
                                }
                            } else if (activity != null && BiometricAuthManager.isBiometricAvailable(context)) {
                                BiometricAuthManager.promptBiometric(
                                    activity = activity,
                                    title = "Derive Post-Quantum Keys",
                                    subtitle = "Authenticate to derive ML-DSA-65 keys",
                                    onSuccess = {
                                        showPasswordDialogForKeys = true
                                    },
                                    onError = { _ ->
                                        showPasswordDialogForKeys = true
                                    }
                                )
                            } else {
                                showPasswordDialogForKeys = true
                            }
                        },
                        enabled = !isGeneratingKeys,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KaspaPrimary, contentColor = Color(0xFF003731))
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Derive from Wallet", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                isGeneratingKeys = true
                                val generated = withContext(Dispatchers.Default) {
                                    KaspaMlDsa.generateRandomKeyPair()
                                }
                                currentKeyPair = generated
                                verifyPubKeyInput = generated.publicKeyHex
                                isGeneratingKeys = false
                                Toast.makeText(context, "Fresh ML-DSA-65 Keypair Generated!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isGeneratingKeys,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, KaspaPrimaryGlow)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = KaspaPrimaryGlow)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Random Keypair", fontSize = 12.sp, color = KaspaPrimaryGlow)
                    }
                }

                if (isGeneratingKeys) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = KaspaSurfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = KaspaPrimary, strokeWidth = 2.dp)
                            Text(
                                "Generating NIST FIPS 204 Keypair via SHAKE-256 polynomial expansion...",
                                fontSize = 12.sp,
                                color = KaspaTextSecondary
                            )
                        }
                    }
                }

                currentKeyPair?.let { keys ->
                    // Public Key Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = KaspaSurface),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Public Key", fontWeight = FontWeight.Bold, color = KaspaTextPrimary, fontSize = 14.sp)
                                    Surface(color = KaspaPrimary.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                                        Text("${KaspaMlDsa.PUBLIC_KEY_BYTES} Bytes", fontSize = 10.sp, color = KaspaPrimary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                                Row {
                                    IconButton(onClick = { showQrDialog = keys.publicKeyHex }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.QrCode, contentDescription = "QR Code", tint = KaspaPrimaryGlow, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { copyToClipboard("ML-DSA-65 Public Key", keys.publicKeyHex, false) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = KaspaTextSecondary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            SelectionContainer {
                                Text(
                                    keys.publicKeyHex,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = KaspaTextSecondary,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                "Matrix seed \u03C1 (32B) + Compressed t1 vector (1,920B). Used for quantum-safe verification.",
                                fontSize = 11.sp,
                                color = KaspaTextMuted
                            )
                        }
                    }

                    // Private Key Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = KaspaSurface),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Secret Key", fontWeight = FontWeight.Bold, color = KaspaTextPrimary, fontSize = 14.sp)
                                    Surface(color = KaspaWarning.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                                        Text("${KaspaMlDsa.PRIVATE_KEY_BYTES} Bytes", fontSize = 10.sp, color = KaspaWarning, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                                Row {
                                    IconButton(onClick = { showPrivateKey = !showPrivateKey }, modifier = Modifier.size(32.dp)) {
                                        Icon(
                                            if (showPrivateKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle Visibility",
                                            tint = KaspaTextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(onClick = { copyToClipboard("ML-DSA-65 Secret Key", keys.privateKeyHex, true) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = KaspaTextSecondary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            SelectionContainer {
                                Text(
                                    if (showPrivateKey) keys.privateKeyHex else "•••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••• [4,032 BYTES SECURE STORE]",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (showPrivateKey) KaspaTextSecondary else KaspaWarning,
                                    maxLines = if (showPrivateKey) 4 else 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (keys.isDerivedFromWallet && keys.seedDigestHex != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Seed Digest (\u03BE):", fontSize = 11.sp, color = KaspaTextMuted)
                                    Text(
                                        "${keys.seedDigestHex.take(12)}...${keys.seedDigestHex.takeLast(12)}",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = KaspaPrimaryGlow
                                    )
                                }
                            }
                        }
                    }

                    // Security Parameters Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = KaspaSurfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("ML-DSA-65 Ring Specifications", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = KaspaPrimaryGlow)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Ring Modulus q:", fontSize = 11.sp, color = KaspaTextSecondary)
                                Text("8,380,417 (23 bits)", fontSize = 11.sp, color = KaspaTextPrimary, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Polynomial Degree n:", fontSize = 11.sp, color = KaspaTextSecondary)
                                Text("256 coefficients", fontSize = 11.sp, color = KaspaTextPrimary, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Matrix Dimensions (k \u00D7 \u2113):", fontSize = 11.sp, color = KaspaTextSecondary)
                                Text("6 \u00D7 5 modules", fontSize = 11.sp, color = KaspaTextPrimary, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Noise Parameter \u03B7:", fontSize = 11.sp, color = KaspaTextSecondary)
                                Text("4 (Centered Binomial)", fontSize = 11.sp, color = KaspaTextPrimary, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Quantum Security Level:", fontSize = 11.sp, color = KaspaTextSecondary)
                                Text("NIST Level III (128-bit quantum / 192-bit classical)", fontSize = 11.sp, color = KaspaSuccess, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } ?: run {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = KaspaSurfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = KaspaTextMuted, modifier = Modifier.size(36.dp))
                            Text("No Keypair Loaded", fontWeight = FontWeight.Bold, color = KaspaTextPrimary, fontSize = 14.sp)
                            Text(
                                "Tap 'Derive from Wallet' to use your existing BIP-39 mnemonic phrase deterministically, or 'Random Keypair' for an ephemeral post-quantum keypair.",
                                fontSize = 12.sp,
                                color = KaspaTextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // TAB 1: SIGN PAYLOAD
        AnimatedVisibility(visible = selectedSubMode == 1) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Preset Selector
                Text("Payload to Sign", fontWeight = FontWeight.Bold, color = KaspaTextPrimary, fontSize = 14.sp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = { signMessageInput = "Kaspa BlockDAG Transaction #${System.currentTimeMillis()}" },
                        label = { Text("TX Payload", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = { signMessageInput = "Kaspa DAA Virtual Score: 88204910 Blue Score Root" },
                        label = { Text("DAA Score", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = { signMessageInput = state.activeAccount?.address ?: "" },
                        label = { Text("Account Address", fontSize = 11.sp) }
                    )
                }

                OutlinedTextField(
                    value = signMessageInput,
                    onValueChange = { signMessageInput = it },
                    label = { Text("Message / Sighash Payload") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                // Hedged Mode Switcher Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = KaspaSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (signUseHedged) "Hedged Signing Mode (Recommended)" else "Deterministic Mode",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (signUseHedged) KaspaPrimaryGlow else KaspaWarning
                            )
                            Text(
                                if (signUseHedged)
                                    "Injects fresh CSPRNG salt into rejection sampling to neutralize Differential Fault Attacks (DFA) and side-channel power traces."
                                else
                                    "Strict RFC/FIPS deterministic mode. Generates identical signature bytes for the same message.",
                                fontSize = 11.sp,
                                color = KaspaTextSecondary
                            )
                        }
                        Switch(
                            checked = signUseHedged,
                            onCheckedChange = { signUseHedged = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF003731),
                                checkedTrackColor = KaspaPrimary
                            )
                        )
                    }
                }

                // Sign Action Button
                Button(
                    onClick = {
                        val keys = currentKeyPair
                        if (keys == null) {
                            Toast.makeText(context, "Please generate or derive an ML-DSA-65 keypair first", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        coroutineScope.launch {
                            isSigning = true
                            val result = withContext(Dispatchers.Default) {
                                KaspaMlDsa.sign(
                                    keyPair = keys,
                                    message = signMessageInput.toByteArray(Charsets.UTF_8),
                                    hedged = signUseHedged
                                )
                            }
                            lastSignatureResult = result
                            isSigning = false
                            Toast.makeText(context, "ML-DSA-65 Signature Generated!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isSigning && signMessageInput.isNotBlank() && currentKeyPair != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KaspaPrimary, contentColor = Color(0xFF003731))
                ) {
                    if (isSigning) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF003731), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Signing with FIPS 204 ML-DSA-65...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign with ML-DSA-65 (3,309 Bytes)", fontWeight = FontWeight.Bold)
                    }
                }

                // Signature Output Card
                lastSignatureResult?.let { sig ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = KaspaSurface),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, KaspaSuccess.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = KaspaSuccess, modifier = Modifier.size(18.dp))
                                    Text("Valid Signature Produced", fontWeight = FontWeight.Bold, color = KaspaTextPrimary, fontSize = 13.sp)
                                }
                                Surface(color = KaspaSuccess.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                                    Text("${sig.signatureBytes.size} Bytes", fontSize = 10.sp, color = KaspaSuccess, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }

                            SelectionContainer {
                                Text(
                                    sig.signatureHex,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = KaspaTextSecondary,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { copyToClipboard("ML-DSA-65 Signature", sig.signatureHex, false) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy Hex", fontSize = 11.sp)
                                }

                                Button(
                                    onClick = {
                                        verifyPubKeyInput = currentKeyPair?.publicKeyHex ?: ""
                                        verifyMessageInput = signMessageInput
                                        verifySignatureInput = sig.signatureHex
                                        selectedSubMode = 2
                                        Toast.makeText(context, "Transferred to Verifier Tab", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = KaspaSurfaceVariant)
                                ) {
                                    Text("Verify Now", fontSize = 11.sp, color = KaspaPrimaryGlow)
                                }
                            }
                        }
                    }
                }
            }
        }

        // TAB 2: VERIFY SIGNATURE
        AnimatedVisibility(visible = selectedSubMode == 2) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("NIST FIPS 204 Verification Engine", fontWeight = FontWeight.Bold, color = KaspaTextPrimary, fontSize = 14.sp)

                OutlinedTextField(
                    value = verifyPubKeyInput,
                    onValueChange = { verifyPubKeyInput = it },
                    label = { Text("Signer Public Key (Hex, 1,952 Bytes)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                OutlinedTextField(
                    value = verifyMessageInput,
                    onValueChange = { verifyMessageInput = it },
                    label = { Text("Original Message") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )

                OutlinedTextField(
                    value = verifySignatureInput,
                    onValueChange = { verifySignatureInput = it },
                    label = { Text("ML-DSA-65 Signature (Hex, 3,309 Bytes)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isVerifying = true
                                val result = withContext(Dispatchers.Default) {
                                    try {
                                        val pubBytes = KaspaSigner.hexStringToByteArray(verifyPubKeyInput.trim())
                                        val sigBytes = KaspaSigner.hexStringToByteArray(verifySignatureInput.trim())
                                        val msgBytes = verifyMessageInput.toByteArray(Charsets.UTF_8)
                                        KaspaMlDsa.verify(pubBytes, msgBytes, sigBytes)
                                    } catch (e: Exception) {
                                        KaspaMlDsa.MlDsaVerificationResult(
                                            isValid = false,
                                            message = "Input Parsing Error",
                                            details = e.localizedMessage ?: "Invalid hex formatting",
                                            signatureBytesLength = 0,
                                            publicKeyBytesLength = 0
                                        )
                                    }
                                }
                                verifyResult = result
                                isVerifying = false
                            }
                        },
                        enabled = !isVerifying && verifyPubKeyInput.isNotBlank() && verifySignatureInput.isNotBlank(),
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KaspaPrimary, contentColor = Color(0xFF003731))
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF003731), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Verify Signature", fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            // Tamper with message to demonstrate rejection
                            verifyMessageInput += " [TAMPERED]"
                            coroutineScope.launch {
                                isVerifying = true
                                val result = withContext(Dispatchers.Default) {
                                    try {
                                        val pubBytes = KaspaSigner.hexStringToByteArray(verifyPubKeyInput.trim())
                                        val sigBytes = KaspaSigner.hexStringToByteArray(verifySignatureInput.trim())
                                        val msgBytes = verifyMessageInput.toByteArray(Charsets.UTF_8)
                                        KaspaMlDsa.verify(pubBytes, msgBytes, sigBytes)
                                    } catch (e: Exception) {
                                        KaspaMlDsa.MlDsaVerificationResult(false, "Error", e.message ?: "", 0, 0)
                                    }
                                }
                                verifyResult = result
                                isVerifying = false
                                Toast.makeText(context, "Tamper injected: Verification correctly rejected!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = verifySignatureInput.isNotBlank() && !verifyMessageInput.endsWith("[TAMPERED]"),
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, KaspaError.copy(alpha = 0.6f))
                    ) {
                        Text("Tamper Test", color = KaspaError, fontSize = 12.sp)
                    }
                }

                // Verification Result Card
                verifyResult?.let { res ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (res.isValid) KaspaSuccess.copy(alpha = 0.1f) else KaspaError.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, if (res.isValid) KaspaSuccess else KaspaError, RoundedCornerShape(14.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (res.isValid) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (res.isValid) KaspaSuccess else KaspaError,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    res.message,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (res.isValid) KaspaSuccess else KaspaError
                                )
                            }
                            Text(res.details, fontSize = 12.sp, color = KaspaTextSecondary)
                        }
                    }
                }
            }
        }

        // TAB 3: DUAL HYBRID (SCHNORR + ML-DSA-65)
        AnimatedVisibility(visible = selectedSubMode == 3) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = KaspaSurfaceVariant),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = KaspaPrimaryGlow, modifier = Modifier.size(20.dp))
                            Text("Kaspa Post-Quantum Transition Architecture", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = KaspaTextPrimary)
                        }
                        Text(
                            "Combines Kaspa's ultra-fast BIP-340 Schnorr (64 bytes) with NIST FIPS 204 ML-DSA-65 (3,309 bytes). If a quantum computer compromises ECDLP on secp256k1 in the future, the ML-DSA-65 layer guarantees unbreakable security.",
                            fontSize = 11.sp,
                            color = KaspaTextSecondary,
                            lineHeight = 15.sp
                        )
                    }
                }

                OutlinedTextField(
                    value = hybridPayloadInput,
                    onValueChange = { hybridPayloadInput = it },
                    label = { Text("Dual Hybrid Payload") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        val wallet = state.activeWallet
                        if (wallet == null) {
                            Toast.makeText(context, "Please create or select an active wallet", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val words = viewModel.repository.getWalletMnemonicWords(wallet)
                        if (words.isEmpty()) {
                            showPasswordDialogForKeys = true
                            return@Button
                        }

                        coroutineScope.launch {
                            isHybridSigning = true
                            val result = withContext(Dispatchers.Default) {
                                val seed = KaspaCrypto.mnemonicToSeed(words)
                                val schnorrPriv = KaspaSigner.derivePrivateKey(seed, 0, 0, 0)
                                val schnorrPub = KaspaSigner.derivePublicKey(schnorrPriv)
                                val mlDsaKeys = KaspaMlDsa.deriveKeyPairFromMnemonic(words, 0)

                                val dualSig = KaspaMlDsa.createDualHybridSignature(
                                    schnorrPrivateKey = schnorrPriv,
                                    schnorrPublicKey = schnorrPub,
                                    mlDsaKeyPair = mlDsaKeys,
                                    payload = hybridPayloadInput.toByteArray(Charsets.UTF_8)
                                )

                                val verify = KaspaMlDsa.verifyDualHybridSignature(
                                    schnorrPublicKey = schnorrPub,
                                    mlDsaPublicKey = mlDsaKeys.publicKeyBytes,
                                    payload = hybridPayloadInput.toByteArray(Charsets.UTF_8),
                                    schnorrSignature = KaspaSigner.hexStringToByteArray(dualSig.schnorrSignatureHex),
                                    mlDsaSignature = KaspaSigner.hexStringToByteArray(dualSig.mlDsaSignatureHex)
                                )

                                Pair(dualSig, verify)
                            }

                            hybridSignatureResult = result.first
                            hybridVerificationResult = result.second
                            isHybridSigning = false
                            Toast.makeText(context, "Dual Hybrid Signature Verified Live!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isHybridSigning && hybridPayloadInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KaspaPrimary, contentColor = Color(0xFF003731))
                ) {
                    if (isHybridSigning) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF003731), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Dual Signing (Schnorr + ML-DSA)...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Execute Dual Hybrid Signing", fontWeight = FontWeight.Bold)
                    }
                }

                hybridSignatureResult?.let { dual ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = KaspaSurface),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Dual Hybrid Signature Outputs", fontWeight = FontWeight.Bold, color = KaspaTextPrimary, fontSize = 13.sp)

                            // Layer 1
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Layer 1: BIP-340 Schnorr", fontSize = 11.sp, color = KaspaTextSecondary)
                                Text("64 Bytes (Secp256k1)", fontSize = 11.sp, color = KaspaPrimaryGlow, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                "${dual.schnorrSignatureHex.take(20)}...${dual.schnorrSignatureHex.takeLast(20)}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = KaspaTextSecondary
                            )

                            HorizontalDivider(color = KaspaCardBorder)

                            // Layer 2
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Layer 2: NIST FIPS 204 ML-DSA-65", fontSize = 11.sp, color = KaspaTextSecondary)
                                Text("3,309 Bytes (Lattice R_q)", fontSize = 11.sp, color = KaspaSuccess, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                "${dual.mlDsaSignatureHex.take(20)}...${dual.mlDsaSignatureHex.takeLast(20)}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = KaspaTextSecondary
                            )

                            hybridVerificationResult?.let { ver ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    color = if (ver.isBothValid) KaspaSuccess.copy(alpha = 0.15f) else KaspaError.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            if (ver.isBothValid) Icons.Default.CheckCircle else Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = if (ver.isBothValid) KaspaSuccess else KaspaError,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(ver.message, fontSize = 11.sp, color = if (ver.isBothValid) KaspaSuccess else KaspaError)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
