package com.example.kaspawallet

import com.example.kaspawallet.data.crypto.KaspaCrypto
import com.example.kaspawallet.data.crypto.KaspaSigner
import com.example.kaspawallet.data.model.KaspaNetwork
import com.example.kaspawallet.data.model.UtxoEntry
import org.junit.Test
import org.junit.Assert.assertNotNull

class JsonFormatTest {
    @Test
    fun testJson() {
        val mnemonic = listOf("abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "about")
        val seed = KaspaCrypto.mnemonicToSeed(mnemonic)
        val address = KaspaSigner.deriveKaspaAddressFromSeed(seed, 0, 0, 0)
        
        val utxos = listOf(
            UtxoEntry(
                outpointTxId = "880eb9819a31821d9d2399e2f35e2433b72637e393d71ecc9b8d0250f49153c3",
                outpointIndex = 0,
                amountSompi = 1000000000L,
                scriptPublicKey = KaspaCrypto.decodeAddressToScriptPublicKey(address),
                blockDaaScore = 0L,
                isCoinbase = false
            )
        )
        
        val (json, txId) = KaspaSigner.createAndSignTransaction(
            seed = seed,
            accountIndex = 0,
            inputs = utxos,
            recipientAddress = address,
            amountSompi = 500000000L,
            feeSompi = 10000L,
            changeAddress = address,
            network = KaspaNetwork.MAINNET
        )
        
        println("GENERATED JSON:")
        println(json)
        assertNotNull(json)
    }
}
