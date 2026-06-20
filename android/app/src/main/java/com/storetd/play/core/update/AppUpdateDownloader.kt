package com.storetd.play.core.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object AppUpdateDownloader {

    fun downloadAndInstall(context: Context, apkUrl: String): Boolean {
        val cleanUrl = apkUrl.trim()
        if (cleanUrl.isBlank()) return false

        Toast.makeText(context, "Iniciando descarga segura (Motor Propio)...", Toast.LENGTH_LONG).show()

        thread {
            val handler = Handler(Looper.getMainLooper())
            try {
                val url = URL(cleanUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("Error del servidor: ${connection.responseCode}")
                }

                // 📁 Bóveda secreta de tu aplicación (Ignora si la TV Box está mutilada)
                val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                val apkFile = File(directory, "update_storetd.apk")
                if (apkFile.exists()) apkFile.delete()

                val inputStream = connection.inputStream
                val outputStream = FileOutputStream(apkFile)
                
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()
                connection.disconnect()

                handler.post {
                    Toast.makeText(context, "Descarga lista. Abriendo instalador...", Toast.LENGTH_SHORT).show()
                    openInstaller(context, apkFile)
                }

            } catch (e: Exception) {
                handler.post {
                    Toast.makeText(context, "Fallo interno. Abriendo navegador web...", Toast.LENGTH_LONG).show()
                    // 🛟 PUENTE DE EMERGENCIA: Si todo falla, abre el navegador
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (ignored: Exception) {}
                }
            }
        }
        return true
    }

    private fun openInstaller(context: Context, apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(
                context,
                "Permití instalar apps desconocidas para StoreTD Play y tocá Actualizar otra vez.",
                Toast.LENGTH_LONG
            ).show()

            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            try { context.startActivity(settingsIntent) } catch (e: Exception) {}
            return
        }

        val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apkFile)

        val installIntent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(apkUri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        try {
            context.startActivity(installIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No se encontró instalador en esta TV Box.", Toast.LENGTH_LONG).show()
        }
    }
}
