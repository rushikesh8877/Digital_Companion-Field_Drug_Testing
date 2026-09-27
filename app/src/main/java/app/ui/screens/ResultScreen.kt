package app.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ui.data.DemoTestViewModel
import app.ui.ui.resultColor
import com.sih.drugtestclassifier.models.ClassificationResult

private const val DISCLAIMER = "Presumptive field result only — confirmatory laboratory testing required."

@Composable
fun ResultScreen(viewModel: DemoTestViewModel, onContinue: () -> Unit, onRetake: () -> Unit) {
    val result by viewModel.classification.collectAsState()
    val reason by viewModel.reason.collectAsState()
    val image by viewModel.image.collectAsState()
    val location by viewModel.capturedLocation.collectAsState()
    val value = result ?: ClassificationResult("Inconclusive", 0f, false)
    val context = LocalContext.current

    val thumbnail = remember(image?.imageUri) {
        val uri = image?.imageUri ?: return@remember null
        try {
            if (uri.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
            } else {
                val path = if (uri.startsWith("file://")) Uri.parse(uri).path ?: uri else uri
                BitmapFactory.decodeFile(path)
            }
        } catch (_: Exception) {
            null
        }
    }

    ScreenColumn("Field Test Result", scrollable = true) {
        // Result Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = resultColor(value.result).copy(alpha = 0.12f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "CLASSIFICATION OUTCOME",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                    ),
                    color = resultColor(value.result),
                )

                Text(
                    text = value.result.uppercase(),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                    color = resultColor(value.result),
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(
                        "Confidence Score: ${(value.confidence * 100).toInt()}%",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }

                Text(
                    if (value.calibrated) "✓ Colorimetric Matrix Calibrated" else "⚠ Uncalibrated Field Lighting",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Location Badge
        if (location != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("📍", fontSize = 20.sp)
                    Column {
                        Text(
                            "CAPTURE LOCATION",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            location?.address?.ifBlank { "Coordinates: ${location?.latitude}, ${location?.longitude}" }
                                ?: "Makhmalabad, Nashik",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        if (value.result == "Inconclusive") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3D6)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    "Reason: ${reason ?: "poor lighting or card not detected"}",
                    Modifier.padding(14.dp),
                    color = Color(0xFF805000),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // Image Preview Card
        Card(
            modifier = Modifier.fillMaxWidth().height(200.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Box(
                Modifier.fillMaxSize().background(Color(0xFFE8EDF2)),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = "Captured reference image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        if (image != null) "Captured image unavailable" else "Reference image",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1D6)),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                DISCLAIMER,
                Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF573900),
            )
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                "Review & Record Report ➔",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        }

        TextButton(
            onClick = onRetake,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Retake Photo")
        }
    }
}


