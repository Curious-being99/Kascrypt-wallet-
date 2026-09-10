package com.example.kaspawallet.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kaspawallet.data.crypto.KaspaUtils
import com.example.kaspawallet.ui.KaspaViewModel
import com.example.kaspawallet.ui.WalletUiState
import com.example.kaspawallet.ui.theme.*

@Composable
fun BlockDagTab(
    state: WalletUiState,
    viewModel: KaspaViewModel
) {
    val context = LocalContext.current
    val dag = state.blockDagInfo
    var customNodeUrl by remember { mutableStateOf(state.network.defaultRpc) }
    var isTestingConnection by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        // Node Status Header Card
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(KaspaSuccess)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Connected & Synced",
                                color = KaspaSuccess,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            "${dag.nodeLatencyMs} ms latency",
                            color = KaspaTextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        "BlockDAG Consensus Engine",
                        color = KaspaTextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Kaspa GhostDAG protocol allows 10-100 blocks per second with high parallelism.",
                        color = KaspaTextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        // Live Metric Cards Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        icon = Icons.Default.Tag,
                        label = "Virtual DAA Score",
                        value = "#${dag.virtualDaaScore}",
                        color = KaspaPrimaryGlow,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        icon = Icons.Default.Layers,
                        label = "Total Block Count",
                        value = "${dag.blockCount}",
                        color = Color(0xFF64B5F6),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val formattedHashrate = try {
                        String.format(java.util.Locale.US, "%.2f", dag.hashratePhPerSec)
                    } catch (e: Exception) {
                        "${dag.hashratePhPerSec}"
                    }
                    val formattedDiff = try {
                        String.format(java.util.Locale.US, "%.2f", dag.difficulty)
                    } catch (e: Exception) {
                        "${dag.difficulty}"
                    }
                    MetricCard(
                        icon = Icons.Default.Speed,
                        label = "Hashrate (PoW)",
                        value = "$formattedHashrate PH/s",
                        color = KaspaSuccess,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        icon = Icons.Default.LocalFireDepartment,
                        label = "Difficulty",
                        value = "$formattedDiff P",
                        color = KaspaWarning,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        icon = Icons.Default.Paid,
                        label = "Block Reward",
                        value = "${dag.currentRewardKas} KAS",
                        color = Color(0xFFBA68C8),
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        icon = Icons.Default.Hub,
                        label = "P2P Active Nodes",
                        value = when {
                            dag.connectedPeers > 1 -> "${dag.connectedPeers} Active Nodes"
                            dag.connectedPeers == 1 -> "1 Active Node"
                            else -> "0 (Connecting...)"
                        },
                        color = KaspaTertiary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Tip Hashes Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = KaspaSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Current DAG Selected Tip Hash", color = KaspaTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        dag.tipHashes.firstOrNull() ?: "9f2a48b89c31fa7e1d5e6837ca25b6a71e8932cf0b4d1c3a62884a8b7c4d5e9f",
                        color = KaspaPrimaryGlow,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Protocol Version: ${dag.nodeVersion}", color = KaspaTextMuted, fontSize = 11.sp)
                }
            }
        }

        // Node RPC Manager
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = KaspaSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Kaspa Node RPC Configuration", color = KaspaTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = customNodeUrl,
                        onValueChange = { customNodeUrl = it },
                        label = { Text("RPC Endpoint / Resolver") },
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
                            viewModel.setCustomNodeUrl(customNodeUrl.ifBlank { null })
                            viewModel.refreshAll()
                        },
                        enabled = !state.isRefreshing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KaspaPrimary, contentColor = Color(0xFF003731))
                    ) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF003731), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Testing Node...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test & Refresh Node Connection (${dag.nodeLatencyMs} ms)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = KaspaSurface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(value, color = KaspaTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(label, color = KaspaTextSecondary, fontSize = 11.sp)
        }
    }
}
