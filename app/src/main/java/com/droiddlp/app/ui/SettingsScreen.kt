package com.droiddlp.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.droiddlp.app.settings.SettingsStore
import kotlin.math.roundToInt

/** App settings (max concurrency, download subfolder, dynamic color). CLAUDE.md §6 P2. */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val maxConcurrent by SettingsStore.maxConcurrent.collectAsStateWithLifecycle()
    val subDir by SettingsStore.subDir.collectAsStateWithLifecycle()
    val dynamicColor by SettingsStore.dynamicColor.collectAsStateWithLifecycle()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Max concurrent downloads: $maxConcurrent",
                style = MaterialTheme.typography.bodyLarge,
            )
            Slider(
                value = maxConcurrent.toFloat(),
                onValueChange = { SettingsStore.setMaxConcurrent(it.roundToInt()) },
                valueRange = 1f..5f,
                steps = 3,
            )
            Text(
                text = "Applies to new downloads once the current queue drains.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        OutlinedTextField(
            value = subDir,
            onValueChange = { SettingsStore.setSubDir(it) },
            label = { Text("Download subfolder (Downloads/…)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Use dynamic color (Android 12+)",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = dynamicColor, onCheckedChange = { SettingsStore.setDynamicColor(it) })
        }
    }
}
