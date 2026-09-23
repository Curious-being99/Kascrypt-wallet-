package com.example.kaspawallet

import com.example.kaspawallet.data.crypto.KaspaCrypto
import com.example.kaspawallet.data.crypto.KaspaSigner
import com.example.kaspawallet.data.model.KaspaNetwork
import org.junit.Assert.*
import org.junit.Test

class CryptoPassphraseTest {
    @Test
    fun testPassphraseEncryption() {
        val password = "my_password"
        val passphrase = "my_secret_passphrase"
        val encrypted = KaspaCrypto.encryptPassphrase(passphrase, password)
        val decryptedWithPwd = KaspaCrypto.decryptPassphrase(encrypted, password)
        val decryptedWithDev = KaspaCrypto.decryptPassphrase(encrypted, "")
        assertEquals(passphrase, decryptedWithPwd)
        assertEquals(passphrase, decryptedWithDev)
    }

    @Test
    fun testPassphraseSeedDerivation() {
        val mnemonic = listOf("abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "about")
        val seedWithoutPassphrase = KaspaCrypto.mnemonicToSeed(mnemonic, "")
        val seedWithPassphrase = KaspaCrypto.mnemonicToSeed(mnemonic, "my_passphrase")
        
        assertNotEquals(seedWithoutPassphrase.toList(), seedWithPassphrase.toList())
    }

    @Test
    fun testDynamicKeyDerivationForHighIndex() {
        val mnemonic = listOf("abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "about")
        val seed = KaspaCrypto.mnemonicToSeed(mnemonic)
        // High index 150
        val privKey150 = KaspaSigner.derivePrivateKey(seed, accountIndex = 0, branch = 0, addressIndex = 150)
        val pubKey150 = KaspaSigner.derivePublicKey(privKey150)
        val pubHex150 = KaspaSigner.byteArrayToHexString(pubKey150).lowercase()
        val script150 = "20${pubHex150}ac"

        val keyMap = KaspaSigner.deriveAccountKeyMap(seed, accountIndex = 0, gapLimit = 100, network = KaspaNetwork.MAINNET)
        // Ensure index 150 is not in the pre-derived 0..99 map
        assertNull(keyMap[script150])

        val defaultPriv = KaspaSigner.derivePrivateKey(seed, 0, 0, 0)
        val foundPriv = KaspaSigner.findPrivateKeyForScript(
            seed = seed,
            accountIndex = 0,
            cleanScript = script150,
            accountKeyMap = keyMap,
            defaultPrivKey = defaultPriv,
            network = KaspaNetwork.MAINNET
        )
        assertArrayEquals(privKey150, foundPriv)
    }
}
