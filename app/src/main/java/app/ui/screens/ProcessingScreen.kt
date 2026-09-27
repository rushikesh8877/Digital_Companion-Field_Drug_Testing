package app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ui.data.DemoTestViewModel

@Composable
fun ProcessingScreen(viewModel: DemoTestViewModel, onComplete: () -> Unit) {
    val scope = rememberCoroutineScope()
    var started by remember { mutableStateOf(false) }
    ScreenColumn("Processing") {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                CircularProgressIndicator()
                Text("Analyzing test image…", style = MaterialTheme.typography.titleMedium)
                Text("Checking card alignment and result", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    LaunchedEffect(Unit) {
        if (!started) {
            started = true
            viewModel.classify().join()
            kotlinx.coroutines.delay(300)
            onComplete()
        }
    }
}
