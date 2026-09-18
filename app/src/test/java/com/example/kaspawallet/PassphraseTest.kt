package com.example.kaspawallet

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.kaspawallet.data.crypto.KaspaCrypto
import com.example.kaspawallet.data.crypto.KaspaUtils
import com.example.kaspawallet.data.local.KaspaDatabase
import com.example.kaspawallet.data.repository.KaspaWalletRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PassphraseTest {
    private lateinit var db: KaspaDatabase
    private lateinit var repo: KaspaWalletRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, KaspaDatabase::class.java
        ).allowMainThreadQueries().build()
        // Simple mock of API client is needed if repository calls it on createWallet
    }

    @After
    fun closeDb() {
        db.close()
    }
    
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
}
