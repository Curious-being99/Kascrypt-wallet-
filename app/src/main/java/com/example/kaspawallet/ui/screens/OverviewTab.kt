package com.example.kaspawallet.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.example.kaspawallet.ui.theme.noRippleClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.kaspawallet.R
import com.example.kaspawallet.data.crypto.KaspaUtils
import com.example.kaspawallet.data.security.BiometricAuthManager
import com.example.kaspawallet.data.model.TransactionType
import com.example.kaspawallet.ui.KaspaViewModel
import com.example.kaspawallet.ui.MainTab
import com.example.kaspawallet.ui.WalletUiState
import com.example.kaspawallet.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OverviewTab(
    state: WalletUiState,
    viewModel: KaspaViewModel
) {
    val context = LocalContext.current
    val activeAcc = state.activeAccount
    val kasBalance = activeAcc?.let { KaspaUtils.sompiToKas(it.balanceSompi) } ?: 0.0
    val fiatValue = KaspaUtils.formatCurrency(kasBalance, state.marketInfo.priceUsd, state.selectedCurrency)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 0.dp, bottom = 24.dp)
    ) {
        // Balance Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    KaspaSurfaceElevated,
                                    KaspaSurfaceVariant,
                                    KaspaSurface
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        // Large Balance Display
                        Text(
                            KaspaUtils.formatKas(kasBalance),
                            color = KaspaTextPrimary,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "≈ $fiatValue ${state.selectedCurrency}",
                                color = KaspaPrimaryGlow,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "• ${activeAcc?.balanceSompi ?: 0L} Sompi",
                                color = KaspaTextMuted,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Address Pill
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(KaspaBackground.copy(alpha = 0.6f))
                                .noRippleClickable {
                                    activeAcc?.address?.let { addr ->
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Kaspa Address", addr))
                                        Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    Icons.Outlined.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = KaspaPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    KaspaUtils.truncateAddress(activeAcc?.address ?: "", 16, 10),
                                    color = KaspaTextSecondary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy address",
                                tint = KaspaPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Quick Actions Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ActionButton(
                    icon = Icons.Default.ArrowOutward,
                    label = "Send",
                    color = KaspaPrimary,
                    modifier = Modifier.weight(1f)
                ) {
                    viewModel.setShowSendDialog(true)
                }

                ActionButton(
                    icon = Icons.Default.QrCode,
                    label = "Receive",
                    color = KaspaPrimaryGlow,
                    modifier = Modifier.weight(1f)
                ) {
                    viewModel.setShowReceiveDialog(true)
                }

                if (state.accounts.size > 1) {
                    ActionButton(
                        icon = Icons.Default.SwapHoriz,
                        label = "Transfer",
                        color = Color(0xFF64B5F6),
                        modifier = Modifier.weight(1f)
                    ) {
                        viewModel.setShowTransferDialog(true)
                    }
                }

                ActionButton(
                    icon = Icons.Default.Compress,
                    label = "Compound",
                    color = Color(0xFFFFFFFF),
                    modifier = Modifier.weight(1f)
                ) {
                    val fragmentActivity = context as? FragmentActivity
                    if (fragmentActivity != null && BiometricAuthManager.isBiometricAvailable(context)) {
                        BiometricAuthManager.promptBiometric(
                            activity = fragmentActivity,
                            title = "Authorize UTXO Consolidation",
                            subtitle = "Scan fingerprint/face to confirm UTXO compound transaction",
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
                }
            }
        }

        // Live Kaspa Market Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = KaspaSurface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("KAS Price", color = KaspaTextSecondary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            val isPositive = state.marketInfo.change24hPercent >= 0
                            Text(
                                "${if (isPositive) "+" else ""}${String.format(java.util.Locale.US, "%.2f", state.marketInfo.change24hPercent)}%",
                                color = if (isPositive) KaspaSuccess else KaspaError,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "$${String.format(java.util.Locale.US, "%.4f", state.marketInfo.priceUsd)}",
                            color = KaspaTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Image(
                        painter = painterResource(id = R.drawable.ic_kaspa_round_logo),
                        contentDescription = "Kaspa Logo",
                        modifier = Modifier.size(42.dp)
                    )
                }
            }
        }

        // Recent Activity Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Recent Activity",
                    color = KaspaTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                if (state.transactions.isNotEmpty()) {
                    TextButton(onClick = { viewModel.selectTab(MainTab.TRANSACTIONS) }) {
                        Text("View All", color = KaspaPrimary, fontSize = 13.sp)
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = KaspaPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // Transactions List or Empty State
        if (state.transactions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = KaspaSurfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.ReceiptLong,
                            contentDescription = null,
                            tint = KaspaTextMuted,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("No Transactions Yet", color = KaspaTextPrimary, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Transactions made with this wallet will appear here in real-time.",
                            color = KaspaTextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        } else {
            items(state.transactions.take(5)) { tx ->
                TransactionItemRow(tx = tx)
            }
        }
    }
}

@Composable
fun ActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.noRippleClickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = KaspaSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(label, color = KaspaTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TransactionItemRow(tx: com.example.kaspawallet.data.model.TransactionEntity) {
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
        TransactionType.SEND -> "Sent Kaspa"
        TransactionType.RECEIVE -> "Received Kaspa"
        TransactionType.TRANSFER -> "Internal Transfer"
        TransactionType.COMPOUND -> "Compounded UTXOs"
    }

    val dateStr = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(tx.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = KaspaSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(iconColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(title, color = KaspaTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(dateStr, color = KaspaTextMuted, fontSize = 11.sp)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                val prefix = if (isIncoming) "+" else if (isCompound) "" else "-"
                val amountKas = KaspaUtils.sompiToKas(tx.amountSompi)
                Text(
                    "$prefix${KaspaUtils.formatKas(amountKas)}",
                    color = if (isIncoming) KaspaSuccess else KaspaTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "DAA #${tx.daaScore}",
                    color = KaspaTextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
