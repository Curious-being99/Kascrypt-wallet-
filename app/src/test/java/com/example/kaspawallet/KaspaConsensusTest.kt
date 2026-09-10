package com.example.kaspawallet

import com.example.kaspawallet.data.crypto.Blake2b
import com.example.kaspawallet.data.crypto.KaspaSigner
import com.example.kaspawallet.data.model.UtxoEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class KaspaConsensusTest {

    @Test
    fun testBlake2bKeyed() {
        val b = Blake2b("TransactionSigningHash".toByteArray(Charsets.UTF_8), 32)
        b.update("abc".toByteArray(Charsets.UTF_8))
        val hex = KaspaSigner.byteArrayToHexString(b.finalize())
        assertEquals("1d25f7b19571ef69f5a7fac4494f74bb9c410ec485570beefe5a7e8d11fcc465", hex)
    }

    @Test
    fun testRustyKaspaSighashVector() {
        // Exact test vector from rusty-kaspa consensus/core/src/hashing/sighash.rs ("native-all-0")
        val prevTxId = "880eb9819a31821d9d2399e2f35e2433b72637e393d71ecc9b8d0250f49153c3"
        val scriptPubKey1 = "208325613d2eeaf7176ac6c670b13c0043156c427438ed72d74b7800862ad884e8ac"
        val scriptPubKey2 = "20fcef4c106cf11135bbd70f02a726a92162d2fb8b22f0469126f800862ad884e8ac"

        val inputs = listOf(
            UtxoEntry(outpointTxId = prevTxId, outpointIndex = 0, amountSompi = 100, scriptPublicKey = scriptPubKey1, blockDaaScore = 0, isCoinbase = false),
            UtxoEntry(outpointTxId = prevTxId, outpointIndex = 1, amountSompi = 200, scriptPublicKey = scriptPubKey1, blockDaaScore = 0, isCoinbase = false),
            UtxoEntry(outpointTxId = prevTxId, outpointIndex = 2, amountSompi = 300, scriptPublicKey = scriptPubKey1, blockDaaScore = 0, isCoinbase = false)
        )
        val outputs = listOf(
            Pair(300L, scriptPubKey2),
            Pair(300L, scriptPubKey1)
        )

        val sighash = KaspaSigner.computeKaspaSighash(
            txVersion = 0,
            inputs = inputs,
            outputs = outputs,
            inputIndex = 0,
            sequences = listOf(0L, 1L, 2L),
            sigOpCounts = listOf(0, 0, 0),
            lockTime = 1615462089000L,
            subnetworkId = ByteArray(20),
            gas = 0L,
            payload = ByteArray(0),
            sighashType = 0x01.toByte()
        )

        val sighashHex = KaspaSigner.byteArrayToHexString(sighash)
        assertEquals("03b7ac6927b2b67100734c3cc313ff8c2e8b3ce3e746d46dd660b706a916b1f5", sighashHex)
    }

    @Test
    fun testKaspaTxIdVector() {
        // Exact test vector #1 from rusty-kaspa consensus/core/src/hashing/tx.rs:
        // tx: version 0, inputs: empty, outputs: empty, lock_time: 0, subnetwork_id: 20 zeros, gas: 0, payload: empty
        // expected_id: "2c18d5e59ca8fc4c23d9560da3bf738a8f40935c11c162017fbf2c907b7e665c"
        val txId = KaspaSigner.computeTransactionId(
            txVersion = 0,
            inputs = emptyList(),
            outputs = emptyList(),
            lockTime = 0L,
            subnetworkId = ByteArray(20),
            gas = 0L,
            payload = ByteArray(0)
        )
        assertEquals("2c18d5e59ca8fc4c23d9560da3bf738a8f40935c11c162017fbf2c907b7e665c", txId)
    }

    @Test
    fun testBip340SchnorrSigningAndVerification() {
        val privKey = KaspaSigner.to32Bytes(BigInteger("B7E151628AED2A6ABF7158809CF4F3C762E7160F38B4DA56A784D9045190CFEF", 16))
        val pubKey = KaspaSigner.derivePublicKey(privKey)
        val msgHash = KaspaSigner.to32Bytes(BigInteger("243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89", 16))

        val sig = KaspaSigner.signSchnorr(privKey, msgHash)
        assertEquals(64, sig.size)

        val verified = KaspaSigner.verifySchnorr(pubKey, msgHash, sig)
        assertTrue("Schnorr signature must be cryptographically valid", verified)
    }

    @Test
    fun testKaspaQrPayloadParsing() {
        val raw1 = "kaspa:qq8l8xy3h967q94z48yv6z2g48q32z7y72g48q32z7q"
        val (addr1, amt1) = com.example.kaspawallet.ui.components.parseKaspaQrPayload(raw1)
        assertEquals("kaspa:qq8l8xy3h967q94z48yv6z2g48q32z7y72g48q32z7q", addr1)
        assertEquals(null, amt1)

        val raw2 = "kaspa:qq8l8xy3h967q94z48yv6z2g48q32z7y72g48q32z7q?amount=123.45"
        val (addr2, amt2) = com.example.kaspawallet.ui.components.parseKaspaQrPayload(raw2)
        assertEquals("kaspa:qq8l8xy3h967q94z48yv6z2g48q32z7y72g48q32z7q", addr2)
        assertEquals(123.45, amt2!!, 0.00001)
    }

    @Test
    fun testKaspaAddressOrUriValidation() {
        assertTrue(com.example.kaspawallet.ui.components.isKaspaAddressOrUri("kaspa:qq8l8xy3h967q94z48yv6z2g48q32z7y72g48q32z7q"))
        assertTrue(com.example.kaspawallet.ui.components.isKaspaAddressOrUri("kaspa:qq8l8xy3h967q94z48yv6z2g48q32z7y72g48q32z7q?amount=10"))
        assertTrue(com.example.kaspawallet.ui.components.isKaspaAddressOrUri("kaspatest:qp8l8xy3h967q94z48yv6z2g48q32z7y72g48q32z7"))
        
        // Ensure arbitrary non-wallet strings are rejected by the camera scanner
        org.junit.Assert.assertFalse(com.example.kaspawallet.ui.components.isKaspaAddressOrUri("https://google.com"))
        org.junit.Assert.assertFalse(com.example.kaspawallet.ui.components.isKaspaAddressOrUri("bitcoin:1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"))
        org.junit.Assert.assertFalse(com.example.kaspawallet.ui.components.isKaspaAddressOrUri("WIFI:S:MyNetwork;T:WPA;P:secret;;"))
    }

    @Test
    fun testDerivePathsAndMultiInputSigning() {
        val mnemonic = listOf(
            "abandon", "abandon", "abandon", "abandon", "abandon", "abandon",
            "abandon", "abandon", "abandon", "abandon", "abandon", "about"
        )
        val seed = com.example.kaspawallet.data.crypto.KaspaCrypto.mnemonicToSeed(mnemonic)

        // 1. Verify that all 30 receive paths and 30 change paths derive unique valid addresses
        val receiveAddresses = (0 until 30).map { idx ->
            KaspaSigner.deriveKaspaAddressFromSeed(seed, accountIndex = 0, branch = 0, addressIndex = idx)
        }
        val changeAddresses = (0 until 30).map { idx ->
            KaspaSigner.deriveKaspaAddressFromSeed(seed, accountIndex = 0, branch = 1, addressIndex = idx)
        }

        assertEquals(30, receiveAddresses.distinct().size)
        assertEquals(30, changeAddresses.distinct().size)
        assertTrue(receiveAddresses.all { it.startsWith("kaspa:q") })
        assertTrue(changeAddresses.all { it.startsWith("kaspa:q") })

        // 2. Build multi-input transaction with inputs from different receive & change paths
        // Input 0: Receive path m/44'/111111'/0'/0/1
        val privKeyRec1 = KaspaSigner.derivePrivateKey(seed, 0, branch = 0, addressIndex = 1)
        val pubKeyRec1 = KaspaSigner.derivePublicKey(privKeyRec1)
        val scriptRec1 = "20" + KaspaSigner.byteArrayToHexString(pubKeyRec1) + "ac"

        // Input 1: Change path m/44'/111111'/0'/1/5
        val privKeyChg5 = KaspaSigner.derivePrivateKey(seed, 0, branch = 1, addressIndex = 5)
        val pubKeyChg5 = KaspaSigner.derivePublicKey(privKeyChg5)
        val scriptChg5 = "20" + KaspaSigner.byteArrayToHexString(pubKeyChg5) + "ac"

        val inputs = listOf(
            UtxoEntry("11".repeat(32), 0, 500000000L, scriptRec1, 1000L),
            UtxoEntry("22".repeat(32), 1, 300000000L, scriptChg5, 1001L)
        )

        val (txJson, txId) = KaspaSigner.createAndSignTransaction(
            seed = seed,
            accountIndex = 0,
            inputs = inputs,
            recipientAddress = receiveAddresses[0],
            amountSompi = 700000000L,
            feeSompi = 10000L,
            changeAddress = changeAddresses[0],
            network = com.example.kaspawallet.data.model.KaspaNetwork.MAINNET
        )

        assertTrue(txJson.isNotEmpty())
        assertEquals(64, txId.length)

        // Verify signatures in the generated JSON
        val parsed = org.json.JSONObject(txJson)
        val parsedInputs = parsed.getJSONObject("transaction").getJSONArray("inputs")
        assertEquals(2, parsedInputs.length())

        val sigScript0Hex = parsedInputs.getJSONObject(0).getString("signatureScript")
        val sig0Bytes = hexStringToByteArray(sigScript0Hex.substring(2, 130)) // extract 64 bytes
        val sighash0 = KaspaSigner.computeKaspaSighash(0, inputs, listOf(
            Pair(700000000L, KaspaSigner.addressToScriptPublicKey(receiveAddresses[0])),
            Pair(99990000L, KaspaSigner.addressToScriptPublicKey(changeAddresses[0]))
        ), 0)
        val sig0Valid = KaspaSigner.verifySchnorr(pubKeyRec1, sighash0, sig0Bytes)
        assertTrue("Input 0 signed with receive path key must verify", sig0Valid)

        val sigScript1Hex = parsedInputs.getJSONObject(1).getString("signatureScript")
        val sig1Bytes = hexStringToByteArray(sigScript1Hex.substring(2, 130))
        val sighash1 = KaspaSigner.computeKaspaSighash(0, inputs, listOf(
            Pair(700000000L, KaspaSigner.addressToScriptPublicKey(receiveAddresses[0])),
            Pair(99990000L, KaspaSigner.addressToScriptPublicKey(changeAddresses[0]))
        ), 1)
        val sig1Valid = KaspaSigner.verifySchnorr(pubKeyChg5, sighash1, sig1Bytes)
        assertTrue("Input 1 signed with change path key must verify", sig1Valid)
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
