package app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.ui.data.DemoTestViewModel

@Composable
fun VerificationDetailScreen(viewModel: DemoTestViewModel, testId: String, onBack: () -> Unit) {
    val record = viewModel.record(testId)
    val verification by viewModel.verification.collectAsState()
    val detail by viewModel.verificationDetail.collectAsState()

    ScreenColumn("Cryptographic Audit", scrollable = true) {
        if (record == null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Record not found in local history.",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            RecordFields(record)

            if (detail != null) {
                val d = detail!!
                val ok = verification == true
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (ok) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = if (ok) Icons.Filled.VerifiedUser else Icons.Filled.Warning,
                                contentDescription = if (ok) "Verified" else "Warning",
                                tint = if (ok) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                if (ok) "Integrity Verified: Hash and Signature Match" else "Tampering Detected: Cryptographic Check Failed",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        CheckLine("Photo pixel hash matches original capture", d.imageHashMatch)
                        CheckLine("Canonical record metadata digest matches", d.recordHashMatch)
                        CheckLine("ECDSA Android Keystore signature is valid", d.signatureValid)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = { viewModel.verify(record) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Verify Authenticity & Signature")
            }

            OutlinedButton(
                onClick = {
                    viewModel.clearVerification()
                    // Corrupt record hash to demonstrate tamper detection
                    viewModel.verify(record.copy(recordHash = "0000000000000000000000000000000000000000000000000000000000000000"))
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Simulate Tamper Detection")
            }
        }

        Spacer(Modifier.height(8.dp))

        TextButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Back to Evidence History")
        }
    }
}

@Composable
private fun CheckLine(label: String, passed: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = if (passed) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
            contentDescription = if (passed) "Passed" else "Failed",
            tint = if (passed) Color(0xFF2E7D32) else Color(0xFFD32F2F),
            modifier = Modifier.size(16.dp),
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
