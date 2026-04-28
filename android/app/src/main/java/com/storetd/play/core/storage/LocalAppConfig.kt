package com.storetd.play.core.storage

import android.content.Context
import com.storetd.play.core.network.RemoteAppConfig

object LocalAppConfig {
    private const val PREFS = "storetd_play_app_config"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(context: Context): RemoteAppConfig {
        val p = prefs(context)

        return RemoteAppConfig(
            appName = p.getString("appName", "StoreTD Play") ?: "StoreTD Play",
            welcomeTitle = p.getString(
                "welcomeTitle",
                "Reproductor profesional para contenido autorizado"
            ) ?: "Reproductor profesional para contenido autorizado",
            welcomeMessage = p.getString(
                "welcomeMessage",
                "Carga tus listas M3U/M3U8 autorizadas, organiza canales, usa favoritos, historial y zapping."
            ) ?: "Carga tus listas M3U/M3U8 autorizadas, organiza canales, usa favoritos, historial y zapping.",
            maintenanceMode = p.getBoolean("maintenanceMode", false),
            maintenanceMessage = p.getString(
                "maintenanceMessage",
                "Estamos realizando mantenimiento. Intenta nuevamente mas tarde."
            ) ?: "Estamos realizando mantenimiento. Intenta nuevamente mas tarde.",
            providerMessage = p.getString(
                "providerMessage",
                "Uso permitido solo con contenido autorizado."
            ) ?: "Uso permitido solo con contenido autorizado.",
            supportWhatsApp = p.getString("supportWhatsApp", "") ?: "",
            supportEmail = p.getString("supportEmail", "") ?: "",
            renewUrl = p.getString("renewUrl", "") ?: "",
            termsUrl = p.getString("termsUrl", "") ?: "",
            privacyUrl = p.getString("privacyUrl", "") ?: "",
            allowUserPlaylistInput = p.getBoolean("allowUserPlaylistInput", true),
            forceAppUpdate = p.getBoolean("forceAppUpdate", false),
            minimumAppVersion = p.getString("minimumAppVersion", "1.0.0") ?: "1.0.0",
            updatedAt = p.getString("updatedAt", "") ?: ""
        )
    }

    fun save(context: Context, config: RemoteAppConfig) {
        prefs(context).edit()
            .putString("appName", config.appName)
            .putString("welcomeTitle", config.welcomeTitle)
            .putString("welcomeMessage", config.welcomeMessage)
            .putBoolean("maintenanceMode", config.maintenanceMode)
            .putString("maintenanceMessage", config.maintenanceMessage)
            .putString("providerMessage", config.providerMessage)
            .putString("supportWhatsApp", config.supportWhatsApp)
            .putString("supportEmail", config.supportEmail)
            .putString("renewUrl", config.renewUrl)
            .putString("termsUrl", config.termsUrl)
            .putString("privacyUrl", config.privacyUrl)
            .putBoolean("allowUserPlaylistInput", config.allowUserPlaylistInput)
            .putBoolean("forceAppUpdate", config.forceAppUpdate)
            .putString("minimumAppVersion", config.minimumAppVersion)
            .putString("updatedAt", config.updatedAt)
            .apply()
    }
}
