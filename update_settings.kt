    if (showDeleteConfirm) {
        val isBiometricAvailable = remember { BiometricAuthManager.isBiometricAvailable(context) }
        val fragmentActivity = context as? FragmentActivity
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { 
                showDeleteConfirm = false 
                deletePasswordInput = ""
            },
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
                                .background(KaspaError.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = KaspaError, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Delete Wallet", color = KaspaTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Remove from device", color = KaspaTextSecondary, fontSize = 12.sp)
                        }
                    }
                    IconButton(
                        onClick = { 
                            showDeleteConfirm = false 
                            deletePasswordInput = ""
                        }, 
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = KaspaTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Are you sure you want to remove '${state.activeWallet?.name}'? Make sure you have backed up your recovery seed phrase.\n\nPlease authenticate to confirm wallet deletion.",
                    color = KaspaTextPrimary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = deletePasswordInput,
                    onValueChange = { deletePasswordInput = it },
                    label = { Text("Wallet Password") },
                    placeholder = { Text("Enter password to confirm") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (isDeletePasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isDeletePasswordVisible = !isDeletePasswordVisible }) {
                            Icon(
                                imageVector = if (isDeletePasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle password visibility",
                                tint = KaspaTextSecondary
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

                if (isBiometricAvailable) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (fragmentActivity != null) {
                                BiometricAuthManager.promptBiometric(
                                    activity = fragmentActivity,
                                    title = "Confirm Wallet Deletion",
                                    subtitle = "Authenticate to delete the wallet securely",
                                    onSuccess = {
                                        viewModel.deleteCurrentWallet(context)
                                        showDeleteConfirm = false
                                        deletePasswordInput = ""
                                        Toast.makeText(context, "Wallet deleted", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, "Authentication failed: $err", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = KaspaSurfaceVariant, contentColor = KaspaPrimaryGlow),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Confirm with Biometrics", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Button(
                    onClick = {
                        val walletId = state.activeWallet?.id ?: ""
                        if (viewModel.verifyWalletPassword(context, walletId, deletePasswordInput)) {
                            viewModel.deleteCurrentWallet(context)
                            showDeleteConfirm = false
                            deletePasswordInput = ""
                            Toast.makeText(context, "Wallet deleted", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Incorrect password", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = deletePasswordInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KaspaError, contentColor = Color.White)
                ) {
                    Text("Delete Wallet", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
