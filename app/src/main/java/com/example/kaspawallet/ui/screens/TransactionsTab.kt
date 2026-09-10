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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kaspawallet.data.crypto.KaspaUtils
import com.example.kaspawallet.data.model.TransactionEntity
import com.example.kaspawallet.data.model.TransactionType
import com.example.kaspawallet.ui.WalletUiState
import com.example.kaspawallet.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransactionsTab(state: WalletUiState) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf<TransactionType?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTxDetail by remember { mutableStateOf<TransactionEntity?>(null) }

    val filteredTransactions = remember(state.transactions, selectedFilter, searchQuery) {
        state.transactions.filter { tx ->
            val matchFilter = selectedFilter == null || tx.txType == selectedFilter
            val matchQuery = searchQuery.isBlank() ||
                    tx.id.contains(searchQuery, ignoreCase = true) ||
                    tx.recipientAddress.contains(searchQuery, ignoreCase = true) ||
                    tx.senderAddress.contains(searchQuery, ignoreCase = true) ||
                    tx.note.contains(searchQuery, ignoreCase = true)
            matchFilter && matchQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by TxID, address or note...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = KaspaTextSecondary) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = KaspaTextSecondary)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = KaspaTextPrimary,
                unfocusedTextColor = KaspaTextPrimary
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == null,
                onClick = { selectedFilter = null },
                label = { Text("All (${state.transactions.size})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = KaspaPrimary,
                    selectedLabelColor = Color(0xFF003731),
                    containerColor = KaspaSurfaceVariant,
                    labelColor = KaspaTextSecondary
                )
            )

            FilterChip(
                selected = selectedFilter == TransactionType.SEND,
                onClick = { selectedFilter = TransactionType.SEND },
                label = { Text("Sent") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = KaspaPrimary,
                    selectedLabelColor = Color(0xFF003731),
                    containerColor = KaspaSurfaceVariant,
                    labelColor = KaspaTextSecondary
                )
            )

            FilterChip(
                selected = selectedFilter == TransactionType.RECEIVE,
                onClick = { selectedFilter = TransactionType.RECEIVE },
                label = { Text("Received") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = KaspaPrimary,
                    selectedLabelColor = Color(0xFF003731),
                    containerColor = KaspaSurfaceVariant,
                    labelColor = KaspaTextSecondary
                )
            )

            FilterChip(
                selected = selectedFilter == TransactionType.COMPOUND,
                onClick = { selectedFilter = TransactionType.COMPOUND },
                label = { Text("Compound") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = KaspaPrimary,
                    selectedLabelColor = Color(0xFF003731),
                    containerColor = KaspaSurfaceVariant,
                    labelColor = KaspaTextSecondary
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredTransactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.ReceiptLong,
                        contentDescription = null,
                        tint = KaspaTextMuted,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No transactions found", color = KaspaTextSecondary, fontSize = 16.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredTransactions) { tx ->
                    val isIncoming = tx.txType == TransactionType.RECEIVE
                    val isCompound = tx.txType == TransactionType.COMPOUND
                    val isTransfer = tx.txType == TransactionType.TRANSFER

                    val icon = when {
                        isCompound -> Icons.Default.Compress
                        isTransfer -> Icons.Default.SwapHoriz
                        isIncoming -> Icons.Default.ArrowDownward
                        else -> Icons.Default.ArrowUpward
                    }

                    val iconColor = when {
                        isCompound -> Color(0xFFBA68C8)
                        isTransfer -> Color(0xFF64B5F6)
                        isIncoming -> KaspaSuccess
                        else -> Color(0xFFE0E0E0)
                    }

                    val title = when (tx.txType) {
                        TransactionType.SEND -> "Sent to ${KaspaUtils.truncateAddress(tx.recipientAddress, 8, 6)}"
                        TransactionType.RECEIVE -> "Received from ${KaspaUtils.truncateAddress(tx.senderAddress, 8, 6)}"
                        TransactionType.TRANSFER -> "Internal Account Transfer"
                        TransactionType.COMPOUND -> "Consolidated UTXOs"
                    }

                    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(tx.timestamp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .noRippleClickable { selectedTxDetail = tx },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = KaspaSurface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(iconColor.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(title, color = KaspaTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(dateStr, color = KaspaTextMuted, fontSize = 10.sp)
                                    }
                                }

                                val prefix = if (isIncoming) "+" else if (isCompound) "" else "-"
                                val amountKas = KaspaUtils.sompiToKas(tx.amountSompi)
                                Text(
                                    "$prefix${KaspaUtils.formatKas(amountKas)}",
                                    color = if (isIncoming) KaspaSuccess else KaspaTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (tx.note.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Note: ${tx.note}",
                                    color = KaspaTextSecondary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 46.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Detail Dialog
    selectedTxDetail?.let { tx ->
        AlertDialog(
            onDismissRequest = { selectedTxDetail = null },
            title = {
                Text("Transaction Details", color = KaspaTextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailRow("TxID", KaspaUtils.truncateAddress(tx.id, 14, 10)) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Kaspa TxID", tx.id))
                        Toast.makeText(context, "TxID copied", Toast.LENGTH_SHORT).show()
                    }
                    DetailRow("Amount", KaspaUtils.formatSompi(tx.amountSompi))
                    DetailRow("Fee", KaspaUtils.formatSompi(tx.feeSompi))
                    DetailRow("Sender", KaspaUtils.truncateAddress(tx.senderAddress, 10, 8))
                    DetailRow("Recipient", KaspaUtils.truncateAddress(tx.recipientAddress, 10, 8))
                    DetailRow("Virtual DAA Score", "#${tx.daaScore}")
                    DetailRow("Status", tx.status.name)
                    if (tx.note.isNotEmpty()) {
                        DetailRow("Note", tx.note)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedTxDetail = null },
                    colors = ButtonDefaults.buttonColors(containerColor = KaspaPrimary, contentColor = Color(0xFF003731))
                ) {
                    Text("Close")
                }
            },
            containerColor = KaspaSurface
        )
    }
}

@Composable
fun DetailRow(label: String, value: String, onCopy: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = KaspaTextSecondary, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, color = KaspaTextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
            if (onCopy != null) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onCopy, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = KaspaPrimary, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}
