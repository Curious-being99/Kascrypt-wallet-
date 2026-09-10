package com.example.kaspawallet.data.crypto

import org.bouncycastle.crypto.params.ParametersWithRandom
import org.bouncycastle.pqc.crypto.mldsa.*
import java.security.SecureRandom

/**
 * NIST FIPS 204 Post-Quantum Digital Signature Standard (ML-DSA-65) Implementation.
 *
 * Parameters (ML-DSA-65 / NIST Security Category 3):
 * - Modulus q = 8,380,417
 * - Polynomial Degree n = 256
 * - Matrix Dimensions: k = 6, l = 5
 * - Noise bound eta = 4
 * - Gamma1 = 2^19 (524,288), Gamma2 = (q - 1)/32 (261,888)
 * - Public Key Size: 1,952 bytes
 * - Secret Key Size: 4,032 bytes (or 32-byte master seed xi)
 * - Signature Size: 3,309 bytes
 */
object KaspaMlDsa {

    const val ALGORITHM_NAME = "ML-DSA-65 (NIST FIPS 204)"
    const val PUBLIC_KEY_BYTES = 1952
    const val PRIVATE_KEY_BYTES = 4032
    const val SIGNATURE_BYTES = 3309
    const val SEED_BYTES = 32

    data class MlDsaKeyPair(
        val publicKeyBytes: ByteArray,
        val privateKeyBytes: ByteArray,
        val seedBytes: ByteArray? = null,
        val publicKeyHex: String,
        val privateKeyHex: String,
        val algorithm: String = ALGORITHM_NAME,
        val isDerivedFromWallet: Boolean = false,
        val seedDigestHex: String? = null
    ) {
        val publicKeyPreview: String
            get() = if (publicKeyHex.length > 24) "${publicKeyHex.take(12)}...${publicKeyHex.takeLast(12)} ($PUBLIC_KEY_BYTES bytes)" else publicKeyHex

        val privateKeyPreview: String
            get() = if (privateKeyHex.length > 24) "${privateKeyHex.take(12)}...${privateKeyHex.takeLast(12)} ($PRIVATE_KEY_BYTES bytes)" else privateKeyHex

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as MlDsaKeyPair
            return publicKeyBytes.contentEquals(other.publicKeyBytes) &&
                    privateKeyBytes.contentEquals(other.privateKeyBytes)
        }

        override fun hashCode(): Int {
            var result = publicKeyBytes.contentHashCode()
            result = 31 * result + privateKeyBytes.contentHashCode()
            return result
        }
    }

    data class MlDsaSignatureResult(
        val signatureBytes: ByteArray,
        val signatureHex: String,
        val messageBytes: ByteArray,
        val isHedged: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        val signaturePreview: String
            get() = if (signatureHex.length > 32) "${signatureHex.take(16)}...${signatureHex.takeLast(16)} ($SIGNATURE_BYTES bytes)" else signatureHex

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as MlDsaSignatureResult
            return signatureBytes.contentEquals(other.signatureBytes) &&
                    messageBytes.contentEquals(other.messageBytes)
        }

        override fun hashCode(): Int {
            var result = signatureBytes.contentHashCode()
            result = 31 * result + messageBytes.contentHashCode()
            return result
        }
    }

    data class MlDsaVerificationResult(
        val isValid: Boolean,
        val message: String,
        val details: String,
        val signatureBytesLength: Int,
        val publicKeyBytesLength: Int
    )

    data class DualHybridSignature(
        val payloadHex: String,
        val schnorrSignatureHex: String,
        val schnorrPublicKeyHex: String,
        val mlDsaSignatureHex: String,
        val mlDsaPublicKeyHex: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class DualHybridVerificationResult(
        val isBothValid: Boolean,
        val isSchnorrValid: Boolean,
        val isMlDsaValid: Boolean,
        val message: String
    )

    /**
     * Generates a fresh random ML-DSA-65 keypair using CSPRNG.
     */
    fun generateRandomKeyPair(): MlDsaKeyPair {
        val seed32 = ByteArray(SEED_BYTES)
        SecureRandom().nextBytes(seed32)
        return deriveKeyPairFromSeed(seed32).copy(isDerivedFromWallet = false)
    }

    /**
     * Deterministically derives an ML-DSA-65 keypair from a 32-byte master seed.
     * Conforms to FIPS 204 Section 5.1 (Algorithm 1 ML-DSA.KeyGen).
     */
    fun deriveKeyPairFromSeed(seed32: ByteArray): MlDsaKeyPair {
        require(seed32.size == SEED_BYTES) { "Seed must be exactly 32 bytes for ML-DSA-65 generation" }
        val privKeyParams = MLDSAPrivateKeyParameters(MLDSAParameters.ml_dsa_65, seed32)
        val pubKeyParams = privKeyParams.publicKeyParameters

        val pubBytes = pubKeyParams.encoded
        val privBytes = privKeyParams.encoded

        return MlDsaKeyPair(
            publicKeyBytes = pubBytes,
            privateKeyBytes = privBytes,
            seedBytes = seed32,
            publicKeyHex = KaspaSigner.byteArrayToHexString(pubBytes),
            privateKeyHex = KaspaSigner.byteArrayToHexString(privBytes),
            isDerivedFromWallet = true,
            seedDigestHex = KaspaSigner.byteArrayToHexString(seed32)
        )
    }

    /**
     * Deterministically derives an ML-DSA-65 keypair from a BIP-39 mnemonic phrase.
     * Uses Blake2b keyed domain derivation to generate the 32-byte master seed.
     */
    fun deriveKeyPairFromMnemonic(mnemonicWords: List<String>, accountIndex: Int = 0): MlDsaKeyPair {
        val bip39Seed = KaspaCrypto.mnemonicToSeed(mnemonicWords)
        val blake = Blake2b(digestSize = 32)
        val domain = "Kaspa_FIPS204_ML_DSA_65_Account_$accountIndex".toByteArray(Charsets.UTF_8)
        blake.update(domain, 0, domain.size)
        blake.update(bip39Seed, 0, bip39Seed.size)
        val derivedSeed = blake.finalize()
        return deriveKeyPairFromSeed(derivedSeed)
    }

    /**
     * Signs a message using NIST FIPS 204 ML-DSA-65 from an MlDsaKeyPair.
     */
    fun sign(
        keyPair: MlDsaKeyPair,
        message: ByteArray,
        hedged: Boolean = true
    ): MlDsaSignatureResult {
        return signInternal(
            privateKeyBytes = keyPair.privateKeyBytes,
            publicKeyBytes = keyPair.publicKeyBytes,
            seedBytes = keyPair.seedBytes,
            message = message,
            hedged = hedged
        )
    }

    /**
     * Signs a message using NIST FIPS 204 ML-DSA-65.
     * Supports both 32-byte master seed and 4,032-byte full secret key.
     */
    fun sign(
        privateKeyBytes: ByteArray,
        publicKeyBytes: ByteArray? = null,
        message: ByteArray,
        hedged: Boolean = true
    ): MlDsaSignatureResult {
        return signInternal(
            privateKeyBytes = privateKeyBytes,
            publicKeyBytes = publicKeyBytes,
            seedBytes = if (privateKeyBytes.size == SEED_BYTES) privateKeyBytes else null,
            message = message,
            hedged = hedged
        )
    }

    private fun signInternal(
        privateKeyBytes: ByteArray,
        publicKeyBytes: ByteArray?,
        seedBytes: ByteArray?,
        message: ByteArray,
        hedged: Boolean
    ): MlDsaSignatureResult {
        val privKeyParams = when {
            seedBytes != null && seedBytes.size == SEED_BYTES -> {
                MLDSAPrivateKeyParameters(MLDSAParameters.ml_dsa_65, seedBytes)
            }
            privateKeyBytes.size == SEED_BYTES -> {
                MLDSAPrivateKeyParameters(MLDSAParameters.ml_dsa_65, privateKeyBytes)
            }
            publicKeyBytes != null && publicKeyBytes.size == PUBLIC_KEY_BYTES -> {
                val pubParams = MLDSAPublicKeyParameters(MLDSAParameters.ml_dsa_65, publicKeyBytes)
                MLDSAPrivateKeyParameters(MLDSAParameters.ml_dsa_65, privateKeyBytes, pubParams)
            }
            else -> {
                throw IllegalArgumentException(
                    "ML-DSA-65 signing requires either the 32-byte seed or the companion 1,952-byte public key."
                )
            }
        }

        val signer = MLDSASigner()
        if (hedged) {
            signer.init(true, ParametersWithRandom(privKeyParams, SecureRandom()))
        } else {
            signer.init(true, privKeyParams)
        }

        signer.update(message, 0, message.size)
        val signatureBytes = signer.generateSignature()

        return MlDsaSignatureResult(
            signatureBytes = signatureBytes,
            signatureHex = KaspaSigner.byteArrayToHexString(signatureBytes),
            messageBytes = message,
            isHedged = hedged
        )
    }

    /**
     * Verifies an ML-DSA-65 signature according to NIST FIPS 204.
     * Checks bounds, hint weight constraints, and SHAKE-256 challenge consistency.
     */
    fun verify(
        publicKeyBytes: ByteArray,
        message: ByteArray,
        signatureBytes: ByteArray
    ): MlDsaVerificationResult {
        if (publicKeyBytes.size != PUBLIC_KEY_BYTES) {
            return MlDsaVerificationResult(
                isValid = false,
                message = "Invalid public key length",
                details = "Expected $PUBLIC_KEY_BYTES bytes (FIPS 204 ML-DSA-65), got ${publicKeyBytes.size}",
                signatureBytesLength = signatureBytes.size,
                publicKeyBytesLength = publicKeyBytes.size
            )
        }

        if (signatureBytes.size != SIGNATURE_BYTES) {
            return MlDsaVerificationResult(
                isValid = false,
                message = "Invalid signature length",
                details = "Expected $SIGNATURE_BYTES bytes (FIPS 204 ML-DSA-65), got ${signatureBytes.size}",
                signatureBytesLength = signatureBytes.size,
                publicKeyBytesLength = publicKeyBytes.size
            )
        }

        return try {
            val pubKeyParams = MLDSAPublicKeyParameters(MLDSAParameters.ml_dsa_65, publicKeyBytes)
            val verifier = MLDSASigner()
            verifier.init(false, pubKeyParams)
            verifier.update(message, 0, message.size)
            val valid = verifier.verifySignature(signatureBytes)

            if (valid) {
                MlDsaVerificationResult(
                    isValid = true,
                    message = "Signature Authenticated Successfully",
                    details = "NIST FIPS 204 ML-DSA-65 mathematical verification verified. Ring R_q (q=8380417, n=256, k=6, l=5) bounds satisfied.",
                    signatureBytesLength = signatureBytes.size,
                    publicKeyBytesLength = publicKeyBytes.size
                )
            } else {
                MlDsaVerificationResult(
                    isValid = false,
                    message = "Signature Verification Failed",
                    details = "The signature or message has been modified, or the public key does not correspond to the signer.",
                    signatureBytesLength = signatureBytes.size,
                    publicKeyBytesLength = publicKeyBytes.size
                )
            }
        } catch (e: Exception) {
            MlDsaVerificationResult(
                isValid = false,
                message = "Verification Error: ${e.message}",
                details = e.localizedMessage ?: "Cryptographic error encountered during verification",
                signatureBytesLength = signatureBytes.size,
                publicKeyBytesLength = publicKeyBytes.size
            )
        }
    }

    /**
     * Creates a composite / hybrid signature:
     * - BIP-340 Schnorr (Classical Secp256k1) 64-byte signature
     * - NIST FIPS 204 ML-DSA-65 (Post-Quantum) 3,309-byte signature
     */
    fun createDualHybridSignature(
        schnorrPrivateKey: ByteArray,
        schnorrPublicKey: ByteArray,
        mlDsaKeyPair: MlDsaKeyPair,
        payload: ByteArray,
        hedgedMlDsa: Boolean = true
    ): DualHybridSignature {
        // Hash payload with Blake2b
        val blake = Blake2b(digestSize = 32)
        blake.update(payload, 0, payload.size)
        val payloadHash = blake.finalize()

        // 1. Classical Schnorr signature (64 bytes)
        val schnorrSig = KaspaSigner.signSchnorr(schnorrPrivateKey, payloadHash)

        // 2. Post-Quantum ML-DSA-65 signature (3,309 bytes)
        val mlDsaSigResult = sign(mlDsaKeyPair, payload, hedged = hedgedMlDsa)

        return DualHybridSignature(
            payloadHex = KaspaSigner.byteArrayToHexString(payload),
            schnorrSignatureHex = KaspaSigner.byteArrayToHexString(schnorrSig),
            schnorrPublicKeyHex = KaspaSigner.byteArrayToHexString(schnorrPublicKey),
            mlDsaSignatureHex = mlDsaSigResult.signatureHex,
            mlDsaPublicKeyHex = mlDsaKeyPair.publicKeyHex
        )
    }

    /**
     * Verifies both layers of a Dual Hybrid Signature.
     */
    fun verifyDualHybridSignature(
        schnorrPublicKey: ByteArray,
        mlDsaPublicKey: ByteArray,
        payload: ByteArray,
        schnorrSignature: ByteArray,
        mlDsaSignature: ByteArray
    ): DualHybridVerificationResult {
        // 1. Verify Schnorr
        val blake = Blake2b(digestSize = 32)
        blake.update(payload, 0, payload.size)
        val payloadHash = blake.finalize()

        val isSchnorrValid = try {
            KaspaSigner.verifySchnorr(schnorrPublicKey, payloadHash, schnorrSignature)
        } catch (_: Exception) {
            false
        }

        // 2. Verify ML-DSA-65
        val mlDsaResult = verify(mlDsaPublicKey, payload, mlDsaSignature)

        val isBothValid = isSchnorrValid && mlDsaResult.isValid
        val message = when {
            isBothValid -> "Both Classical (BIP-340 Schnorr) and Post-Quantum (ML-DSA-65) signatures are valid! Maximum quantum resilience achieved."
            !isSchnorrValid && !mlDsaResult.isValid -> "Both signatures failed verification."
            !isSchnorrValid -> "ML-DSA-65 Post-Quantum passed, but BIP-340 Schnorr failed."
            else -> "BIP-340 Schnorr passed, but ML-DSA-65 Post-Quantum failed."
        }

        return DualHybridVerificationResult(
            isBothValid = isBothValid,
            isSchnorrValid = isSchnorrValid,
            isMlDsaValid = mlDsaResult.isValid,
            message = message
        )
    }
}
