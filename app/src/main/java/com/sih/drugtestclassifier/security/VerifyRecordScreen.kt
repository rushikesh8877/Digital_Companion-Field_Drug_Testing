package com.sih.drugtestclassifier.security

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sih.drugtestclassifier.models.DigitalTestRecord

/**
 * Standalone verification screen (Person 3's original composable). Not used
 * directly by the main navigation flow — app.ui.screens.VerificationDetailScreen
 * shows the same three checks inline — but kept here as a reusable, drop-in
 * preview/debug screen that only needs a record and the original image bytes.
 */
@Composable
fun VerifyRecordScreen(
    record: DigitalTestRecord,
    originalImageBytes: ByteArray,
    modifier: Modifier = Modifier,
) {
    val verification = remember(record, originalImageBytes) {
        verifyRecord(record, originalImageBytes)
    }
    Column(modifier = modifier.padding(16.dp)) {
        Text("Digital evidence verification", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        CheckLine("Image hash matches", verification.imageHashMatch)
        CheckLine("Record hash matches", verification.recordHashMatch)
        CheckLine("Signature is valid", verification.signatureValid)
    }
}

@Composable
private fun CheckLine(label: String, passed: Boolean) {
    Text("${if (passed) "✅" else "❌"} $label")
}
