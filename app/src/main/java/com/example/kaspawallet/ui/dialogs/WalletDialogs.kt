package com.example.kaspawallet.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.example.kaspawallet.ui.theme.noRippleClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import com.example.kaspawallet.data.crypto.KaspaCrypto
import com.example.kaspawallet.data.security.BiometricAuthManager
import com.example.kaspawallet.data.crypto.KaspaSigner
import com.example.kaspawallet.data.crypto.KaspaUtils
import com.example.kaspawallet.data.model.AccountEntity
import com.example.kaspawallet.data.model.KaspaNetwork
import com.example.kaspawallet.data.model.WalletEntity
import com.example.kaspawallet.data.model.ContactEntity
import com.example.kaspawallet.data.model.TransactionEntity
import com.example.kaspawallet.data.model.UtxoEntry
import com.example.kaspawallet.ui.KaspaViewModel
import com.example.kaspawallet.ui.components.KaspaQrCode
import com.example.kaspawallet.ui.components.RealQrCodeScannerDialog
import com.example.kaspawallet.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendKasDialog(
    viewModel: KaspaViewModel,
    activeAccount: AccountEntity?,
    contacts: List<ContactEntity>,
    utxos: List<UtxoEntry>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.showTxSuccessDialog) {
        if (state.showTxSuccessDialog) {
            onDismiss()
        }
    }

    var recipientAddress by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var selectedFeeOption by remember { mutableStateOf("Normal") }
    var showContactPicker by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var showAuthPasswordDialog by remember { mutableStateOf(false) }
    var authInput by remember { mutableStateOf("") }
    var isAuthInputVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state.errorMessage) {
        if (state.errorMessage != null) {
            showAuthPasswordDialog = false
        }
    }

    var selectedUtxoKeys by remember(utxos) {
        mutableStateOf(utxos.map { "${it.outpointTxId}:${it.outpointIndex}" }.toSet())
    }

    val selectedUtxosList = utxos.filter { "${it.outpointTxId}:${it.outpointIndex}" in selectedUtxoKeys }
    val selectedUtxosSumSompi = selectedUtxosList.sumOf { it.amountSompi }
    val selectedUtxosSumKas = KaspaUtils.sompiToKas(selectedUtxosSumSompi)

    val availableKas = if (utxos.isNotEmpty()) {
        selectedUtxosSumKas
    } else {
        activeAccount?.let { KaspaUtils.sompiToKas(it.balanceSompi) } ?: 0.0
    }
    val amountKas = amountText.toDoubleOrNull() ?: 0.0
    val feeKas = when (selectedFeeOption) {
        "Low" -> KaspaUtils.sompiToKas(KaspaUtils.DEFAULT_MIN_FEE_SOMPI)
        "Priority" -> KaspaUtils.sompiToKas(KaspaUtils.HIGH_PRIORITY_FEE_SOMPI)
        else -> KaspaUtils.sompiToKas(KaspaUtils.PRIORITY_FEE_SOMPI)
    }
    val totalDebitKas = amountKas + feeKas
    val isAddressValid = KaspaUtils.isValidKaspaAddress(recipientAddress)
    val isAmountValid = amountKas > 0 && totalDebitKas <= availableKas

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Send Kaspa", color = KaspaTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("From: ${activeAccount?.name ?: "Account"}", color = KaspaTextSecondary, fontSize = 12.sp)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = KaspaTextPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = KaspaSurface)
                )
            },
            containerColor = KaspaBackground
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Error Banner
                if (state.errorMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = KaspaError.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, KaspaError.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = KaspaError,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = state.errorMessage ?: "Transaction Error",
                                color = KaspaError,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearErrorMessage() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = KaspaError,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                // UTXO Coin Control Box
                Card(
                    colors = CardDefaults.cardColors(containerColor = KaspaSurface),
                    shape = RoundedCornerShape(14.dp),
                    
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("UTXO Coin Control", color = KaspaTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "Selected: ${KaspaUtils.formatKas(availableKas)} (${selectedUtxosList.size}/${utxos.size} inputs)",
                                    color = KaspaPrimaryGlow,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            
                            var isExpanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.noRippleClickable { isExpanded = !isExpanded }) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Toggle UTXOs",
                                    tint = KaspaPrimary
                                )
                            }
                        }

                        if (utxos.isEmpty()) {
                            Text(
                                "No UTXOs found or loading...",
                                color = KaspaTextMuted,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            var isExpanded by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .noRippleClickable {
                                            selectedUtxoKeys = utxos.map { "${it.outpointTxId}:${it.outpointIndex}" }.toSet()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Select All", color = KaspaPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Box(
                                    modifier = Modifier
                                        .noRippleClickable {
                                            selectedUtxoKeys = emptySet()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Deselect All", color = KaspaError, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            AnimatedVisibility(visible = isExpanded || true) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 140.dp)
                                        .verticalScroll(rememberScrollState())
                                        .background(KaspaSurfaceVariant)
                                        .padding(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    utxos.forEach { utxo ->
                                        val key = "${utxo.outpointTxId}:${utxo.outpointIndex}"
                                        val isChecked = key in selectedUtxoKeys
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .noRippleClickable {
                                                    selectedUtxoKeys = if (isChecked) {
                                                        selectedUtxoKeys - key
                                                    } else {
                                                        selectedUtxoKeys + key
                                                    }
                                                }
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Checkbox(
                                                    checked = isChecked,
                                                    onCheckedChange = null,
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = KaspaPrimary,
                                                        uncheckedColor = KaspaCardBorder,
                                                        checkmarkColor = Color(0xFF003731)
                                                    ),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        text = "Tx: ${KaspaUtils.truncateAddress(utxo.outpointTxId, 10, 8)} #${utxo.outpointIndex}",
                                                        color = KaspaTextPrimary,
                                                        fontSize = 11.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                    Text(
                                                        text = "Score: ${utxo.blockDaaScore}",
                                                        color = KaspaTextMuted,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                            Text(
                                                text = KaspaUtils.formatSompi(utxo.amountSompi),
                                                color = KaspaPrimaryGlow,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Recipient Address
                OutlinedTextField(
                    value = recipientAddress,
                    onValueChange = { recipientAddress = it.trim() },
                    label = { Text("Recipient Kaspa Address") },
                    placeholder = { Text("kaspa:qq...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = KaspaTextPrimary,
                        unfocusedTextColor = KaspaTextPrimary,
                        focusedContainerColor = KaspaSurfaceVariant,
                        unfocusedContainerColor = KaspaSurfaceVariant
                    ),
                    trailingIcon = {
                        Row {
                            IconButton(onClick = { showQrScanner = true }) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR", tint = KaspaPrimary)
                            }
                            if (contacts.isNotEmpty()) {
                                IconButton(onClick = { showContactPicker = true }) {
                                    Icon(Icons.Outlined.Contacts, contentDescription = "Pick contact", tint = KaspaPrimary)
                                }
                            }
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                                if (!clip.isNullOrBlank()) {
                                    recipientAddress = clip.trim()
                                }
                            }) {
                                Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste", tint = KaspaPrimary)
                            }
                        }
                    }
                )

                if (recipientAddress.isNotEmpty() && !isAddressValid) {
                    Text(
                        "Invalid Kaspa address format (e.g. kaspa:qq...)",
                        color = KaspaError,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                // Amount
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (KAS)") },
                    placeholder = { Text("0.0") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = KaspaTextPrimary,
                        unfocusedTextColor = KaspaTextPrimary,
                        focusedContainerColor = KaspaSurfaceVariant,
                        unfocusedContainerColor = KaspaSurfaceVariant
                    ),
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                val maxSend = (availableKas - feeKas).coerceAtLeast(0.0)
                                amountText = KaspaUtils.formatKasOnly(maxSend)
                            }
                        ) {
                            Text("MAX", color = KaspaPrimaryGlow, fontWeight = FontWeight.Bold)
                        }
                    }
                )

                if (amountKas > 0) {
                    Text(
                        "= ${(amountKas * KaspaUtils.SOMPI_PER_KAS).toLong()} Sompi",
                        color = KaspaTextMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                // Fee Priority
                Column {
                    Text("Transaction Priority Fee", color = KaspaTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Low" to "0.00386 KAS", "Normal" to "0.00400 KAS", "Priority" to "0.00486 KAS").forEach { (tier, feeStr) ->
                            val isSelected = selectedFeeOption == tier
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .noRippleClickable { selectedFeeOption = tier },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) KaspaPrimary.copy(alpha = 0.2f) else KaspaSurfaceVariant
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(tier, color = if (isSelected) KaspaPrimaryGlow else KaspaTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(feeStr, color = KaspaTextSecondary, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // Note (Optional)
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Note / Memo (Optional)") },
                    placeholder = { Text("Payment reference...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = KaspaTextPrimary,
                        unfocusedTextColor = KaspaTextPrimary,
                        focusedContainerColor = KaspaSurfaceVariant,
                        unfocusedContainerColor = KaspaSurfaceVariant
                    )
                )

                // Summary
                Card(
                    colors = CardDefaults.cardColors(containerColor = KaspaSurfaceVariant),
                    shape = RoundedCornerShape(14.dp),
                    
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Transfer Amount:", color = KaspaTextSecondary, fontSize = 13.sp)
                            Text(KaspaUtils.formatKas(amountKas), color = KaspaTextPrimary, fontSize = 13.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Network Fee:", color = KaspaTextSecondary, fontSize = 13.sp)
                            Text(KaspaUtils.formatKas(feeKas), color = KaspaTextSecondary, fontSize = 13.sp)
                        }
                        HorizontalDivider(color = KaspaCardBorder, modifier = Modifier.padding(vertical = 4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Debit:", color = KaspaTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(KaspaUtils.formatKas(totalDebitKas), color = KaspaPrimaryGlow, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Submit Button
                Button(
                    onClick = {
                        val fragmentActivity = context as? FragmentActivity
                        if (fragmentActivity != null && BiometricAuthManager.isBiometricAvailable(context)) {
                            BiometricAuthManager.promptBiometric(
                                activity = fragmentActivity,
                                title = "Authorize KAS Transfer",
                                subtitle = "Scan fingerprint/face to authorize sending ${KaspaUtils.formatKas(amountKas)}",
                                onSuccess = {
                                    viewModel.sendKas(
                                        recipientAddress = recipientAddress,
                                        amountKas = amountKas,
                                        feeOption = selectedFeeOption,
                                        note = noteText,
                                        manualUtxos = selectedUtxosList
                                    )
                                },
                                onError = { _ ->
                                    showAuthPasswordDialog = true
                                }
                            )
                        } else {
                            showAuthPasswordDialog = true
                        }
                    },
                    enabled = isAddressValid && isAmountValid && !state.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KaspaPrimary,
                        contentColor = Color(0xFF003731),
                        disabledContainerColor = KaspaSurfaceVariant,
                        disabledContentColor = KaspaTextMuted
                    )
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color(0xFF003731),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Signing & Broadcasting...", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Broadcast Transaction", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    if (showAuthPasswordDialog) {
        AlertDialog(
            onDismissRequest = { if (!state.isLoading) showAuthPasswordDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = KaspaPrimaryGlow)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Authorize Transaction", color = KaspaTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Enter your wallet password or recovery seed phrase to authorize sending ${KaspaUtils.formatKas(amountKas)} to ${KaspaUtils.truncateAddress(recipientAddress)}.",
                        color = KaspaTextSecondary,
                        fontSize = 13.sp
                    )

                    OutlinedTextField(
                        value = authInput,
                        onValueChange = { authInput = it },
                        label = { Text("Wallet Password or Seed Words") },
                        placeholder = { Text("Enter password or 12/24 words") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = false,
                        maxLines = 3,
                        visualTransformation = if (isAuthInputVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isAuthInputVisible = !isAuthInputVisible }) {
                                Icon(
                                    imageVector = if (isAuthInputVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle visibility",
                                    tint = KaspaTextMuted
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
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val activeWallet = state.activeWallet
                        if (activeWallet == null) {
                            Toast.makeText(context, "No active wallet found", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val trimmedInput = authInput.trim()
                        val isPasswordValid = viewModel.verifyWalletPassword(context, activeWallet.id, trimmedInput)
                        val words = KaspaCrypto.decryptMnemonic(activeWallet.encryptedMnemonic, trimmedInput)
                        val isMnemonicValid = (words.size in listOf(12, 24) && words.joinToString(" ") == trimmedInput)
                        if (isPasswordValid || isMnemonicValid) {
                            viewModel.sendKas(
                                recipientAddress = recipientAddress,
                                amountKas = amountKas,
                                feeOption = selectedFeeOption,
                                note = noteText,
                                manualUtxos = selectedUtxosList
                            )
                        } else {
                            Toast.makeText(context, "Invalid wallet password or seed phrase", Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = authInput.isNotBlank() && !state.isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = KaspaPrimary, contentColor = Color(0xFF003731)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color(0xFF003731),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Signing...", fontWeight = FontWeight.Bold)
                    } else {
                        Text("Confirm & Send", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAuthPasswordDialog = false }) {
                    Text("Cancel", color = KaspaTextSecondary)
                }
            },
            containerColor = KaspaSurface
        )
    }

    if (showContactPicker) {
        AlertDialog(
            onDismissRequest = { showContactPicker = false },
            title = { Text("Select Contact", color = KaspaTextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    contacts.forEach { contact ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .noRippleClickable {
                                    recipientAddress = contact.address
                                    showContactPicker = false
                                },
                            colors = CardDefaults.cardColors(containerColor = KaspaSurfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(contact.name, color = KaspaTextPrimary, fontWeight = FontWeight.Bold)
                                Text(KaspaUtils.truncateAddress(contact.address), color = KaspaTextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showContactPicker = false }) {
                    Text("Cancel", color = KaspaPrimary)
                }
            },
            containerColor = KaspaSurface
        )
    }

    if (showQrScanner) {
        RealQrCodeScannerDialog(
            onDismissRequest = { showQrScanner = false },
            onQrCodeScanned = { scannedAddress, scannedAmount ->
                recipientAddress = scannedAddress
                if (scannedAmount != null && scannedAmount > 0.0) {
                    amountText = scannedAmount.toString()
                }
                showQrScanner = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveKasDialog(
    activeAccount: AccountEntity?,
    activeWallet: WalletEntity? = null,
    network: KaspaNetwork = KaspaNetwork.MAINNET,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedBranch by remember { mutableIntStateOf(0) } // 0: Receive (External), 1: Change (Internal)
    var addressIndex by remember { mutableIntStateOf(0) }
    var requestedAmountText by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }

    val words = remember(activeWallet) {
        activeWallet?.encryptedMnemonic?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
    }

    val displayAddress = remember(activeAccount, words, selectedBranch, addressIndex, network) {
        if (words.isNotEmpty() && activeAccount != null) {
            val seed = KaspaCrypto.mnemonicToSeed(words)
            KaspaSigner.deriveKaspaAddressFromSeed(
                seed = seed,
                accountIndex = activeAccount.accountIndex,
                branch = selectedBranch,
                addressIndex = addressIndex,
                network = network
            )
        } else {
            activeAccount?.address ?: "kaspa:address"
        }
    }

    val derivationPath = "m/44'/111111'/${activeAccount?.accountIndex ?: 0}'/$selectedBranch/$addressIndex"
    val reqAmount = requestedAmountText.toDoubleOrNull()
    val qrPayload = if (reqAmount != null && reqAmount > 0) {
        "$displayAddress?amount=$reqAmount"
    } else {
        displayAddress
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
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
                            .background(KaspaPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = null, tint = KaspaPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Receive Kaspa", color = KaspaTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("${activeAccount?.name ?: "Account #0"} • ${network.displayName}", color = KaspaTextSecondary, fontSize = 12.sp)
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = KaspaTextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Centered QR Code Box
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                KaspaQrCode(
                    content = qrPayload,
                    size = 150.dp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Deposit Address Box
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .noRippleClickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Kaspa Address", displayAddress))
                        Toast.makeText(context, "Address copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                colors = CardDefaults.cardColors(containerColor = KaspaSurfaceVariant),
                shape = RoundedCornerShape(14.dp),
                
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Kaspa Deposit Address", color = KaspaTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Text(derivationPath, color = KaspaTextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        displayAddress,
                        color = KaspaTextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Button: Copy Address
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Kaspa Address", displayAddress))
                    Toast.makeText(context, "Address copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KaspaPrimary, contentColor = Color(0xFF003731))
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copy Address", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Advanced Derivation Toggle Button
            TextButton(
                onClick = { showAdvanced = !showAdvanced },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (showAdvanced) "Hide Derivation Options" else "Advanced Derivation Options",
                        color = KaspaTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = KaspaTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Advanced Options Section
            AnimatedVisibility(visible = showAdvanced) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Branch Selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(KaspaSurfaceVariant)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(0 to "Receive (External)", 1 to "Change (Internal)").forEach { (branch, label) ->
                            val isSelected = selectedBranch == branch
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) KaspaPrimary else Color.Transparent)
                                    .noRippleClickable { selectedBranch = branch }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    color = if (isSelected) Color(0xFF003731) else KaspaTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Index Stepper with 30 Limit (0..29)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(KaspaSurfaceVariant, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Address Index: #$addressIndex (Limit: 30 addresses)",
                                color = KaspaTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { if (addressIndex > 0) addressIndex-- },
                                    enabled = addressIndex > 0,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Prev Index", tint = if (addressIndex > 0) KaspaPrimary else KaspaTextMuted, modifier = Modifier.size(16.dp))
                                }
                                Text(
                                    "$addressIndex",
                                    color = KaspaPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                IconButton(
                                    onClick = { if (addressIndex < 29) addressIndex++ },
                                    enabled = addressIndex < 29,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Next Index", tint = if (addressIndex < 29) KaspaPrimary else KaspaTextMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        // Quick Index Chips (0, 1, 2, 5, 10, 20, 29)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(0, 1, 2, 5, 10, 20, 29).forEach { idx ->
                                val isSelected = addressIndex == idx
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) KaspaPrimary else KaspaSurface)
                                        .clickable { addressIndex = idx }
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "#$idx",
                                        color = if (isSelected) Color(0xFF003731) else KaspaTextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    // Request Amount Optional
                    if (selectedBranch == 0) {
                        OutlinedTextField(
                            value = requestedAmountText,
                            onValueChange = { requestedAmountText = it },
                            label = { Text("Request Specific Amount (KAS)", fontSize = 12.sp) },
                            placeholder = { Text("Optional", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = KaspaTextPrimary,
                                unfocusedTextColor = KaspaTextPrimary,
                                focusedContainerColor = KaspaSurfaceVariant,
                                unfocusedContainerColor = KaspaSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferDialog(
    viewModel: KaspaViewModel,
    accounts: List<AccountEntity>,
    activeAccount: AccountEntity?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val otherAccounts = accounts.filter { it.id != activeAccount?.id }
    var selectedTarget by remember { mutableStateOf(otherAccounts.firstOrNull()) }
    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }

    val availableKas = activeAccount?.let { KaspaUtils.sompiToKas(it.balanceSompi) } ?: 0.0
    val amountKas = amountText.toDoubleOrNull() ?: 0.0
    val feeKas = KaspaUtils.sompiToKas(KaspaUtils.DEFAULT_MIN_FEE_SOMPI)
    val isValid = selectedTarget != null && amountKas > 0 && (amountKas + feeKas) <= availableKas
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp)
        ) {
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
                            .background(KaspaPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.SyncAlt, contentDescription = null, tint = KaspaPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Internal Transfer", color = KaspaTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Between accounts in this wallet", color = KaspaTextSecondary, fontSize = 12.sp)
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = KaspaTextSecondary)
                }
            }

            // Error Banner
            if (state.errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = KaspaError.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, KaspaError.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = KaspaError,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state.errorMessage ?: "Transfer Error",
                            color = KaspaError,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearErrorMessage() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = KaspaError,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Source account summary
            Card(
                colors = CardDefaults.cardColors(containerColor = KaspaSurfaceVariant),
                shape = RoundedCornerShape(12.dp),
                
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("From: ${activeAccount?.name}", color = KaspaTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Available Balance", color = KaspaTextMuted, fontSize = 11.sp)
                    }
                    Text(KaspaUtils.formatKas(availableKas), color = KaspaPrimaryGlow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Select Destination Account:", color = KaspaTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                otherAccounts.forEach { acc ->
                    val isSelected = selectedTarget?.id == acc.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .noRippleClickable { selectedTarget = acc },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) KaspaPrimary.copy(alpha = 0.15f) else KaspaSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(acc.name, color = KaspaTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(KaspaUtils.truncateAddress(acc.address), color = KaspaTextSecondary, fontSize = 11.sp)
                            }
                            Text(KaspaUtils.formatSompi(acc.balanceSompi), color = KaspaTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Transfer Amount (KAS)") },
                placeholder = { Text("0.0") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = KaspaTextPrimary,
                    unfocusedTextColor = KaspaTextPrimary,
                    focusedContainerColor = KaspaSurfaceVariant,
                    unfocusedContainerColor = KaspaSurfaceVariant
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("Note / Memo (Optional)") },
                placeholder = { Text("Reason for transfer") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = KaspaTextPrimary,
                    unfocusedTextColor = KaspaTextPrimary,
                    focusedContainerColor = KaspaSurfaceVariant,
                    unfocusedContainerColor = KaspaSurfaceVariant
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    selectedTarget?.let { target ->
                        val fragmentActivity = context as? FragmentActivity
                        if (fragmentActivity != null && BiometricAuthManager.isBiometricAvailable(context)) {
                            BiometricAuthManager.promptBiometric(
                                activity = fragmentActivity,
                                title = "Authorize Internal Transfer",
                                subtitle = "Scan fingerprint/face to confirm transfer of ${KaspaUtils.formatKas(amountKas)}",
                                onSuccess = {
                                    viewModel.transferBetweenAccounts(target, amountKas, noteText)
                                },
                                onError = { err ->
                                    Toast.makeText(context, "Biometric authorization failed: $err", Toast.LENGTH_SHORT).show()
                                }
                            )
                        } else {
                            viewModel.transferBetweenAccounts(target, amountKas, noteText)
                        }
                    }
                },
                enabled = isValid && !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KaspaPrimary,
                    contentColor = Color(0xFF003731),
                    disabledContainerColor = KaspaSurfaceVariant,
                    disabledContentColor = KaspaTextMuted
                )
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color(0xFF003731),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Executing Transfer...", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                } else {
                    Text("Execute Internal Transfer", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
fun AddAccountDialog(
    viewModel: KaspaViewModel,
    accountsCount: Int,
    onDismiss: () -> Unit
) {
    var accountName by remember { mutableStateOf("Account #$accountsCount") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Account", color = KaspaTextPrimary) },
        text = {
            Column {
                Text(
                    "Each account uses an independent BIP44 derivation path under your root master seed.",
                    color = KaspaTextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = accountName,
                    onValueChange = { accountName = it },
                    label = { Text("Account Label") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = KaspaTextPrimary,
                        unfocusedTextColor = KaspaTextPrimary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (accountName.isNotBlank()) {
                        viewModel.createNewAccount(accountName.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = KaspaPrimary, contentColor = Color(0xFF003731))
            ) {
                Text("Create Account")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = KaspaTextSecondary)
            }
        },
        containerColor = KaspaSurface
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContactDialog(
    viewModel: KaspaViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isAddressValid = address.isBlank() || KaspaUtils.isValidKaspaAddress(address)
    val canSave = name.isNotBlank() && address.isNotBlank() && KaspaUtils.isValidKaspaAddress(address)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                .verticalScroll(rememberScrollState())
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
                            .background(KaspaPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.PersonAdd, contentDescription = null, tint = KaspaPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Add Contact", color = KaspaTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Save to Address Book", color = KaspaTextSecondary, fontSize = 12.sp)
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = KaspaTextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Inputs
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Contact Name") },
                    placeholder = { Text("e.g. Alice Mining Pool") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = KaspaTextPrimary,
                        unfocusedTextColor = KaspaTextPrimary,
                        focusedContainerColor = KaspaSurfaceVariant,
                        unfocusedContainerColor = KaspaSurfaceVariant
                    )
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it.trim() },
                    label = { Text("Kaspa Address") },
                    placeholder = { Text("kaspa:qq...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = KaspaTextPrimary,
                        unfocusedTextColor = KaspaTextPrimary,
                        focusedContainerColor = KaspaSurfaceVariant,
                        unfocusedContainerColor = KaspaSurfaceVariant
                    ),
                    trailingIcon = {
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                            if (!clip.isNullOrBlank()) {
                                address = clip.trim()
                            }
                        }) {
                            Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste", tint = KaspaPrimary)
                        }
                    }
                )

                if (address.isNotEmpty() && !isAddressValid) {
                    Text(
                        "Invalid Kaspa address format (e.g. kaspa:qq...)",
                        color = KaspaError,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note / Tag (Optional)") },
                    placeholder = { Text("e.g. Primary payout wallet") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = KaspaTextPrimary,
                        unfocusedTextColor = KaspaTextPrimary,
                        focusedContainerColor = KaspaSurfaceVariant,
                        unfocusedContainerColor = KaspaSurfaceVariant
                    )
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, KaspaCardBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KaspaTextSecondary)
                ) {
                    Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        if (canSave) {
                            viewModel.saveContact(name.trim(), address.trim(), note.trim())
                        }
                    },
                    enabled = canSave,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KaspaPrimary,
                        contentColor = Color(0xFF003731),
                        disabledContainerColor = KaspaSurfaceVariant,
                        disabledContentColor = KaspaTextMuted
                    )
                ) {
                    Text("Save Contact", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeedBackupDialog(
    mnemonicPhrase: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val words = remember(mnemonicPhrase) { mnemonicPhrase.trim().split("\\s+".toRegex()).filter { it.isNotBlank() } }
    var isRevealed by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
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
                            .background(KaspaPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = KaspaPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Secret Recovery Phrase", color = KaspaTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("${words.size}-Word Seed Backup", color = KaspaTextSecondary, fontSize = 12.sp)
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = KaspaTextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Security Warning Banner
            Card(
                colors = CardDefaults.cardColors(containerColor = KaspaWarning.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(14.dp),
                
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = KaspaWarning, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Never share your recovery phrase. Anyone with these words has full control over your wallet funds.",
                        color = KaspaWarning,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Privacy Toggle Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Seed Words", color = KaspaTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                TextButton(
                    onClick = {
                        if (!isRevealed) {
                            val fragmentActivity = context as? FragmentActivity
                            if (fragmentActivity != null && BiometricAuthManager.isBiometricAvailable(context)) {
                                BiometricAuthManager.promptBiometric(
                                    activity = fragmentActivity,
                                    title = "Reveal Secret Recovery Phrase",
                                    subtitle = "Scan fingerprint/face to view your seed words",
                                    onSuccess = {
                                        isRevealed = true
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, "Biometric authorization failed: $err", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            } else {
                                isRevealed = true
                            }
                        } else {
                            isRevealed = false
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = KaspaPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (isRevealed) "Hide Words" else "Reveal Words",
                        color = KaspaPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Words Grid
            if (isRevealed) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (words.size > 12) 230.dp else 160.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(words) { index, word ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = KaspaSurfaceVariant),
                            shape = RoundedCornerShape(10.dp),
                            
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${index + 1}.",
                                    color = KaspaTextMuted,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    word,
                                    color = KaspaTextPrimary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            } else {
                // Hidden / Privacy Mode Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(KaspaSurfaceVariant)
                        .noRippleClickable { isRevealed = true },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Visibility, contentDescription = null, tint = KaspaPrimary, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Tap to reveal seed phrase", color = KaspaTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Ensure no one is looking at your screen", color = KaspaTextMuted, fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Kaspa Seed Phrase", mnemonicPhrase)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            clip.description.extras = android.os.PersistableBundle().apply {
                                putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
                            }
                        }
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Seed phrase copied. Clear clipboard after backup!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, KaspaCardBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KaspaTextPrimary)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Phrase", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KaspaPrimary, contentColor = Color(0xFF003731))
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("I've Saved It", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CreateWalletDialog(
    viewModel: KaspaViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var walletName by remember { mutableStateOf("Kaspa Wallet 1") }
    var selectedWordCount by remember { mutableIntStateOf(12) }
    var hasPassphrase by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Wallet", color = KaspaTextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = walletName,
                    onValueChange = { walletName = it },
                    label = { Text("Wallet Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = KaspaTextPrimary,
                        unfocusedTextColor = KaspaTextPrimary
                    )
                )

                Text("Seed Phrase Length", color = KaspaTextSecondary, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(12, 24).forEach { count ->
                        val isSelected = selectedWordCount == count
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .noRippleClickable { selectedWordCount = count },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) KaspaPrimary.copy(alpha = 0.2f) else KaspaSurfaceVariant
                            )
                        ) {
                            Box(modifier = Modifier.padding(10.dp), contentAlignment = Alignment.Center) {
                                Text("$count Words", color = if (isSelected) KaspaPrimaryGlow else KaspaTextPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (walletName.isNotBlank()) {
                        val words = KaspaUtils.generateMnemonic(selectedWordCount)
                        viewModel.createNewWallet(context, walletName.trim(), words, hasPassphrase)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = KaspaPrimary, contentColor = Color(0xFF003731))
            ) {
                Text("Generate Wallet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = KaspaTextSecondary)
            }
        },
        containerColor = KaspaSurface
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportWalletDialog(
    viewModel: KaspaViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var walletName by remember { mutableStateOf("Imported Wallet") }
    var seedPhrase by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val wordCount = remember(seedPhrase) {
        seedPhrase.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }.size
    }
    val isValidSeed = wordCount in listOf(12, 18, 24)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                .verticalScroll(rememberScrollState())
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
                            .background(KaspaPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = KaspaPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Import / Recover Wallet", color = KaspaTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Restore with 12, 18 or 24-word Seed", color = KaspaTextSecondary, fontSize = 12.sp)
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = KaspaTextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = walletName,
                    onValueChange = { walletName = it },
                    label = { Text("Wallet Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = KaspaTextPrimary,
                        unfocusedTextColor = KaspaTextPrimary,
                        focusedContainerColor = KaspaSurfaceVariant,
                        unfocusedContainerColor = KaspaSurfaceVariant
                    )
                )

                OutlinedTextField(
                    value = seedPhrase,
                    onValueChange = { seedPhrase = it },
                    label = { Text("Recovery Seed Phrase") },
                    placeholder = { Text("Enter your 12, 18, or 24 mnemonic words separated by spaces...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = KaspaTextPrimary,
                        unfocusedTextColor = KaspaTextPrimary,
                        focusedContainerColor = KaspaSurfaceVariant,
                        unfocusedContainerColor = KaspaSurfaceVariant
                    ),
                    trailingIcon = {
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                            if (!clip.isNullOrBlank()) {
                                seedPhrase = clip.trim()
                            }
                        }) {
                            Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste", tint = KaspaPrimary)
                        }
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Word Count: $wordCount words",
                        color = if (isValidSeed) KaspaPrimaryGlow else KaspaTextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (seedPhrase.isNotBlank() && !isValidSeed) {
                        Text(
                            "Expected 12, 18 or 24 words",
                            color = KaspaError,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Button(
                onClick = {
                    if (walletName.isNotBlank() && isValidSeed) {
                        viewModel.importWallet(context, walletName.trim(), seedPhrase.trim())
                    }
                },
                enabled = walletName.isNotBlank() && isValidSeed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KaspaPrimary,
                    contentColor = Color(0xFF003731),
                    disabledContainerColor = KaspaSurfaceVariant,
                    disabledContentColor = KaspaTextMuted
                )
            ) {
                Icon(Icons.Default.DownloadDone, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Restore Wallet", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun TransactionSuccessDialog(
    transaction: TransactionEntity,
    network: KaspaNetwork,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = KaspaSurface,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, KaspaPrimaryGlow.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Success Checkmark Circle
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(KaspaPrimary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = KaspaPrimaryGlow,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Text(
                    "Transaction Broadcast Successful!",
                    color = KaspaTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    "Your KAS transfer has been signed and accepted by the Kaspa BlockDAG network.",
                    color = KaspaTextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                HorizontalDivider(color = KaspaCardBorder)

                // Details Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = KaspaSurfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Amount Sent:", color = KaspaTextSecondary, fontSize = 13.sp)
                            Text(
                                KaspaUtils.formatKas(KaspaUtils.sompiToKas(transaction.amountSompi)),
                                color = KaspaPrimaryGlow,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Network Fee:", color = KaspaTextSecondary, fontSize = 13.sp)
                            Text(
                                KaspaUtils.formatSompi(transaction.feeSompi),
                                color = KaspaTextPrimary,
                                fontSize = 13.sp
                            )
                        }

                        Column {
                            Text("Recipient Address:", color = KaspaTextSecondary, fontSize = 12.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    KaspaUtils.truncateAddress(transaction.recipientAddress),
                                    color = KaspaTextPrimary,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                )
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Address", transaction.recipientAddress))
                                        Toast.makeText(context, "Recipient address copied", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Address", tint = KaspaPrimary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Column {
                            Text("Transaction ID (Hash):", color = KaspaTextSecondary, fontSize = 12.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    KaspaUtils.truncateAddress(transaction.id),
                                    color = KaspaTextPrimary,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                )
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("TxID", transaction.id))
                                        Toast.makeText(context, "Transaction ID copied", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy TxID", tint = KaspaPrimary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // Explorer Link
                OutlinedButton(
                    onClick = {
                        val explorerUrl = if (network != KaspaNetwork.MAINNET) {
                            "https://explorer.kaspa.org/txs/${transaction.id}?network=testnet"
                        } else {
                            "https://explorer.kaspa.org/txs/${transaction.id}"
                        }
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(explorerUrl))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open web browser", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, KaspaPrimary)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = KaspaPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View on Kaspa Explorer", color = KaspaPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                // Done Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KaspaPrimary, contentColor = Color(0xFF003731))
                ) {
                    Text("Done", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
