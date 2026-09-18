package com.example.kaspawallet

import com.example.kaspawallet.data.crypto.KaspaCrypto
import org.junit.Assert.*
import org.junit.Test

class CryptoPassphraseTest {
    @Test
    fun testPassphraseEncryption() {
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
}
