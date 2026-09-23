package com.example.kaspawallet.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.example.kaspawallet.data.crypto.KaspaUtils
import com.example.kaspawallet.data.model.KaspaNetwork
import com.example.kaspawallet.ui.PendingTxDetails
import com.example.kaspawallet.ui.TxExecutionStatus
import com.example.kaspawallet.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-page Transaction Status & Receipt Screen.
 * Provides authentic, native Kaspa design with live transition
 * from PENDING broadcast to SUCCESSFUL confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionStatusScreen(
    pendingTx: PendingTxDetails,
    network: KaspaNetwork,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Transaction Status",
                        color = KaspaTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = KaspaTextPrimary
                        )
                    }
                },
                actions = {
                    Surface(
                        color = KaspaSurfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, KaspaCardBorder)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (network == KaspaNetwork.MAINNET) KaspaPrimary else KaspaWarning
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = network.displayName,
                                color = KaspaTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
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
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Live Status Banner with State Transition Animation
            AnimatedContent(
                targetState = pendingTx.status,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "status_transition"
            ) { status ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when (status) {
                        TxExecutionStatus.PENDING -> {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(KaspaPrimary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(46.dp),
                                    color = KaspaPrimary,
                                    strokeWidth = 3.dp
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    tint = KaspaPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Broadcasting Transaction",
                                color = KaspaTextPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Submitting to Kaspa BlockDAG consensus nodes...",
                                color = KaspaTextSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                color = KaspaPrimary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, KaspaPrimary.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(10.dp),
                                        color = KaspaPrimary,
                                        strokeWidth = 1.8.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "PENDING ON-CHAIN CONFIRMATION",
                                        color = KaspaPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        TxExecutionStatus.SUCCESSFUL -> {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(KaspaSuccess.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Success",
                                    tint = KaspaSuccess,
                                    modifier = Modifier.size(52.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Transaction Successful!",
                                color = KaspaTextPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Broadcast accepted and recorded on the Kaspa BlockDAG.",
                                color = KaspaTextSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                color = KaspaSuccess.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, KaspaSuccess.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(KaspaSuccess)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "BROADCAST CONFIRMED",
                                        color = KaspaSuccess,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        TxExecutionStatus.FAILED -> {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(KaspaError.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Failed",
                                    tint = KaspaError,
                                    modifier = Modifier.size(52.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Transaction Failed",
                                color = KaspaTextPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = pendingTx.errorMessage ?: "The transaction was rejected by the Kaspa node.",
                                color = KaspaError,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                color = KaspaError.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, KaspaError.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(KaspaError)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "REJECTED BY NETWORK",
                                        color = KaspaError,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Amount Display
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "- ${KaspaUtils.formatKas(pendingTx.amountKas)}",
                    color = KaspaTextPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "≈ ${KaspaUtils.formatSompi(pendingTx.amountSompi)}",
                    color = KaspaTextMuted,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Failure Details Card when status is FAILED
            if (pendingTx.status == TxExecutionStatus.FAILED) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = KaspaError.copy(alpha = 0.10f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, KaspaError.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = KaspaError,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Failure Reason",
                                color = KaspaError,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val failText = pendingTx.errorMessage?.ifBlank { null } ?: "Transaction broadcast failed"
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Kaspa Error", failText))
                                    Toast.makeText(context, "Error message copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "Copy Error Message",
                                    tint = KaspaError,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = KaspaError.copy(alpha = 0.25f))

                        Text(
                            text = pendingTx.errorMessage?.ifBlank { null }
                                ?: "The transaction could not be broadcast or was rejected by the Kaspa node.",
                            color = KaspaTextPrimary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        Surface(
                            color = KaspaSurface.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = KaspaTextSecondary,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .padding(top = 1.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (pendingTx.errorMessage?.contains("UTXO", ignoreCase = true) == true || pendingTx.errorMessage?.contains("balance", ignoreCase = true) == true) {
                                        "Please verify your account balance and wait for incoming transactions or DAG confirmations to mature before retrying."
                                    } else if (pendingTx.errorMessage?.contains("network", ignoreCase = true) == true || pendingTx.errorMessage?.contains("node", ignoreCase = true) == true) {
                                        "Unable to reach Kaspa RPC node. Please verify your internet connection or check the network status in Settings."
                                    } else {
                                        "Your funds have not been debited. You can safely return to your wallet and try sending again."
                                    },
                                    color = KaspaTextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }

            // Transaction Details Card
            Card(
                colors = CardDefaults.cardColors(containerColor = KaspaSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, KaspaCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Transaction Details",
                        color = KaspaTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider(color = KaspaCardBorder)

                    // Transaction ID Row (when available)
                    if (!pendingTx.txId.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Transaction ID",
                                color = KaspaTextSecondary,
                                fontSize = 13.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = KaspaUtils.truncateAddress(pendingTx.txId, 10, 8),
                                    color = KaspaPrimary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Kaspa Tx ID", pendingTx.txId))
                                        Toast.makeText(context, "Transaction ID copied", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = "Copy Tx ID",
                                        tint = KaspaPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Recipient Address Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recipient",
                            color = KaspaTextSecondary,
                            fontSize = 13.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = KaspaUtils.truncateAddress(pendingTx.recipientAddress, 10, 8),
                                color = KaspaTextPrimary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Kaspa Recipient Address", pendingTx.recipientAddress))
                                    Toast.makeText(context, "Recipient address copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "Copy Recipient",
                                    tint = KaspaTextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Sender Account
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "From Account",
                            color = KaspaTextSecondary,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "${pendingTx.senderAccountName} (${KaspaUtils.truncateAddress(pendingTx.senderAddress, 6, 4)})",
                            color = KaspaTextPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Network Fee
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Network Fee",
                            color = KaspaTextSecondary,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "${KaspaUtils.formatKas(pendingTx.feeKas)} (${KaspaUtils.formatSompi(pendingTx.feeSompi)})",
                            color = KaspaTextPrimary,
                            fontSize = 13.sp
                        )
                    }

                    // Total Debit
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Total Debit",
                            color = KaspaTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = KaspaUtils.formatKas(pendingTx.amountKas + pendingTx.feeKas),
                            color = KaspaPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // DAA Score if confirmed
                    if (pendingTx.confirmedTx != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Block DAA Score",
                                color = KaspaTextSecondary,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "${pendingTx.confirmedTx.daaScore}",
                                color = KaspaTextPrimary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Timestamp
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Time",
                            color = KaspaTextSecondary,
                            fontSize = 13.sp
                        )
                        val formattedTime = SimpleDateFormat("MMM dd, yyyy • HH:mm:ss", Locale.getDefault())
                            .format(Date(pendingTx.timestamp))
                        Text(
                            text = formattedTime,
                            color = KaspaTextPrimary,
                            fontSize = 12.sp
                        )
                    }

                    // Memo
                    if (pendingTx.note.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Memo / Note",
                                color = KaspaTextSecondary,
                                fontSize = 13.sp
                            )
                            Text(
                                text = pendingTx.note,
                                color = KaspaTextPrimary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f, fill = false))

            // Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (pendingTx.status == TxExecutionStatus.SUCCESSFUL) {
                    if (!pendingTx.txId.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val url = if (network == KaspaNetwork.MAINNET) {
                                        "https://explorer.kaspa.org/txs/${pendingTx.txId}"
                                    } else {
                                        "https://explorer-tn10.kaspa.org/txs/${pendingTx.txId}"
                                    }
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Unable to open browser", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = KaspaPrimary),
                                border = BorderStroke(1.dp, KaspaPrimary.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Explorer", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Kaspa Tx ID", pendingTx.txId))
                                    Toast.makeText(context, "Transaction ID copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = KaspaTextPrimary),
                                border = BorderStroke(1.dp, KaspaCardBorder)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy ID", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Button(
                        onClick = onDone,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KaspaPrimary,
                            contentColor = Color(0xFF003731)
                        )
                    ) {
                        Text("Done", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (pendingTx.status == TxExecutionStatus.PENDING) {
                    Text(
                        text = "Broadcasting directly to peer-to-peer nodes. You can wait or return to your wallet.",
                        color = KaspaTextMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    OutlinedButton(
                        onClick = onDone,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = KaspaTextPrimary),
                        border = BorderStroke(1.dp, KaspaCardBorder)
                    ) {
                        Text("Return to Wallet", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    // FAILED state buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val failText = pendingTx.errorMessage?.ifBlank { null } ?: "Transaction broadcast failed"
                                clipboard.setPrimaryClip(ClipData.newPlainText("Kaspa Error", failText))
                                Toast.makeText(context, "Error message copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = KaspaTextPrimary),
                            border = BorderStroke(1.dp, KaspaCardBorder)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Error", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = onDone,
                            modifier = Modifier
                                .weight(1.2f)
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = KaspaPrimary,
                                contentColor = Color(0xFF003731)
                            )
                        ) {
                            Text("Back to Wallet", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
