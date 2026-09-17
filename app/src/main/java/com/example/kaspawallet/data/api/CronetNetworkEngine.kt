package com.example.kaspawallet.data.api

import android.content.Context
import android.util.Log
import com.google.android.gms.net.CronetProviderInstaller
import org.chromium.net.CronetEngine
import java.io.File

object CronetNetworkEngine {
    @Volatile
    private var cronetEngine: CronetEngine? = null

    fun getEngine(context: Context): CronetEngine? {
        return cronetEngine ?: synchronized(this) {
            cronetEngine ?: try {
                try {
                    CronetProviderInstaller.installProvider(context.applicationContext)
                } catch (e: Exception) {
                    Log.d("CronetNetworkEngine", "Cronet provider installer note: ${e.message}")
                }

                val builder = CronetEngine.Builder(context.applicationContext)
                    .enableHttp2(true)
                    .enableQuic(true)
                    .enableBrotli(true)
                    .addQuicHint("api.kaspa.org", 443, 443)

                val cacheDir = File(context.cacheDir, "cronet_quic_cache")
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }
                builder.setStoragePath(cacheDir.absolutePath)
                builder.enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISK, 10 * 1024 * 1024)

                val engine = builder.build()
                Log.i("CronetNetworkEngine", "Cronet HTTP/3 QUIC & HTTP/2 Engine initialized: ${engine.versionString}")
                engine.also { cronetEngine = it }
            } catch (e: Exception) {
                Log.w("CronetNetworkEngine", "Cronet engine initialization warning: ${e.message}")
                null
            }
        }
    }
}
