package com.droiddlp.app.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Foreground service that downloads an already-resolved format (URL + filename +
 * mime) so the transfer survives the UI being backgrounded. Progress is mirrored
 * to the UI via [DownloadProgressBus] and to a progress notification with a Cancel
 * action. Resolving/format-selection happens in the ViewModel. CLAUDE.md §6 P1-4.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private val engine by lazy { DownloadEngine(HttpByteSource(), MediaStoreDownloadSink(this)) }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_CANCEL) {
            job?.cancel()
            return START_NOT_STICKY
        }
        val url = intent?.getStringExtra(EXTRA_URL)?.trim()
        val fileName = intent?.getStringExtra(EXTRA_FILENAME)?.trim()
        val mime = intent?.getStringExtra(EXTRA_MIME)?.trim()
        if (url.isNullOrEmpty() || fileName.isNullOrEmpty() || mime.isNullOrEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification("Starting…", progress = null))
        startDownload(DownloadRequest(url, fileName, mime))
        return START_NOT_STICKY
    }

    private fun startDownload(request: DownloadRequest) {
        job?.cancel()
        DownloadProgressBus.reset()
        DownloadProgressBus.setTitle(request.fileName)
        DownloadProgressBus.publish(DownloadState.Queued)
        job =
            scope.launch {
                try {
                    engine.download(request).collect { state ->
                        DownloadProgressBus.publish(state)
                        updateNotification(request.fileName, state)
                        if (state is DownloadState.Completed || state is DownloadState.Failed) {
                            finish(state)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    finish(DownloadState.Failed(e.message ?: e.javaClass.simpleName))
                }
            }
    }

    private fun finish(state: DownloadState) {
        DownloadProgressBus.publish(state)
        val text = if (state is DownloadState.Completed) "Saved to Downloads" else "Download failed"
        notificationManager().notify(NOTIFICATION_ID, buildNotification(text, progress = null, ongoing = false))
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun updateNotification(
        title: String,
        state: DownloadState,
    ) {
        when (state) {
            is DownloadState.Running ->
                notificationManager().notify(
                    NOTIFICATION_ID,
                    buildNotification("$title — ${state.percent?.let { "$it%" } ?: "downloading"}", state.percent),
                )

            DownloadState.Queued ->
                notificationManager().notify(NOTIFICATION_ID, buildNotification("$title — queued", null))

            else -> Unit
        }
    }

    private fun buildNotification(
        text: String,
        progress: Int?,
        ongoing: Boolean = true,
    ): Notification {
        val builder =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("DroidDLP")
                .setContentText(text)
                .setOngoing(ongoing)
                .setOnlyAlertOnce(true)
        if (ongoing) {
            builder.addAction(0, "Cancel", cancelPendingIntent())
            if (progress != null) {
                builder.setProgress(100, progress, false)
            } else {
                builder.setProgress(0, 0, true)
            }
        }
        return builder.build()
    }

    private fun cancelPendingIntent(): PendingIntent =
        PendingIntent.getService(
            this,
            0,
            Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun notificationManager(): NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun createChannel() {
        val channel =
            NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Download progress"
            }
        notificationManager().createNotificationChannel(channel)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_FILENAME = "fileName"
        const val EXTRA_MIME = "mime"
        const val ACTION_CANCEL = "com.droiddlp.app.download.action.CANCEL"
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 1

        fun start(
            context: Context,
            url: String,
            fileName: String,
            mime: String,
        ) {
            val intent =
                Intent(context, DownloadService::class.java)
                    .putExtra(EXTRA_URL, url)
                    .putExtra(EXTRA_FILENAME, fileName)
                    .putExtra(EXTRA_MIME, mime)
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            context.startService(Intent(context, DownloadService::class.java).setAction(ACTION_CANCEL))
        }
    }
}
