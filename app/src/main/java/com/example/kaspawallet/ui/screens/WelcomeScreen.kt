package com.example.kaspawallet.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kaspawallet.R
import com.example.kaspawallet.data.model.KaspaNetwork
import com.example.kaspawallet.ui.KaspaViewModel
import com.example.kaspawallet.ui.theme.*

@Composable
fun WelcomeScreen(
    viewModel: KaspaViewModel,
    onCreateWallet: () -> Unit,
    onImportWallet: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KaspaBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Centered Hero Section
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Official Kaspa Teal >> Chevron Logo
            Image(
                painter = painterResource(id = R.drawable.ic_kaspa_teal_logo),
                contentDescription = "KASCRYPT Logo",
                modifier = Modifier.size(92.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "KASCRYPT",
                color = KaspaTextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Elegant Subtitle Pill Badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = KaspaSurfaceVariant.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, KaspaPrimary.copy(alpha = 0.25f)),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(KaspaPrimaryGlow)
                    )
                    Text(
                        text = "BlockDAG Cryptocurrency Wallet",
                        color = KaspaPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.3.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "The world's fastest, purest Proof-of-Work decentralized wallet with sub-second finality.",
                color = KaspaTextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Bottom Pinned Actions Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onCreateWallet,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KaspaPrimary,
                    contentColor = Color(0xFF003731)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Create New Wallet",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onImportWallet,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = KaspaSurface,
                    contentColor = KaspaPrimaryGlow
                ),
                border = BorderStroke(1.dp, KaspaCardBorder)
            ) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Import from Seed Phrase",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "KASCRYPT • Powered by GHOSTDAG Consensus",
                color = KaspaTextMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                letterSpacing = 0.2.sp
            )
        }
    }
}

