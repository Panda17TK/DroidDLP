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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException

/**
 * Foreground service that downloads already-resolved formats (URL + filename +
 * mime). Supports multiple concurrent downloads (capped by a semaphore); progress
 * for every download is mirrored to the UI via [DownloadQueueBus] and summarised
 * in a foreground notification with a Cancel-all action. CLAUDE.md §6 P1-4.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val semaphore = Semaphore(MAX_CONCURRENT)
    private val idCounter = AtomicLong(0L)
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
        when (intent?.action) {
            ACTION_CANCEL -> jobs[intent.getLongExtra(EXTRA_ID, -1L)]?.cancel()
            ACTION_CANCEL_ALL -> jobs.values.forEach { it.cancel() }
            else -> enqueue(intent)
        }
        return START_NOT_STICKY
    }

    private fun enqueue(intent: Intent?) {
        val url = intent?.getStringExtra(EXTRA_URL)?.trim()
        val fileName = intent?.getStringExtra(EXTRA_FILENAME)?.trim()
        val mime = intent?.getStringExtra(EXTRA_MIME)?.trim()
        if (url.isNullOrEmpty() || fileName.isNullOrEmpty() || mime.isNullOrEmpty()) {
            if (jobs.isEmpty()) stopSelf()
            return
        }

        val id = idCounter.incrementAndGet()
        val request = DownloadRequest(url, fileName, mime)
        DownloadQueueBus.update(DownloadItem(id, fileName, DownloadState.Queued))
        startForeground(SUMMARY_ID, summaryNotification())

        val job =
            scope.launch {
                try {
                    semaphore.withPermit {
                        engine.download(request).collect { state ->
                            DownloadQueueBus.update(DownloadItem(id, fileName, state))
                            updateSummary()
                        }
                    }
                } catch (e: CancellationException) {
                    DownloadQueueBus.update(DownloadItem(id, fileName, DownloadState.Failed("cancelled")))
                    throw e
                } catch (e: Exception) {
                    DownloadQueueBus.update(
                        DownloadItem(id, fileName, DownloadState.Failed(e.message ?: e.javaClass.simpleName)),
                    )
                } finally {
                    jobs.remove(id)
                    if (jobs.isEmpty()) {
                        notificationManager().notify(SUMMARY_ID, summaryNotification(done = true))
                        stopForeground(STOP_FOREGROUND_DETACH)
                        stopSelf()
                    } else {
                        updateSummary()
                    }
                }
            }
        jobs[id] = job
    }

    private fun updateSummary() {
        notificationManager().notify(SUMMARY_ID, summaryNotification())
    }

    private fun summaryNotification(done: Boolean = false): Notification {
        val active = DownloadQueueBus.items.value.count { it.isActive }
        val ongoing = !done && active > 0
        val builder =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("DroidDLP")
                .setContentText(if (ongoing) "$active downloading" else "Downloads finished")
                .setOngoing(ongoing)
                .setOnlyAlertOnce(true)
        if (ongoing) {
            builder.addAction(0, "Cancel all", cancelAllPendingIntent())
        }
        return builder.build()
    }

    private fun cancelAllPendingIntent(): PendingIntent =
        PendingIntent.getService(
            this,
            0,
            Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL_ALL),
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
        const val EXTRA_ID = "id"
        const val ACTION_CANCEL = "com.droiddlp.app.download.action.CANCEL"
        const val ACTION_CANCEL_ALL = "com.droiddlp.app.download.action.CANCEL_ALL"
        private const val CHANNEL_ID = "downloads"
        private const val SUMMARY_ID = 1
        private const val MAX_CONCURRENT = 3

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

        fun cancel(
            context: Context,
            id: Long,
        ) {
            context.startService(
                Intent(context, DownloadService::class.java)
                    .setAction(ACTION_CANCEL)
                    .putExtra(EXTRA_ID, id),
            )
        }
    }
}
