package app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import com.sih.drugtestclassifier.camera.CameraCaptureFragment
import com.sih.drugtestclassifier.models.KitProfileLoader
import com.sih.drugtestclassifier.pipeline.ClassificationPipeline
import com.sih.drugtestclassifier.pipeline.DiagnosticReport

/**
 * Debug/calibration screen — NOT part of the locked record flow. Captures a
 * photo and runs [ClassificationPipeline.diagnose] on it, showing every
 * intermediate value (card detection box, raw vs. calibrated RGB, Lab,
 * colour distance to each configured reference) instead of just a final
 * verdict.
 *
 * This exists because assets/kit_profiles/default_kit_profile.json's
 * reference colours are explicitly placeholders derived from synthetic
 * test images, not real photos — see its own "_TODO" field. Real captured
 * photos will very likely classify as Inconclusive, or misclassify, until
 * that file's `references` (and possibly the `testAreaOffset*`/`testArea*`
 * geometry fractions, if the card/test-area boxes below don't land where
 * you expect) are replaced with values measured from real photos of the
 * actual kit. Use this screen to capture a known-positive and a
 * known-negative strip, read off the "Lab" line for each, and put those
 * numbers into the kit profile's `references` array.
 */
@Composable
fun CalibrationScreen(onBack: () -> Unit) {
    val activity = LocalContext.current as? FragmentActivity
    val context = LocalContext.current
    val containerId = remember { View.generateViewId() }
    val attached = remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<DiagnosticReport?>(null) }
    val pipeline = remember {
        ClassificationPipeline(kit = KitProfileLoader.loadDefault(context.assets))
    }

    ScreenColumn("Calibration / diagnostics") {
        Text(
            "Capture a real test strip + reference card. This shows what the pipeline " +
                "actually measured, instead of just Positive/Negative/Inconclusive.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (activity != null) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { ctx -> FragmentContainerView(ctx).apply { id = containerId } },
                update = { container ->
                    if (!attached.value) {
                        attached.value = true
                        val fragment = CameraCaptureFragment().apply {
                            onImageCaptured = { image ->
                                val bitmap = decodeBitmap(context, image.imageUri)
                                report = bitmap?.let { pipeline.diagnose(it) }
                            }
                        }
                        activity.supportFragmentManager
                            .beginTransaction()
                            .replace(container.id, fragment)
                            .commitNowAllowingStateLoss()
                    }
                },
            )
        }
        report?.let { DiagnosticCard(it) }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun DiagnosticCard(report: DiagnosticReport) {
    Card(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F2F2)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Reference card", style = MaterialTheme.typography.titleSmall)
            if (!report.cardFound) {
                Text("NOT DETECTED — ${report.cardFailureReason}")
            } else {
                Text("Detected at: ${report.cardBox}")
                Text("Test area sampled at: ${report.testAreaBox}")
                Text("Samples: ${report.sampleCount}, std dev: ${"%.1f".format(report.stdDev ?: 0.0)}")
                Text(
                    "Calibration applied: ${report.calibrationApplied}" +
                        (report.gains?.let {
                            " (gains R=${"%.2f".format(it.first)} G=${"%.2f".format(it.second)} B=${"%.2f".format(it.third)})"
                        } ?: ""),
                )
                Text("Raw sampled RGB: ${report.rawRgb}")
                Text("Calibrated RGB: ${report.calibratedRgb}")
                Text("Lab (put this in kit_profiles JSON): ${report.lab}")
                Text("Distance to each reference (max confident \u0394E = ${report.maxConfidentDeltaE}):")
                report.distances.forEach { (label, distance) ->
                    Text("  \u00b7 $label: \u0394E = ${"%.1f".format(distance)}")
                }
                Text("Pipeline verdict: ${report.result}")
            }
        }
    }
}

private fun decodeBitmap(context: android.content.Context, imageUri: String): Bitmap? = try {
    if (imageUri.startsWith("content://")) {
        context.contentResolver.openInputStream(Uri.parse(imageUri))?.use { BitmapFactory.decodeStream(it) }
    } else {
        val path = if (imageUri.startsWith("file://")) Uri.parse(imageUri).path ?: imageUri else imageUri
        BitmapFactory.decodeFile(path)
    }
} catch (_: Exception) {
    null
}
