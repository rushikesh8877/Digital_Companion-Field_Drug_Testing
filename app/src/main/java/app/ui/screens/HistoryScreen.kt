package app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ui.data.DemoTestViewModel
import app.ui.ui.resultColor
import com.sih.drugtestclassifier.models.DigitalTestRecord

@Composable
fun HistoryScreen(viewModel: DemoTestViewModel, onBack: () -> Unit, onRecord: (String) -> Unit) {
    val records by viewModel.records.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val pendingCount by viewModel.pendingSyncCount.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("All") }
    val results = listOf("All", "Positive", "Negative", "Inconclusive")

    val filtered = records.filter { record ->
        (searchQuery.isBlank() ||
            record.operatorId.contains(searchQuery, true) ||
            record.testId.contains(searchQuery, true) ||
            record.locationAddress.contains(searchQuery, true)) &&
            (result == "All" || record.result == result) &&
            (fromDate.isBlank() || dateOnly(record.timestamp) >= fromDate) &&
            (toDate.isBlank() || dateOnly(record.timestamp) <= toDate)
    }

    ScreenColumn("Digital Evidence Vault") {
        // Cloud Sync Status Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isOnline) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    // Actual Green Dot (or Amber Dot if offline)
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = if (isOnline) Color(0xFF2E7D32) else Color(0xFFE65100),
                                shape = CircleShape,
                            ),
                    )
                    Column {
                        Text(
                            if (isOnline) "Firebase Cloud Connected" else "Offline Mode (Local Storage)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        if (pendingCount > 0) {
                            Text(
                                "$pendingCount report(s) queued for sync",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            Text(
                                if (isOnline) "All officer reports synced in real time" else "Will sync automatically when reconnected",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (isOnline) {
                    IconButton(
                        onClick = { viewModel.syncNow() },
                        enabled = !isSyncing,
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            // Actual Refresh Button
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Sync now",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search by ID, Officer, or Location") },
            placeholder = { Text("e.g. Nashik, Makhmalabad, OFFICER-7421") },
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = fromDate,
                onValueChange = { fromDate = it },
                modifier = Modifier.weight(1f),
                label = { Text("From YYYY-MM-DD") },
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
            )
            OutlinedTextField(
                value = toDate,
                onValueChange = { toDate = it },
                modifier = Modifier.weight(1f),
                label = { Text("To YYYY-MM-DD") },
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            results.forEach { r ->
                val selected = result == r
                FilterChip(
                    selected = selected,
                    onClick = { result = r },
                    label = { Text(r, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }

        Text(
            "${filtered.size} record(s) found",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(filtered) { record ->
                RecordRow(record, onClick = { onRecord(record.testId) })
            }
        }

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

@Composable
private fun RecordRow(record: DigitalTestRecord, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        record.testId,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Spacer(Modifier.height(2.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (record.isSynced) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Icon(
                                imageVector = if (record.isSynced) Icons.Filled.CloudDone else Icons.Filled.PhoneAndroid,
                                contentDescription = if (record.isSynced) "Cloud Synced" else "Local Phone Storage",
                                tint = if (record.isSynced) Color(0xFF2E7D32) else Color(0xFFE65100),
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                if (record.isSynced) "Cloud Synced" else "Offline (Pending Sync)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (record.isSynced) Color(0xFF2E7D32) else Color(0xFFE65100),
                                ),
                            )
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = resultColor(record.result).copy(alpha = 0.15f),
                ) {
                    Text(
                        record.result.uppercase(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = resultColor(record.result),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                    )
                }
            }

            Text(
                "Officer: ${record.operatorId} · ${formatTimestamp(record.timestamp)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val locationDisplay = record.locationAddress.ifBlank {
                if (record.latitude != 0.0 || record.longitude != 0.0) {
                    "GPS: ${String.format(java.util.Locale.US, "%.4f", record.latitude)}, ${String.format(java.util.Locale.US, "%.4f", record.longitude)}"
                } else "Location unavailable"
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = "Location Pin",
                    tint = Color(0xFFE53935),
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    locationDisplay,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun dateOnly(timestamp: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(timestamp))
