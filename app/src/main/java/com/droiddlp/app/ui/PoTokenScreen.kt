package com.droiddlp.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.droiddlp.app.potoken.PoTokenResult

/**
 * On-device PoToken verification screen: enter a videoId (and optional
 * visitorData), tap Generate, and observe whether the WebView BotGuard solver
 * mints tokens. This is the manual E2E harness for the device-only path.
 */
@Composable
fun PoTokenScreen(
    state: PoTokenUiState,
    onGenerate: (videoId: String, visitorData: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var videoId by rememberSaveable { mutableStateOf("dQw4w9WgXcQ") }
    var visitorData by rememberSaveable { mutableStateOf("") }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "DroidDLP — PoToken", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "On-device verification harness for the WebView BotGuard solver.",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = videoId,
            onValueChange = { videoId = it },
            label = { Text("videoId") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = visitorData,
            onValueChange = { visitorData = it },
            label = { Text("visitorData (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = { onGenerate(videoId, visitorData) },
            enabled = state !is PoTokenUiState.Loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state is PoTokenUiState.Loading) "Generating…" else "Generate PoToken")
        }

        when (state) {
            PoTokenUiState.Idle ->
                Text(
                    text = "Enter a videoId and tap Generate.",
                    style = MaterialTheme.typography.bodyMedium,
                )

            PoTokenUiState.Loading -> CircularProgressIndicator()

            is PoTokenUiState.Success -> ResultCard(state.result)

            is PoTokenUiState.Error ->
                Text(
                    text = "Error: ${state.message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
        }
    }
}

@Composable
private fun ResultCard(result: PoTokenResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "Success", style = MaterialTheme.typography.titleMedium)
                TokenRow(label = "player", value = result.playerRequestPoToken)
                TokenRow(label = "streaming", value = result.streamingDataPoToken)
                TokenRow(label = "visitorData", value = result.visitorData ?: "(null)")
            }
        }
    }
}

@Composable
private fun TokenRow(
    label: String,
    value: String,
) {
    Column {
        Text(
            text = "$label (${value.length} chars)",
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}
