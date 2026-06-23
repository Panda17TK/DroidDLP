package com.droiddlp.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.droiddlp.app.download.DownloadItem
import com.droiddlp.app.download.DownloadState
import com.droiddlp.app.download.StreamFormat

/**
 * Resolve a YouTube or direct media URL, pick a format, and add it to the
 * concurrent download queue (run by a foreground service). CLAUDE.md §6 P1.
 */
@Composable
fun DownloadScreen(
    state: DownloadUiState,
    onResolve: (url: String) -> Unit,
    onDownload: (StreamFormat) -> Unit,
    onCancel: (id: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var url by rememberSaveable { mutableStateOf("") }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "DroidDLP — Download", style = MaterialTheme.typography.headlineMedium)
        Text(
            text =
                "Paste a YouTube or direct media URL, resolve, then pick a format. " +
                    "Multiple downloads run at once; saved to Downloads/DroidDLP.",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(onClick = { onResolve(url) }, enabled = !state.resolving) {
            Text("Resolve")
        }

        if (state.error != null) {
            Text(
                text = "Error: ${state.error}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (state.resolving) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator()
                Text(text = "Resolving…", style = MaterialTheme.typography.bodyMedium)
            }
        }

        state.info?.let { info ->
            if (info.formats.isNotEmpty()) {
                Text(text = info.title, style = MaterialTheme.typography.titleMedium)
                info.formats.forEach { format ->
                    FormatRow(
                        format = format,
                        enabled = !state.resolving,
                        onDownload = { onDownload(format) },
                    )
                }
            }
        }

        if (state.queue.isNotEmpty()) {
            Text(text = "Downloads", style = MaterialTheme.typography.titleMedium)
            state.queue.forEach { item ->
                QueueRow(item = item, onCancel = { onCancel(item.id) })
            }
        }
    }
}

@Composable
private fun FormatRow(
    format: StreamFormat,
    enabled: Boolean,
    onDownload: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = format.label, style = MaterialTheme.typography.bodyLarge)
                val size = format.sizeBytes
                Text(
                    text =
                        format.kind.name.lowercase() +
                            (if (size != null) " · ${formatBytes(size)}" else ""),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(onClick = onDownload, enabled = enabled) { Text("Download") }
        }
    }
}

@Composable
private fun QueueRow(
    item: DownloadItem,
    onCancel: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                if (item.isActive) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            }
            QueueItemStatus(item.state)
        }
    }
}

@Composable
private fun QueueItemStatus(state: DownloadState) {
    when (state) {
        DownloadState.Queued ->
            Text(text = "Queued…", style = MaterialTheme.typography.bodySmall)

        is DownloadState.Running -> {
            val percent = state.percent
            if (percent != null) {
                LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            val total = state.totalBytes
            val totalText = if (total != null) " / ${formatBytes(total)}" else ""
            Text(
                text = "${formatBytes(state.downloadedBytes)}$totalText${percent?.let { " ($it%)" } ?: ""}",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        is DownloadState.Completed ->
            Text(
                text = "Saved · ${state.uri}",
                style = MaterialTheme.typography.bodySmall,
            )

        is DownloadState.Failed ->
            Text(
                text = "Failed: ${state.message}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return "%.1f %s".format(value, units[unitIndex])
}
