package com.example.kaspawallet.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.kaspawallet.data.crypto.KaspaUtils
import com.example.kaspawallet.data.security.BiometricAuthManager
import com.example.kaspawallet.ui.KaspaViewModel
import com.example.kaspawallet.ui.WalletUiState
import kotlinx.coroutines.launch
import com.example.kaspawallet.ui.components.KaspaQrCode
import com.example.kaspawallet.ui.theme.*

@Composable
fun ToolsTab(
    state: WalletUiState,
    viewModel: KaspaViewModel
) {
    val context = LocalContext.current
    var selectedToolSection by remember { mutableIntStateOf(0) } // 0: UTXOs, 1: Checker, 2: Generator

    // Checker state
    var checkAddressInput by remember { mutableStateOf("") }
    var checkedBalanceResult by remember { mutableStateOf<String?>(null) }
    var isCheckingAddress by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val apiClient = remember { com.example.kaspawallet.data.api.KaspaApiClient() }

    // Generator state
    var generatedWords by remember { mutableStateOf<List<String>?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Tool Selector Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedToolSection == 0,
                onClick = { selectedToolSection = 0 },
                label = { Text("UTXO Compound (${state.utxos.size})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = KaspaPrimary,
                    selectedLabelColor = Color(0xFF003731),
                    containerColor = KaspaSurfaceVariant,
                    labelColor = KaspaTextSecondary
                )
            )

            FilterChip(
                selected = selectedToolSection == 1,
                onClick = { selectedToolSection = 1 },
                label = { Text("Address Checker") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = KaspaPrimary,
                    selectedLabelColor = Color(0xFF003731),
                    containerColor = KaspaSurfaceVariant,
                    labelColor = KaspaTextSecondary
                )
            )

            FilterChip(
                selected = selectedToolSection == 2,
                onClick = { selectedToolSection = 2 },
                label = { Text("Keypair Gen") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = KaspaPrimary,
                    selectedLabelColor = Color(0xFF003731),
                    containerColor = KaspaSurfaceVariant,
                    labelColor = KaspaTextSecondary
                )
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        when (selectedToolSection) {
            0 -> {
                // UTXO Management
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = KaspaSurface)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("UTXO Consolidation", color = KaspaTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text("${state.utxos.size} Outputs", color = KaspaPrimaryGlow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Consolidating multiple unspent outputs into a single output reduces future transaction fees and keeps your wallet optimized.",
                                    color = KaspaTextSecondary,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = {
                                        val fragmentActivity = context as? FragmentActivity
                                        if (fragmentActivity != null && BiometricAuthManager.isBiometricAvailable(context)) {
                                            BiometricAuthManager.promptBiometric(
                                                activity = fragmentActivity,
                                                title = "Authorize UTXO Consolidation",
                                                subtitle = "Scan fingerprint/face to confirm compounding ${state.utxos.size} UTXOs",
                                                onSuccess = {
                                                    viewModel.compoundUtxos()
                                                },
                                                onError = { err ->
                                                    Toast.makeText(context, "Biometric authorization failed: $err", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        } else {
                                            viewModel.compoundUtxos()
                                        }
                                    },
                                    enabled = state.utxos.size > 1,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = KaspaPrimary, contentColor = Color(0xFF003731))
                                ) {
                                    Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (state.utxos.size > 1) "Compound ${state.utxos.size} UTXOs into 1" else "UTXOs Already Consolidated",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Text("Active Account UTXOs", color = KaspaTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

                    if (state.utxos.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = KaspaSurfaceVariant)
                            ) {
                                Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                                    Text("No UTXOs currently tracked", color = KaspaTextSecondary)
                                }
                            }
                        }
                    } else {
                        items(state.utxos) { utxo ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = KaspaSurface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            KaspaUtils.formatSompi(utxo.amountSompi),
                                            color = KaspaPrimaryGlow,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Outpoint #${utxo.outpointIndex}",
                                            color = KaspaTextMuted,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "TxID: ${KaspaUtils.truncateAddress(utxo.outpointTxId, 16, 12)}",
                                        color = KaspaTextSecondary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        "DAA Score: #${utxo.blockDaaScore}",
                                        color = KaspaTextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // Address Checker
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = KaspaSurface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Check Kaspa Address", color = KaspaTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Verify formatting, checksum prefix, and check balance on node.", color = KaspaTextSecondary, fontSize = 12.sp)

                                Spacer(modifier = Modifier.height(14.dp))

                                OutlinedTextField(
                                    value = checkAddressInput,
                                    onValueChange = { checkAddressInput = it.trim() },
                                    label = { Text("Kaspa Address") },
                                    placeholder = { Text("kaspa:qq...") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedTextColor = KaspaTextPrimary,
                                        unfocusedTextColor = KaspaTextPrimary
                                    )
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        if (KaspaUtils.isValidKaspaAddress(checkAddressInput)) {
                                            isCheckingAddress = true
                                            coroutineScope.launch {
                                                val sompi = apiClient.fetchAddressBalance(checkAddressInput, state.network)
                                                val kas = KaspaUtils.sompiToKas(sompi)
                                                checkedBalanceResult = "Valid Kaspa Address on ${state.network.displayName}\nPrefix: ${state.network.prefix}\nOn-chain Balance: ${KaspaUtils.formatKas(kas)} ($sompi Sompi)"
                                                isCheckingAddress = false
                                            }
                                        } else {
                                            checkedBalanceResult = "Invalid Kaspa address format or checksum"
                                        }
                                    },
                                    enabled = !isCheckingAddress,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = KaspaPrimary, contentColor = Color(0xFF003731))
                                ) {
                                    if (isCheckingAddress) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF003731), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Querying Kaspa Node...", fontWeight = FontWeight.Bold)
                                    } else {
                                        Text("Verify & Check On-Chain Balance", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    checkedBalanceResult?.let { res ->
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = KaspaSurfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Verification Output:", color = KaspaTextSecondary, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(res, color = if (res.startsWith("Valid")) KaspaSuccess else KaspaError, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                // Keypair / Paper Generator
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = KaspaSurface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Offline Keypair Generator", color = KaspaTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Generate a fresh 12-word BIP39 seed phrase and public address for cold storage.", color = KaspaTextSecondary, fontSize = 12.sp)

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = {
                                        generatedWords = KaspaUtils.generateMnemonic(12)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = KaspaPrimary, contentColor = Color(0xFF003731))
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Generate Fresh Keypair", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    generatedWords?.let { words ->
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = KaspaSurfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Generated Seed Words:", color = KaspaPrimaryGlow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        words.joinToString(" "),
                                        color = KaspaTextPrimary,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 20.sp
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    val genReceiveAddress = KaspaUtils.generateDeterministicAddress(words, 0, state.network, 0)
                                    val genChangeAddress = KaspaUtils.generateDeterministicChangeAddress(words, 0, state.network, 0)

                                    Text("Primary Receive Address (m/44'/111111'/0'/0/0):", color = KaspaTextSecondary, fontSize = 11.sp)
                                    Text(genReceiveAddress, color = KaspaPrimaryGlow, fontSize = 11.sp, fontFamily = FontFamily.Monospace)

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text("Change Address (m/44'/111111'/0'/1/0):", color = KaspaTextSecondary, fontSize = 11.sp)
                                    Text(genChangeAddress, color = KaspaTextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)

                                    Spacer(modifier = Modifier.height(12.dp))
                                    KaspaQrCode(content = genReceiveAddress, size = 150.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
