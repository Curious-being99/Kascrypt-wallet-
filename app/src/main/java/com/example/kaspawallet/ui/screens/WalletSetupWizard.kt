package com.example.kaspawallet.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.example.kaspawallet.ui.theme.noRippleClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kaspawallet.data.crypto.Bip39WordList
import com.example.kaspawallet.data.crypto.KaspaUtils
import com.example.kaspawallet.data.model.KaspaNetwork
import com.example.kaspawallet.ui.KaspaViewModel
import com.example.kaspawallet.ui.theme.*
import kotlin.random.Random

enum class SetupStep {
    CONFIG,
    RECOVERY_PHRASE,
    VERIFY_QUIZ,
    IMPORT_PHRASE,
    SCAN_INDEXING
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletSetupWizard(
    viewModel: KaspaViewModel,
    initialMode: String = "CREATE",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember {
        mutableStateOf(if (initialMode == "RESCAN") SetupStep.SCAN_INDEXING else SetupStep.CONFIG)
    }
    var isImportMode by remember { mutableStateOf(initialMode == "IMPORT" || initialMode == "RESCAN") }

    // Configuration State
    var walletName by remember { mutableStateOf("Kaspa Primary Wallet") }
    var selectedNetwork by remember { mutableStateOf(KaspaNetwork.MAINNET) }
    var wordCount by remember { mutableIntStateOf(12) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var hasAdvancedPassphrase by remember { mutableStateOf(false) }
    var bip39Passphrase by remember { mutableStateOf("") }

    // Generated Seed State
    var generatedWords by remember { mutableStateOf(KaspaUtils.generateMnemonic(12)) }
    var isSeedHidden by remember { mutableStateOf(false) }
    var hasConfirmedBackup by remember { mutableStateOf(false) }

    // Quiz Verification State
    var quizIndices by remember { mutableStateOf(listOf(2, 6, 10)) }
    var quizAnswers by remember { mutableStateOf(mutableMapOf<Int, String>()) }

    // Import State
    var importText by remember { mutableStateOf("") }
    var importWords by remember { mutableStateOf(List(12) { "" }) }
    var activeWordInputIndex by remember { mutableIntStateOf(0) }

    fun refreshQuiz() {
        val total = generatedWords.size
        val randomIndices = (0 until total).shuffled().take(3).sorted()
        quizIndices = randomIndices
        quizAnswers = mutableMapOf()
    }

    LaunchedEffect(wordCount) {
        if (!isImportMode) {
            generatedWords = KaspaUtils.generateMnemonic(wordCount)
            refreshQuiz()
        } else {
            importWords = List(wordCount) { "" }
        }
    }

    if (currentStep == SetupStep.SCAN_INDEXING) {
        val scanState by viewModel.scanIndexingState.collectAsState()
        val uiState by viewModel.uiState.collectAsState()
        ScanIndexingScreen(
            state = scanState,
            marketInfo = uiState.marketInfo,
            selectedCurrency = uiState.selectedCurrency,
            onContinue = {
                viewModel.finishScanAndNavigateToWallet()
            },
            onRetry = {
                val words = if (isImportMode) {
                    importText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                } else {
                    generatedWords
                }
                viewModel.startScanAndIndex(
                    context = context,
                    name = walletName.trim(),
                    words = words,
                    hasPassphrase = hasAdvancedPassphrase,
                    passphrase = bip39Passphrase,
                    network = selectedNetwork,
                    password = password,
                    isImport = isImportMode
                )
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (currentStep) {
                            SetupStep.CONFIG -> "Step 1: Configuration & Security"
                            SetupStep.RECOVERY_PHRASE -> "Step 2: Backup Recovery Phrase"
                            SetupStep.VERIFY_QUIZ -> "Step 3: Verify Recovery Phrase"
                            SetupStep.IMPORT_PHRASE -> "Step 2: Enter Seed Phrase"
                            SetupStep.SCAN_INDEXING -> "Step 3: On-Chain Indexing"
                        },
                        color = KaspaTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when (currentStep) {
                                SetupStep.CONFIG -> onDismiss()
                                SetupStep.RECOVERY_PHRASE -> currentStep = SetupStep.CONFIG
                                SetupStep.VERIFY_QUIZ -> currentStep = SetupStep.RECOVERY_PHRASE
                                SetupStep.IMPORT_PHRASE -> currentStep = SetupStep.CONFIG
                                SetupStep.SCAN_INDEXING -> onDismiss()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = KaspaTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = KaspaSurface)
            )
        },
        containerColor = KaspaBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Compact Progress Step Indicator
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val stepsTotal = if (isImportMode) 2 else 3
                    val activeIndex = when (currentStep) {
                        SetupStep.CONFIG -> 1
                        SetupStep.RECOVERY_PHRASE, SetupStep.IMPORT_PHRASE -> 2
                        SetupStep.VERIFY_QUIZ -> 3
                        SetupStep.SCAN_INDEXING -> stepsTotal
                    }

                    for (i in 1..stepsTotal) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (i <= activeIndex) KaspaPrimary else KaspaSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "$i",
                                color = if (i <= activeIndex) Color(0xFF003731) else KaspaTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (i < stepsTotal) {
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(2.dp)
                                    .background(if (i < activeIndex) KaspaPrimary else KaspaCardBorder)
                            )
                        }
                    }
                }

                when (currentStep) {
                    // ==========================================
                    // STEP 1: CONFIGURATION & SECURITY
                    // ==========================================
                    SetupStep.CONFIG -> {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Wallet Name
                            OutlinedTextField(
                                value = walletName,
                                onValueChange = { walletName = it },
                                label = { Text("Wallet Name") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("wallet_name_input"),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = KaspaTextPrimary,
                                    unfocusedTextColor = KaspaTextPrimary,
                                    focusedContainerColor = KaspaSurface,
                                    unfocusedContainerColor = KaspaSurface
                                )
                            )

                            // Kaspa Network Selector (Segmented)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Kaspa Network", color = KaspaTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(KaspaSurface)
                                        .padding(3.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(
                                        KaspaNetwork.MAINNET to "Mainnet",
                                        KaspaNetwork.TESTNET_10 to "Testnet-10",
                                        KaspaNetwork.TESTNET_11 to "Testnet-11"
                                    ).forEach { (net, label) ->
                                        val isSelected = selectedNetwork == net
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) KaspaPrimary else Color.Transparent)
                                                .noRippleClickable { selectedNetwork = net }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                label,
                                                color = if (isSelected) Color(0xFF003731) else KaspaTextSecondary,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }

                            // Recovery Phrase Standard (Segmented)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Phrase Length", color = KaspaTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(KaspaSurface)
                                        .padding(3.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(12 to "12 Words (Standard)", 24 to "24 Words (High Security)").forEach { (count, label) ->
                                        val isSelected = wordCount == count
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) KaspaPrimary else Color.Transparent)
                                                .noRippleClickable { wordCount = count }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                label,
                                                color = if (isSelected) Color(0xFF003731) else KaspaTextSecondary,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }

                            // Password (Required)
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Keystore Password (Required)") },
                                placeholder = { Text("Set wallet encryption password") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("password_input"),
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showPassword = !showPassword }) {
                                        Icon(
                                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle password visibility",
                                            tint = KaspaTextSecondary
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = KaspaTextPrimary,
                                    unfocusedTextColor = KaspaTextPrimary,
                                    focusedContainerColor = KaspaSurface,
                                    unfocusedContainerColor = KaspaSurface
                                )
                            )

                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it },
                                label = { Text("Confirm Keystore Password") },
                                placeholder = { Text("Re-enter keystore password") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                isError = confirmPassword.isNotEmpty() && confirmPassword != password,
                                supportingText = {
                                    if (confirmPassword.isNotEmpty() && confirmPassword != password) {
                                        Text("Passwords do not match", color = KaspaError, fontSize = 11.sp)
                                    } else if (password.isEmpty()) {
                                        Text("Password is required to secure your wallet", color = KaspaWarning, fontSize = 11.sp)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = KaspaTextPrimary,
                                    unfocusedTextColor = KaspaTextPrimary,
                                    focusedContainerColor = KaspaSurface,
                                    unfocusedContainerColor = KaspaSurface
                                )
                            )

                            // Advanced BIP39 Passphrase toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .noRippleClickable { hasAdvancedPassphrase = !hasAdvancedPassphrase }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = hasAdvancedPassphrase,
                                    onCheckedChange = { hasAdvancedPassphrase = it },
                                    colors = CheckboxDefaults.colors(checkedColor = KaspaPrimary)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Column {
                                    Text("Advanced: BIP39 Passphrase (Salt)", color = KaspaTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Text("Extra secret passphrase protection", color = KaspaTextSecondary, fontSize = 10.sp)
                                }
                            }

                            if (hasAdvancedPassphrase) {
                                OutlinedTextField(
                                    value = bip39Passphrase,
                                    onValueChange = { bip39Passphrase = it },
                                    label = { Text("BIP39 Passphrase Salt") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedTextColor = KaspaTextPrimary,
                                        unfocusedTextColor = KaspaTextPrimary,
                                        focusedContainerColor = KaspaSurface,
                                        unfocusedContainerColor = KaspaSurface
                                    )
                                )
                            }
                        }

                        val isConfigValid = walletName.isNotBlank() && password.isNotBlank() && password == confirmPassword

                        Button(
                            onClick = {
                                if (isImportMode) {
                                    currentStep = SetupStep.IMPORT_PHRASE
                                } else {
                                    generatedWords = KaspaUtils.generateMnemonic(wordCount)
                                    refreshQuiz()
                                    currentStep = SetupStep.RECOVERY_PHRASE
                                }
                            },
                            enabled = isConfigValid,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("continue_to_step2_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KaspaPrimary, contentColor = Color(0xFF003731))
                        ) {
                            Text(if (isImportMode) "Continue to Import" else "Generate Recovery Phrase", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    }

                    // ==========================================
                    // STEP 2 (CREATE): RECOVERY PHRASE BACKUP
                    // ==========================================
                    SetupStep.RECOVERY_PHRASE -> {
                        Card(
                                            colors = CardDefaults.cardColors(containerColor = KaspaWarning.copy(alpha = 0.12f)),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = KaspaWarning, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Record Your Secret Recovery Phrase", color = KaspaWarning, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Write down these ${generatedWords.size} words in order and store them offline. If you lose this phrase, nobody can help you recover your Kaspa.",
                                        color = KaspaTextPrimary,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Seed Phrase Grid
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = KaspaSurface),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("BIP39 Seed Phrase (${generatedWords.size} words)", color = KaspaTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    TextButton(onClick = { isSeedHidden = !isSeedHidden }) {
                                        Icon(if (isSeedHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null, tint = KaspaPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (isSeedHidden) "Reveal" else "Hide", color = KaspaPrimary, fontSize = 12.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (generatedWords.size == 24) 340.dp else 190.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    itemsIndexed(generatedWords) { index, word ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = KaspaSurfaceVariant),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("${index + 1}.", color = KaspaTextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    if (isSeedHidden) "•••••" else word,
                                                    color = KaspaTextPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Kaspa Seed", generatedWords.joinToString(" ")))
                                        Toast.makeText(context, "Recovery phrase copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KaspaPrimaryGlow),
                                    border = ButtonDefaults.outlinedButtonBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(KaspaPrimary))
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy Recovery Phrase", fontSize = 13.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Confirmation checkbox
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .noRippleClickable { hasConfirmedBackup = !hasConfirmedBackup }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = hasConfirmedBackup,
                                onCheckedChange = { hasConfirmedBackup = it },
                                colors = CheckboxDefaults.colors(checkedColor = KaspaPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "I have written down all ${generatedWords.size} words in correct sequence.",
                                color = KaspaTextPrimary,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                refreshQuiz()
                                currentStep = SetupStep.VERIFY_QUIZ
                            },
                            enabled = hasConfirmedBackup,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("verify_quiz_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KaspaPrimary, contentColor = Color(0xFF003731))
                        ) {
                            Text("Verify Phrase (Safety Check)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    }

                    // ==========================================
                    // STEP 3 (CREATE): VERIFY QUIZ (Kaspa-NG Safety)
                    // ==========================================
                    SetupStep.VERIFY_QUIZ -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = KaspaSurface),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = KaspaPrimary, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Verify Recovery Phrase", color = KaspaTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }

                                Text(
                                    "To verify your backup, please select the correct words for the indicated positions:",
                                    color = KaspaTextSecondary,
                                    fontSize = 13.sp
                                )

                                quizIndices.forEach { targetIndex ->
                                    val correctWord = generatedWords[targetIndex]
                                    val selectedAnswer = quizAnswers[targetIndex]

                                    // Generate 3 distractors + 1 correct word
                                    val candidateOptions = remember(targetIndex, generatedWords) {
                                        val pool = (Bip39WordList.WORDS - correctWord).shuffled().take(3)
                                        (pool + correctWord).shuffled()
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            "Select Word #${targetIndex + 1}:",
                                            color = KaspaPrimaryGlow,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            candidateOptions.forEach { opt ->
                                                val isSelected = selectedAnswer == opt
                                                val isCorrect = isSelected && opt == correctWord
                                                val isWrong = isSelected && opt != correctWord

                                                Card(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .noRippleClickable {
                                                            quizAnswers = quizAnswers.toMutableMap().apply {
                                                                put(targetIndex, opt)
                                                            }
                                                        },
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = when {
                                                            isCorrect -> KaspaSuccess.copy(alpha = 0.2f)
                                                            isWrong -> KaspaError.copy(alpha = 0.2f)
                                                            isSelected -> KaspaPrimary.copy(alpha = 0.2f)
                                                            else -> KaspaSurfaceVariant
                                                        }
                                                    ),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Box(modifier = Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                                                        Text(
                                                            opt,
                                                            color = when {
                                                                isCorrect -> KaspaSuccess
                                                                isWrong -> KaspaError
                                                                isSelected -> KaspaPrimaryGlow
                                                                else -> KaspaTextPrimary
                                                            },
                                                            fontSize = 12.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        val allAnswered = quizIndices.all { quizAnswers[it] != null }
                        val allCorrect = quizIndices.all { quizAnswers[it] == generatedWords[it] }

                        Button(
                            onClick = {
                                currentStep = SetupStep.SCAN_INDEXING
                                viewModel.startScanAndIndex(
                                    context = context,
                                    name = walletName.trim(),
                                    words = generatedWords,
                                    hasPassphrase = hasAdvancedPassphrase,
                                    passphrase = bip39Passphrase,
                                    network = selectedNetwork,
                                    password = password,
                                    isImport = false
                                )
                            },
                            enabled = allAnswered && allCorrect,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("finalize_create_wallet_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KaspaPrimary, contentColor = Color(0xFF003731))
                        ) {
                            Text("Confirm & Index On-Chain", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    }

                    // ==========================================
                    // STEP 2 (IMPORT): IMPORT EXISTING PHRASE
                    // ==========================================
                    SetupStep.IMPORT_PHRASE -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = KaspaSurface),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Enter Seed Phrase ($wordCount words)", color = KaspaTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    TextButton(onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                                        if (!clip.isNullOrBlank()) {
                                            val parsed = clip.trim().split(Regex("\\s+"))
                                            if (parsed.size == 12 || parsed.size == 24) {
                                                wordCount = parsed.size
                                                importWords = parsed
                                                importText = clip
                                            } else {
                                                importText = clip
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = null, tint = KaspaPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Paste Phrase", color = KaspaPrimary, fontSize = 12.sp)
                                    }
                                }

                                OutlinedTextField(
                                    value = importText,
                                    onValueChange = {
                                        importText = it
                                        val parsed = it.trim().split(Regex("\\s+"))
                                        if (parsed.size == 12 || parsed.size == 24) {
                                            wordCount = parsed.size
                                            importWords = parsed
                                        }
                                    },
                                    label = { Text("Paste or Type full seed phrase") },
                                    placeholder = { Text("word1 word2 word3 ...") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .testTag("import_seed_input"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedTextColor = KaspaTextPrimary,
                                        unfocusedTextColor = KaspaTextPrimary
                                    )
                                )

                                // Word Validation Status
                                val wordsList = importText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                                val allWordsValid = wordsList.size in listOf(12, 24) && wordsList.all { Bip39WordList.isValidWord(it) }

                                if (wordsList.isNotEmpty()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (allWordsValid) Icons.Default.CheckCircle else Icons.Default.Info,
                                            contentDescription = null,
                                            tint = if (allWordsValid) KaspaSuccess else KaspaWarning,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            if (allWordsValid) "Valid ${wordsList.size}-word BIP39 mnemonic" else "${wordsList.size} / $wordCount words entered",
                                            color = if (allWordsValid) KaspaSuccess else KaspaTextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        val wordsList = importText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                        val canImport = wordsList.size in listOf(12, 24) && wordsList.all { Bip39WordList.isValidWord(it) }

                        Button(
                            onClick = {
                                val words = importText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                                currentStep = SetupStep.SCAN_INDEXING
                                viewModel.startScanAndIndex(
                                    context = context,
                                    name = walletName.trim(),
                                    words = words,
                                    hasPassphrase = hasAdvancedPassphrase,
                                    passphrase = bip39Passphrase,
                                    network = selectedNetwork,
                                    password = password,
                                    isImport = true
                                )
                            },
                            enabled = canImport,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("finalize_import_wallet_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KaspaPrimary, contentColor = Color(0xFF003731))
                        ) {
                            Text("Confirm & Index On-Chain", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    }

                    SetupStep.SCAN_INDEXING -> {
                        // Handled above before Scaffold
                    }
                }
            }
        }
    }
}
