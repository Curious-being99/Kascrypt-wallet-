package com.example.kaspawallet

import com.example.kaspawallet.data.crypto.KaspaCrypto
import org.junit.Assert.*
import org.junit.Test

class PassphraseTest {
    @Test
    fun testPassphraseEncryption() {
        // Just testing crypto logic
        val password = "my_password"
        val passphrase = "my_secret_passphrase"
        val encrypted = KaspaCrypto.encryptKeystore(passphrase, password)
        val decrypted = KaspaCrypto.decryptKeystore(encrypted, password)
        assertEquals(passphrase, decrypted)
    }

    @Test
    fun testPassphraseSeedDerivation() {
        val mnemonic = listOf("abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "about")
        val seedWithoutPassphrase = KaspaCrypto.mnemonicToSeed(mnemonic, "")
        val seedWithPassphrase = KaspaCrypto.mnemonicToSeed(mnemonic, "my_passphrase")
        
        assertNotEquals(seedWithoutPassphrase.toList(), seedWithPassphrase.toList())
    }

    @Test
    fun testPassphraseAddressDerivation() {
        val mnemonic = listOf("abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "about")
        val network = com.example.kaspawallet.data.model.KaspaNetwork.MAINNET

        val addrWithoutPassphrase = KaspaCrypto.deriveKaspaAddress(mnemonic, 0, 0, network, "")
        val addrWithPassphrase = KaspaCrypto.deriveKaspaAddress(mnemonic, 0, 0, network, "my_passphrase")
        assertNotEquals(addrWithoutPassphrase, addrWithPassphrase)
        assertTrue(addrWithPassphrase.startsWith("kaspa:"))

        val changeWithout = KaspaCrypto.deriveKaspaChangeAddress(mnemonic, 0, 0, network, "")
        val changeWith = KaspaCrypto.deriveKaspaChangeAddress(mnemonic, 0, 0, network, "my_passphrase")
        assertNotEquals(changeWithout, changeWith)
        assertTrue(changeWith.startsWith("kaspa:"))
    }
}
