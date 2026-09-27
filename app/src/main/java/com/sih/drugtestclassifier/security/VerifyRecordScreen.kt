package com.sih.drugtestclassifier.security

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (passed) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
            contentDescription = if (passed) "Passed" else "Failed",
            tint = if (passed) Color(0xFF2E7D32) else Color(0xFFC62828),
            modifier = Modifier.size(18.dp),
        )
        Text(label)
    }
}
