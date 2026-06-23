package com.droiddlp.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.droiddlp.app.ui.PoTokenScreen
import com.droiddlp.app.ui.PoTokenViewModel
import com.droiddlp.app.ui.theme.DroidDlpTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DroidDlpTheme {
                val viewModel: PoTokenViewModel = viewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PoTokenScreen(
                        state = state,
                        onGenerate = viewModel::generate,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}
