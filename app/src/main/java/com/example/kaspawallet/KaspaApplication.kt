package com.example.kaspawallet

import android.app.Application
import com.example.kaspawallet.data.local.KaspaDatabase
import com.example.kaspawallet.data.repository.KaspaWalletRepository

class KaspaApplication : Application() {
    val database by lazy { KaspaDatabase.getDatabase(this) }
    val repository by lazy { KaspaWalletRepository(database) }
}
