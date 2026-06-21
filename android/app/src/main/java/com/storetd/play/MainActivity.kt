package com.storetd.play

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.storetd.play.ui.streamvault.theme.StreamVaultTheme
import com.storetd.play.navigation.StoreTdPlayNavHost
import com.storetd.play.ui.theme.StoreTdPlayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- AUTO-LIMPIEZA DE CACHÉ (NUEVA VERSIÓN) ---
        try {
            val prefs = getSharedPreferences("StoreTD_Prefs", android.content.Context.MODE_PRIVATE)
            val lastVersion = prefs.getInt("last_version_code", 0)
            val currentVersion = 88

            if (currentVersion > lastVersion) {
                cacheDir?.deleteRecursively()
                codeCacheDir?.deleteRecursively()
                prefs.edit().putInt("last_version_code", currentVersion).apply()
                android.util.Log.d("STORETD", "¡Caché e historial viejo eliminados en actualización!")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // ----------------------------------------------

        setContent {
            StreamVaultTheme {
                StoreTdPlayNavHost()
            }
        }
    }
}
