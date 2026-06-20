package com.storetd.play.core.update

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

object AppUpdateDownloader {
    private val handledDownloads = Collections.newSetFromMap(ConcurrentHashMap<Long, Boolean>())

    fun downloadAndInstall(context: Context, apkUrl: String): Boolean {
        val cleanUrl = apkUrl.trim()

        if (cleanUrl.isBlank()) {
                        try {
                // 🔥 PUENTE DE EMERGENCIA: Abre el navegador si la TV Box está mutilada
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                android.widget.Toast.makeText(context, "Gestor bloqueado. Abriendo navegador web...", android.widget.Toast.LENGTH_LONG).show()
                true
            } catch (e2: Exception) {
                android.widget.Toast.makeText(context, "Descarga manual requerida. Usa la app 'Downloader'.", android.widget.Toast.LENGTH_LONG).show()
                false
            }
        }
    }

    private fun registerDownloadReceiver(
        context: Context,
        manager: DownloadManager,
        downloadId: Long
    ) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)

                if (completedId != downloadId) return

                runCatching { context.unregisterReceiver(this) }

                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = manager.query(query)

                cursor?.use {
                    if (!it.moveToFirst()) {
                        Toast.makeText(context, "No se pudo verificar la descarga.", Toast.LENGTH_LONG).show()
                        return
                    }

                    val statusIndex = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = if (statusIndex >= 0) it.getInt(statusIndex) else DownloadManager.STATUS_FAILED

                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        handleSuccessfulDownload(context, manager, downloadId)
                    } else {
                        Toast.makeText(context, "La descarga de actualización falló.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)

        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun pollDownloadCompletion(
        context: Context,
        manager: DownloadManager,
        downloadId: Long
    ) {
        Thread {
            val startedAt = System.currentTimeMillis()
            val maxWaitMs = 10L * 60L * 1000L

            while (System.currentTimeMillis() - startedAt < maxWaitMs) {
                try {
                    val cursor = manager.query(
                        DownloadManager.Query().setFilterById(downloadId)
                    )

                    cursor?.use {
                        if (it.moveToFirst()) {
                            val statusIndex = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            val status = if (statusIndex >= 0) {
                                it.getInt(statusIndex)
                            } else {
                                DownloadManager.STATUS_FAILED
                            }

                            when (status) {
                                DownloadManager.STATUS_SUCCESSFUL -> {
                                    handleSuccessfulDownload(context, manager, downloadId)
                                    return@Thread
                                }

                                DownloadManager.STATUS_FAILED -> {
                                    Handler(Looper.getMainLooper()).post {
                                        Toast.makeText(
                                            context,
                                            "La descarga de actualización falló.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    return@Thread
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Se reintenta hasta agotar tiempo.
                }

                Thread.sleep(1000L)
            }
        }.start()
    }

    private fun handleSuccessfulDownload(
        context: Context,
        manager: DownloadManager,
        downloadId: Long
    ) {
        if (!handledDownloads.add(downloadId)) return

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                context,
                "Descarga completa. Abriendo instalador...",
                Toast.LENGTH_LONG
            ).show()

            openInstaller(context, manager, downloadId)
        }
    }

    private fun openInstaller(
        context: Context,
        manager: DownloadManager,
        downloadId: Long
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            Toast.makeText(
                context,
                "Permití instalar apps desconocidas para StoreTD Play y tocá Actualizar otra vez.",
                Toast.LENGTH_LONG
            ).show()

            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            runCatching { context.startActivity(settingsIntent) }
            return
        }

        var apkFile: java.io.File? = null
        val query = android.app.DownloadManager.Query().setFilterById(downloadId)
        manager.query(query)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val uriIndex = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_LOCAL_URI)
                if (uriIndex >= 0) {
                    val localUriString = cursor.getString(uriIndex)
                    if (localUriString != null) {
                        apkFile = java.io.File(android.net.Uri.parse(localUriString).path)
                    }
                }
            }
        }

        if (apkFile == null || !apkFile!!.exists()) {
            android.widget.Toast.makeText(context, "Error: No se pudo ubicar el archivo APK físico.", android.widget.Toast.LENGTH_LONG).show()
            return
        }

        val apkUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            apkFile!!
        )

        val installIntent = android.content.Intent(android.content.Intent.ACTION_VIEW)
            .setDataAndType(apkUri, "application/vnd.android.package-archive")
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)

        try {
            context.startActivity(installIntent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No se encontró instalador de APK en este dispositivo.", Toast.LENGTH_LONG).show()
        }
    }
}
