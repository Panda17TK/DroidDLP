package com.droiddlp.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.droiddlp.app.ui.DownloadScreen
import com.droiddlp.app.ui.DownloadViewModel
import com.droiddlp.app.ui.PoTokenScreen
import com.droiddlp.app.ui.PoTokenViewModel
import com.droiddlp.app.ui.theme.DroidDlpTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DroidDlpTheme {
                NotificationPermissionRequest()
                MainScaffold()
            }
        }
    }
}

/** Requests POST_NOTIFICATIONS once on Android 13+ so download progress can show. */
@Composable
private fun NotificationPermissionRequest() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun MainScaffold() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) },
                    label = { Text("Download") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    label = { Text("PoToken") },
                )
            }
        },
    ) { innerPadding ->
        when (tab) {
            0 -> {
                val vm: DownloadViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                DownloadScreen(
                    state = state,
                    onStart = vm::start,
                    onCancel = vm::cancel,
                    modifier = Modifier.padding(innerPadding),
                )
            }

            else -> {
                val vm: PoTokenViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                PoTokenScreen(
                    state = state,
                    onGenerate = vm::generate,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}
