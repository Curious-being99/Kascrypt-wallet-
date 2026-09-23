package com.example.kaspawallet.data.crypto

import com.example.kaspawallet.data.model.KaspaNetwork
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.SecureRandom
import java.text.DecimalFormat

object KaspaUtils {
    const val SOMPI_PER_KAS = 100_000_000L // 10^8 sompi = 1 KAS
    const val DEFAULT_MIN_FEE_SOMPI = 386_000L // 0.00386 KAS
    const val PRIORITY_FEE_SOMPI = 400_000L // 0.00400 KAS
    const val HIGH_PRIORITY_FEE_SOMPI = 486_000L // 0.00486 KAS

    fun generateMnemonic(wordCount: Int = 12): List<String> {
        return Bip39WordList.generateMnemonic(wordCount)
    }

    fun validateMnemonic(mnemonicWords: List<String>): Boolean {
        return Bip39WordList.validateMnemonic(mnemonicWords)
    }

    /**
     * Ensures an address is properly formatted with the active network prefix (kaspa, kaspatest, etc.)
     */
    fun formatAddressForNetwork(address: String, network: KaspaNetwork): String {
        if (address.isBlank()) return ""
        val targetPrefix = when (network) {
            KaspaNetwork.MAINNET -> "kaspa"
            KaspaNetwork.TESTNET_10, KaspaNetwork.TESTNET_11 -> "kaspatest"
            KaspaNetwork.DEVNET -> "kaspadev"
            KaspaNetwork.SIMNET -> "kaspasim"
        }
        if (address.startsWith("$targetPrefix:")) return address
        return KaspaCrypto.convertAddressPrefix(address, targetPrefix)
    }

    fun generateDeterministicAddress(
        mnemonicWords: List<String>,
        accountIndex: Int,
        network: KaspaNetwork,
        addressIndex: Int = 0,
        passphrase: String = ""
    ): String {
        return KaspaCrypto.deriveKaspaAddress(
            mnemonic = mnemonicWords,
            accountIndex = accountIndex,
            addressIndex = addressIndex,
            network = network,
            passphrase = passphrase
        )
    }

    fun generateDeterministicChangeAddress(
        mnemonicWords: List<String>,
        accountIndex: Int,
        network: KaspaNetwork,
        addressIndex: Int = 0,
        passphrase: String = ""
    ): String {
        return KaspaCrypto.deriveKaspaChangeAddress(
            mnemonic = mnemonicWords,
            accountIndex = accountIndex,
            addressIndex = addressIndex,
            network = network,
            passphrase = passphrase
        )
    }

    fun generateTransactionId(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun generateTxId(): String = generateTransactionId()

    fun buildTransactionJson(
        senderAddress: String,
        recipientAddress: String,
        amountSompi: Long,
        feeSompi: Long
    ): String {
        return """
        {
            "transaction": {
                "version": 0,
                "inputs": [],
                "outputs": [
                    {
                        "amount": $amountSompi,
                        "scriptPublicKey": {
                            "version": 0,
                            "scriptPublicKey": "$recipientAddress"
                        }
                    }
                ],
                "lockTime": 0,
                "subnetworkId": "0000000000000000000000000000000000000000",
                "gas": 0,
                "payload": "",
                "mass": 200
            }
        }
        """.trimIndent()
    }

    fun sompiToKas(sompi: Long): Double {
        return sompi.toDouble() / SOMPI_PER_KAS
    }

    fun kasToSompi(kas: Double): Long {
        return try {
            BigDecimal.valueOf(kas)
                .multiply(BigDecimal.valueOf(SOMPI_PER_KAS))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        } catch (e: Exception) {
            (kas * SOMPI_PER_KAS).toLong()
        }
    }

    fun formatKas(kas: Double): String {
        val df = DecimalFormat("#,##0.00######")
        return "${df.format(kas)} KAS"
    }

    fun formatKasOnly(kas: Double): String {
        val df = DecimalFormat("#,##0.00######")
        return df.format(kas)
    }

    fun formatSompi(sompi: Long): String {
        val kas = sompiToKas(sompi)
        return formatKas(kas)
    }

    fun formatCurrency(kasAmount: Double, priceUsd: Double, currency: String = "USD"): String {
        val value = kasAmount * priceUsd
        val df = DecimalFormat("#,##0.00")
        val symbol = when (currency) {
            "EUR" -> "€"
            "GBP" -> "£"
            "JPY" -> "¥"
            "CAD" -> "CA$"
            "AUD" -> "A$"
            "CNY" -> "¥"
            else -> "$"
        }
        return "$symbol${df.format(value)}"
    }

    fun truncateAddress(address: String, leadingChars: Int = 12, trailingChars: Int = 8): String {
        if (address.length <= (leadingChars + trailingChars + 3)) return address
        return "${address.take(leadingChars)}...${address.takeLast(trailingChars)}"
    }

    fun isValidKaspaAddress(address: String, network: KaspaNetwork? = null): Boolean {
        val trimmed = address.trim().lowercase()
        if (trimmed.length < 30 || trimmed.length > 95) return false
        val validPrefixes = if (network != null) {
            listOf(network.prefix.lowercase())
        } else {
            listOf("kaspa:", "kaspatest:", "kaspadev:", "kaspasim:")
        }
        if (!validPrefixes.any { trimmed.startsWith(it) }) return false
        return KaspaCrypto.verifyKaspaAddress(trimmed)
    }

    enum class AddressValidationState {
        EMPTY,
        VALID,
        VALID_OTHER_NETWORK,
        INVALID
    }

    data class AddressValidationResult(
        val state: AddressValidationState,
        val normalizedAddress: String,
        val message: String,
        val detectedNetwork: KaspaNetwork? = null
    )

    fun validateKaspaAddress(input: String, currentNetwork: KaspaNetwork): AddressValidationResult {
        val trimmed = input.trim().lowercase()
        if (trimmed.isEmpty()) {
            return AddressValidationResult(
                state = AddressValidationState.EMPTY,
                normalizedAddress = "",
                message = "Enter recipient Kaspa address"
            )
        }

        // Check if user entered address with a prefix
        if (trimmed.contains(":")) {
            if (isValidKaspaAddress(trimmed, currentNetwork)) {
                return AddressValidationResult(
                    state = AddressValidationState.VALID,
                    normalizedAddress = trimmed,
                    message = "Valid ${currentNetwork.displayName} address",
                    detectedNetwork = currentNetwork
                )
            }

            // Check if it belongs to another Kaspa network
            for (net in KaspaNetwork.values()) {
                if (net != currentNetwork && isValidKaspaAddress(trimmed, net)) {
                    return AddressValidationResult(
                        state = AddressValidationState.VALID_OTHER_NETWORK,
                        normalizedAddress = trimmed,
                        message = "Valid ${net.displayName} address, but wallet is on ${currentNetwork.displayName}",
                        detectedNetwork = net
                    )
                }
            }

            return AddressValidationResult(
                state = AddressValidationState.INVALID,
                normalizedAddress = trimmed,
                message = "Invalid Kaspa address checksum or format"
            )
        }

        // If user omitted prefix (e.g. "qq..." or "qr...")
        val withPrefix = "${currentNetwork.prefix.lowercase()}$trimmed"
        if (isValidKaspaAddress(withPrefix, currentNetwork)) {
            return AddressValidationResult(
                state = AddressValidationState.VALID,
                normalizedAddress = withPrefix,
                message = "Valid ${currentNetwork.displayName} address",
                detectedNetwork = currentNetwork
            )
        }

        for (net in KaspaNetwork.values()) {
            if (net != currentNetwork) {
                val candidate = "${net.prefix.lowercase()}$trimmed"
                if (isValidKaspaAddress(candidate, net)) {
                    return AddressValidationResult(
                        state = AddressValidationState.VALID_OTHER_NETWORK,
                        normalizedAddress = candidate,
                        message = "Valid ${net.displayName} address payload",
                        detectedNetwork = net
                    )
                }
            }
        }

        return AddressValidationResult(
            state = AddressValidationState.INVALID,
            normalizedAddress = trimmed,
            message = "Invalid Kaspa address format (e.g. ${currentNetwork.prefix}qq...)"
        )
    }
}
