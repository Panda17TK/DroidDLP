package com.droiddlp.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.droiddlp.app.download.DownloadState

/**
 * Paste a direct media URL and download it to the system Downloads folder, with
 * live progress. The manual exercise of the P1 download stack. CLAUDE.md §6 P1.
 */
@Composable
fun DownloadScreen(
    state: DownloadUiState,
    onStart: (url: String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var url by rememberSaveable { mutableStateOf("") }
    val busy = state.resolving || state.download is DownloadState.Running || state.download is DownloadState.Queued

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
            text = "Paste a direct media URL (http/https). It saves to Downloads/DroidDLP.",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("media URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onStart(url) }, enabled = !busy) { Text("Download") }
            if (busy) {
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
            }
        }

        StatusArea(state)
    }
}

@Composable
private fun StatusArea(state: DownloadUiState) {
    when {
        state.error != null ->
            Text(
                text = "Error: ${state.error}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )

        state.resolving ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator()
                Text(text = "Resolving…", style = MaterialTheme.typography.bodyMedium)
            }

        state.download is DownloadState.Completed -> CompletedCard(state.download.uri)

        state.download is DownloadState.Running -> RunningView(state.download)

        state.download is DownloadState.Queued ->
            Text(text = "Queued…", style = MaterialTheme.typography.bodyMedium)

        else ->
            Text(
                text = "Idle. Paste a URL and tap Download.",
                style = MaterialTheme.typography.bodyMedium,
            )
    }
}

@Composable
private fun RunningView(running: DownloadState.Running) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val percent = running.percent
        if (percent != null) {
            LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        val total = running.totalBytes
        val totalText = if (total != null) " / ${formatBytes(total)}" else ""
        val percentText = if (percent != null) " ($percent%)" else ""
        Text(
            text = "Downloading: ${formatBytes(running.downloadedBytes)}$totalText$percentText",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun CompletedCard(uri: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "Saved to Downloads", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = uri,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
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
