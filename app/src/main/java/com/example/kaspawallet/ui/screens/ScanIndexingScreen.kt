package com.example.kaspawallet.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.example.kaspawallet.ui.theme.noRippleClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kaspawallet.data.crypto.KaspaUtils
import com.example.kaspawallet.data.model.KaspaMarketInfo
import com.example.kaspawallet.data.model.TransactionEntity
import com.example.kaspawallet.data.model.TransactionType
import com.example.kaspawallet.ui.ScanIndexingStage
import com.example.kaspawallet.ui.ScanIndexingUiState
import com.example.kaspawallet.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanIndexingScreen(
    state: ScanIndexingUiState,
    marketInfo: KaspaMarketInfo,
    selectedCurrency: String = "USD",
    onContinue: () -> Unit,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    val kasBalance = KaspaUtils.sompiToKas(state.balanceSompi)
    val fiatFormatted = KaspaUtils.formatCurrency(kasBalance, marketInfo.priceUsd, selectedCurrency)

    // Animated radar pulse
    val infiniteTransition = rememberInfiniteTransition(label = "radar_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_rotation"
    )

    // Smooth real-time progress interpolation from 0% to 100%
    val animatedProgress by animateFloatAsState(
        targetValue = state.progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "smooth_indexing_progress"
    )

    Scaffold(
        containerColor = KaspaBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (state.isImportMode) "Import & Indexing" else "Wallet Indexing",
                            color = KaspaTextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Live Network Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(KaspaPrimary.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = state.network.displayName,
                                color = KaspaPrimaryGlow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = KaspaSurface)
            )
        },
        bottomBar = {
            Surface(
                color = KaspaSurface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    if (state.stage == ScanIndexingStage.FAILED) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onRetry,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = KaspaPrimaryGlow),
                                border = ButtonDefaults.outlinedButtonBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(KaspaPrimary)
                                )
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry Scan")
                            }

                            Button(
                                onClick = onContinue,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = KaspaPrimary,
                                    contentColor = Color(0xFF003731)
                                )
                            ) {
                                Text("Continue", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    } else {
                        Button(
                            onClick = onContinue,
                            enabled = state.isComplete,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("finish_indexing_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = KaspaPrimary,
                                contentColor = Color(0xFF003731),
                                disabledContainerColor = KaspaSurfaceVariant,
                                disabledContentColor = KaspaTextSecondary
                            )
                        ) {
                            if (state.isComplete) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open Wallet Dashboard", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = KaspaPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Indexing On-Chain BlockDAG...", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            }
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ==========================================
            // HERO RADAR & SCANNING VISUALIZATION
            // ==========================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer Pulse Ring
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(if (state.isComplete) 1f else pulseScale)
                        .clip(CircleShape)
                        .background(
                            if (state.isComplete) KaspaSuccess.copy(alpha = 0.08f)
                            else KaspaPrimary.copy(alpha = 0.10f)
                        )
                )
                // Mid Ring
                Box(
                    modifier = Modifier
                        .size(105.dp)
                        .clip(CircleShape)
                        .border(
                            width = 1.5.dp,
                            color = if (state.isComplete) KaspaSuccess.copy(alpha = 0.4f) else KaspaPrimary.copy(alpha = 0.4f),
                            shape = CircleShape
                        )
                        .background(KaspaSurface)
                )

                // Central Icon
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = if (state.isComplete) {
                                    listOf(KaspaSuccess.copy(alpha = 0.3f), KaspaSuccess.copy(alpha = 0.1f))
                                } else {
                                    listOf(KaspaPrimary.copy(alpha = 0.3f), KaspaPrimary.copy(alpha = 0.1f))
                                }
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.isComplete) Icons.Default.CheckCircle else Icons.Default.Hub,
                        contentDescription = "BlockDAG Indexer",
                        tint = if (state.isComplete) KaspaSuccess else KaspaPrimaryGlow,
                        modifier = Modifier
                            .size(34.dp)
                            .then(
                                if (!state.isComplete) Modifier.rotate(rotationAngle * 0.1f) else Modifier
                            )
                    )
                }
            }

            // Headline & Status Badge
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (state.isComplete) KaspaSuccess.copy(alpha = 0.15f)
                            else KaspaPrimary.copy(alpha = 0.15f)
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (state.isComplete) KaspaSuccess else KaspaPrimaryGlow)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (state.isComplete) "ON-CHAIN SYNC COMPLETE" else "INDEXING BLOCKDAG ON-CHAIN",
                        color = if (state.isComplete) KaspaSuccess else KaspaPrimaryGlow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                }

                Text(
                    text = if (state.isComplete) "Wallet Ready for Use" else "Scanning Wallet Address & History",
                    color = KaspaTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (state.isImportMode) {
                        "Restoring historical BlockDAG transactions, unspent outputs (UTXOs), and consensus balance."
                    } else {
                        "Verifying deterministic address generation and registering initial consensus state on the BlockDAG."
                    },
                    color = KaspaTextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }

            // ==========================================
            // PROGRESS BAR & REALTIME STATUS
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = KaspaSurface),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(KaspaCardBorder)
                )
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
                        Text(
                            text = state.statusMessage.ifBlank { "Scanning on-chain data..." },
                            color = KaspaTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${(animatedProgress * 100).toInt()}%",
                            color = KaspaPrimaryGlow,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (state.isComplete) KaspaSuccess else KaspaPrimary,
                        trackColor = KaspaSurfaceVariant
                    )
                }
            }

            // ==========================================
            // TARGET WALLET & ADDRESS CARD
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = KaspaSurface),
                shape = RoundedCornerShape(16.dp)
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = KaspaPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = state.walletName.ifBlank { "Kaspa Primary Wallet" },
                                color = KaspaTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Derivation Path
                        Text(
                            text = state.derivationPath,
                            color = KaspaTextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Derived Address with Copy
                    if (state.derivedAddress.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(KaspaBackground)
                                .noRippleClickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Kaspa Address", state.derivedAddress))
                                    Toast.makeText(context, "Address copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.QrCode,
                                contentDescription = null,
                                tint = KaspaPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = state.derivedAddress,
                                color = KaspaTextPrimary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy address",
                                tint = KaspaTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // ==========================================
            // SUMMARY OF ON-CHAIN DISCOVERY (HERO METRICS GRID)
            // ==========================================
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "On-Chain Discovery Summary",
                    color = KaspaTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Metric 1: Confirmed Balance
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = KaspaSurface),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = KaspaPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("On-Chain Balance", color = KaspaTextSecondary, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${KaspaUtils.formatKas(kasBalance)} KAS",
                                color = KaspaTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "≈ $fiatFormatted",
                                color = KaspaTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Metric 2: Discovered UTXOs
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = KaspaSurface),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Layers, contentDescription = null, tint = KaspaPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Confirmed UTXOs", color = KaspaTextSecondary, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${state.utxoCount} UTXOs",
                                color = KaspaTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (state.utxoCount > 0) "Outputs indexed" else "Zero outputs",
                                color = KaspaTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Metric 3: Historical Transactions
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = KaspaSurface),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.History, contentDescription = null, tint = KaspaPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("History Indexed", color = KaspaTextSecondary, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${state.txCount} Transactions",
                                color = KaspaTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (state.txCount > 0) "Synchronized" else "Fresh address",
                                color = KaspaTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Metric 4: DAA BlueScore
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = KaspaSurface),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Speed, contentDescription = null, tint = KaspaPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("DAA BlueScore", color = KaspaTextSecondary, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (state.daaScore > 0) "#${state.daaScore}" else "Live Node",
                                color = KaspaTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Consensus height",
                                color = KaspaTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // ==========================================
            // STEP-BY-STEP ON-CHAIN VERIFICATION CHECKLIST
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = KaspaSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "On-Chain Verification Stages",
                        color = KaspaTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Step 1: Key Derivation
                    VerificationStepItem(
                        title = "BIP-44 Address Derivation",
                        subtitle = "Standard Schnorr keypair at m/44'/111111'/0'/0/0",
                        isCompleted = state.stage.ordinal >= ScanIndexingStage.DERIVING_KEYS.ordinal && state.derivedAddress.isNotBlank(),
                        isActive = state.stage == ScanIndexingStage.DERIVING_KEYS,
                        icon = Icons.Default.Key
                    )

                    // Step 2: Node Handshake
                    VerificationStepItem(
                        title = "Kaspa Node Handshake",
                        subtitle = if (state.daaScore > 0) "Connected • Virtual DAA #${state.daaScore}" else "Connecting to ${state.network.displayName}...",
                        isCompleted = state.stage.ordinal >= ScanIndexingStage.CONNECTING_NODE.ordinal && state.daaScore > 0,
                        isActive = state.stage == ScanIndexingStage.CONNECTING_NODE,
                        icon = Icons.Default.CloudSync
                    )

                    // Step 3: UTXO Set Query
                    VerificationStepItem(
                        title = "On-Chain UTXO Graph Query",
                        subtitle = if (state.stage.ordinal >= ScanIndexingStage.SCANNING_UTXOS.ordinal) "${state.utxoCount} unspent transaction outputs confirmed" else "Querying DAG unspent outputs...",
                        isCompleted = state.stage.ordinal >= ScanIndexingStage.SCANNING_UTXOS.ordinal && state.progress >= 0.82f,
                        isActive = state.stage == ScanIndexingStage.SCANNING_UTXOS,
                        icon = Icons.Default.Layers
                    )

                    // Step 4: Consensus Balance
                    VerificationStepItem(
                        title = "Consensus Balance Verification",
                        subtitle = if (state.stage.ordinal >= ScanIndexingStage.CALCULATING_BALANCE.ordinal && state.progress >= 0.92f) "${KaspaUtils.formatKas(kasBalance)} KAS verified (${state.balanceSompi} Sompi)" else "Aggregating Sompi outputs...",
                        isCompleted = state.stage.ordinal >= ScanIndexingStage.CALCULATING_BALANCE.ordinal && state.progress >= 0.94f,
                        isActive = state.stage == ScanIndexingStage.CALCULATING_BALANCE,
                        icon = Icons.Default.CheckCircleOutline
                    )

                    // Step 5: History Indexing
                    VerificationStepItem(
                        title = "Transaction History Indexing",
                        subtitle = if (state.isComplete) "${state.txCount} historical transactions indexed to secure database" else "Querying historical blocks & payloads...",
                        isCompleted = state.isComplete,
                        isActive = state.stage == ScanIndexingStage.INDEXING_HISTORY,
                        icon = Icons.Default.HistoryEdu
                    )
                }
            }

            // ==========================================
            // INDEXED TRANSACTIONS PREVIEW (If any exist)
            // ==========================================
            if (state.indexedTransactions.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = KaspaSurface),
                    shape = RoundedCornerShape(16.dp)
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
                            Text(
                                text = "Indexed On-Chain Transactions",
                                color = KaspaTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${state.indexedTransactions.size} Found",
                                color = KaspaPrimaryGlow,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        state.indexedTransactions.take(4).forEach { tx ->
                            val isIncoming = tx.txType == TransactionType.RECEIVE
                            val txKas = KaspaUtils.sompiToKas(tx.amountSompi)
                            val dateFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(KaspaSurfaceVariant.copy(alpha = 0.5f))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isIncoming) KaspaSuccess.copy(alpha = 0.15f)
                                                else Color(0xFFE0E0E0).copy(alpha = 0.15f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isIncoming) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                            contentDescription = null,
                                            tint = if (isIncoming) KaspaSuccess else Color(0xFFE0E0E0),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = if (isIncoming) "Received KAS" else "Sent KAS",
                                            color = KaspaTextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = dateFormat.format(Date(tx.timestamp)),
                                            color = KaspaTextSecondary,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                Text(
                                    text = "${if (isIncoming) "+" else "-"}${KaspaUtils.formatKas(txKas)} KAS",
                                    color = if (isIncoming) KaspaSuccess else KaspaTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else if (state.isComplete) {
                // Fresh Wallet Confirmation Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = KaspaSurfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = null,
                            tint = KaspaPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Clean On-Chain Address Ready",
                                color = KaspaTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Zero prior transactions detected. You can safely deposit KAS or share this address to receive funds.",
                                color = KaspaTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun VerificationStepItem(
    title: String,
    subtitle: String,
    isCompleted: Boolean,
    isActive: Boolean,
    icon: ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status Icon
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isCompleted -> KaspaSuccess.copy(alpha = 0.15f)
                        isActive -> KaspaPrimary.copy(alpha = 0.20f)
                        else -> KaspaSurfaceVariant
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                isCompleted -> {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Done",
                        tint = KaspaSuccess,
                        modifier = Modifier.size(18.dp)
                    )
                }
                isActive -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = KaspaPrimary,
                        strokeWidth = 2.dp
                    )
                }
                else -> {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = KaspaTextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = when {
                    isCompleted -> KaspaTextPrimary
                    isActive -> KaspaPrimaryGlow
                    else -> KaspaTextSecondary
                },
                fontSize = 13.sp,
                fontWeight = if (isActive || isCompleted) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                text = subtitle,
                color = if (isCompleted || isActive) KaspaTextSecondary else KaspaTextSecondary.copy(alpha = 0.6f),
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}
