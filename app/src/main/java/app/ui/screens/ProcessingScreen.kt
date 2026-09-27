package app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ui.data.DemoTestViewModel

@Composable
fun ProcessingScreen(viewModel: DemoTestViewModel, onComplete: () -> Unit) {
    var started by remember { mutableStateOf(false) }
    ScreenColumn("Digital Forensics Analysis") {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(24.dp),
            ) {
                // Official Application Logo
                Image(
                    painter = painterResource(id = com.sih.drugtestclassifier.R.drawable.app_logo),
                    contentDescription = "Government Digital Forensics Law Enforcement Logo",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Fit,
                )

                Spacer(Modifier.height(8.dp))

                CircularProgressIndicator(
                    color = Color(0xFF0A2342),
                    strokeWidth = 3.5.dp,
                    modifier = Modifier.size(42.dp),
                )

                Text(
                    "Analyzing Test Image…",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF0A2342),
                )

                Text(
                    "Calibrating colorimetric reference matrix & verifying chain-of-custody",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    LaunchedEffect(Unit) {
        if (!started) {
            started = true
            viewModel.classify().join()
            kotlinx.coroutines.delay(400)
            onComplete()
        }
    }
}
