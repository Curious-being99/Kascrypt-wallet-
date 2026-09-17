package com.example.kaspawallet

import android.app.Application
import com.example.kaspawallet.data.api.CronetNetworkEngine
import com.example.kaspawallet.data.local.KaspaDatabase
import com.example.kaspawallet.data.repository.KaspaWalletRepository

class KaspaApplication : Application() {
    val database by lazy { KaspaDatabase.getDatabase(this) }
    val repository by lazy { KaspaWalletRepository(database) }

    override fun onCreate() {
        super.onCreate()
        try {
            CronetNetworkEngine.getEngine(this)
        } catch (_: Exception) {}
    }
}
