package com.example.kaspawallet.data.crypto

import org.junit.Assert.*
import org.junit.Test

class MlDsaTest {

    @Test
    fun testKaspaMlDsaRandomKeyGenAndSignVerify() {
        val keyPair = KaspaMlDsa.generateRandomKeyPair()
        assertEquals(KaspaMlDsa.PUBLIC_KEY_BYTES, keyPair.publicKeyBytes.size)
        assertEquals(KaspaMlDsa.PRIVATE_KEY_BYTES, keyPair.privateKeyBytes.size)
        assertNotNull(keyPair.seedBytes)
        assertEquals(32, keyPair.seedBytes?.size)

        val message = "NIST FIPS 204 ML-DSA-65 Production Verification Test".toByteArray(Charsets.UTF_8)

        // Test Hedged Signing
        val hedgedSig = KaspaMlDsa.sign(keyPair, message, hedged = true)
        assertEquals(KaspaMlDsa.SIGNATURE_BYTES, hedgedSig.signatureBytes.size)
        assertTrue(hedgedSig.isHedged)

        val verifyHedged = KaspaMlDsa.verify(keyPair.publicKeyBytes, message, hedgedSig.signatureBytes)
        assertTrue(verifyHedged.isValid)

        // Test Deterministic Signing
        val detSig1 = KaspaMlDsa.sign(keyPair, message, hedged = false)
        val detSig2 = KaspaMlDsa.sign(keyPair, message, hedged = false)
        assertFalse(detSig1.isHedged)
        assertArrayEquals(detSig1.signatureBytes, detSig2.signatureBytes)

        val verifyDet = KaspaMlDsa.verify(keyPair.publicKeyBytes, message, detSig1.signatureBytes)
        assertTrue(verifyDet.isValid)

        // Test Tampered Message
        val tamperedMessage = "NIST FIPS 204 ML-DSA-65 Tampered Payload".toByteArray(Charsets.UTF_8)
        val verifyTampered = KaspaMlDsa.verify(keyPair.publicKeyBytes, tamperedMessage, hedgedSig.signatureBytes)
        assertFalse(verifyTampered.isValid)
    }

    @Test
    fun testMnemonicDerivationDeterministic() {
        val mnemonic = listOf(
            "abandon", "abandon", "abandon", "abandon", "abandon", "abandon",
            "abandon", "abandon", "abandon", "abandon", "abandon", "about"
        )

        val keyPair1 = KaspaMlDsa.deriveKeyPairFromMnemonic(mnemonic, accountIndex = 0)
        val keyPair2 = KaspaMlDsa.deriveKeyPairFromMnemonic(mnemonic, accountIndex = 0)
        val keyPairAccount1 = KaspaMlDsa.deriveKeyPairFromMnemonic(mnemonic, accountIndex = 1)

        assertEquals(keyPair1.publicKeyHex, keyPair2.publicKeyHex)
        assertEquals(keyPair1.privateKeyHex, keyPair2.privateKeyHex)
        assertTrue(keyPair1.isDerivedFromWallet)

        // Different account index should produce different keys
        assertNotEquals(keyPair1.publicKeyHex, keyPairAccount1.publicKeyHex)

        // Verify signing works with derived keypair
        val payload = "Kaspa Post-Quantum BlockDAG Transaction #100".toByteArray(Charsets.UTF_8)
        val sig = KaspaMlDsa.sign(keyPair1, payload, hedged = true)
        val verifyResult = KaspaMlDsa.verify(keyPair1.publicKeyBytes, payload, sig.signatureBytes)
        assertTrue(verifyResult.isValid)
    }

    @Test
    fun testDualHybridClassicalAndPostQuantum() {
        val mnemonic = listOf(
            "abandon", "abandon", "abandon", "abandon", "abandon", "abandon",
            "abandon", "abandon", "abandon", "abandon", "abandon", "about"
        )
        val seed = KaspaCrypto.mnemonicToSeed(mnemonic)
        val schnorrPriv = KaspaSigner.derivePrivateKey(seed, 0, 0, 0)
        val schnorrPub = KaspaSigner.derivePublicKey(schnorrPriv)

        val mlDsaKeyPair = KaspaMlDsa.deriveKeyPairFromMnemonic(mnemonic, accountIndex = 0)

        val payload = "Kaspa Post-Quantum BlockDAG Transaction #492812".toByteArray(Charsets.UTF_8)

        val dualSig = KaspaMlDsa.createDualHybridSignature(
            schnorrPrivateKey = schnorrPriv,
            schnorrPublicKey = schnorrPub,
            mlDsaKeyPair = mlDsaKeyPair,
            payload = payload
        )

        val dualVerification = KaspaMlDsa.verifyDualHybridSignature(
            schnorrPublicKey = schnorrPub,
            mlDsaPublicKey = mlDsaKeyPair.publicKeyBytes,
            payload = payload,
            schnorrSignature = KaspaSigner.hexStringToByteArray(dualSig.schnorrSignatureHex),
            mlDsaSignature = KaspaSigner.hexStringToByteArray(dualSig.mlDsaSignatureHex)
        )

        assertTrue(dualVerification.isBothValid)
        assertTrue(dualVerification.isSchnorrValid)
        assertTrue(dualVerification.isMlDsaValid)
    }
}
