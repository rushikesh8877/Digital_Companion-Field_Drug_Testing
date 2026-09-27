package app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ui.data.DemoTestViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveConfirmationScreen(
    viewModel: DemoTestViewModel,
    onHome: () -> Unit,
    onViewHistory: (() -> Unit)? = null,
) {
    val record by viewModel.currentRecord.collectAsState()
    val saved by viewModel.saved.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Image(
                            painter = painterResource(id = com.sih.drugtestclassifier.R.drawable.app_logo),
                            contentDescription = "Government Digital Forensics Law Enforcement Logo",
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape),
                        )
                        Text(
                            if (saved) "Record Saved Successfully" else "Review & Confirm Report",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    if (saved) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (onViewHistory != null) {
                                OutlinedButton(
                                    onClick = onViewHistory,
                                    modifier = Modifier.weight(1f).height(54.dp),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text("View History")
                                }
                            }
                            Button(
                                onClick = onHome,
                                modifier = Modifier.weight(1f).height(54.dp),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("Return Home")
                            }
                        }
                    } else {
                        Button(
                            onClick = { viewModel.save() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Save,
                                    contentDescription = "Save",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    "Save Record to Database",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (saved) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isOnline) MaterialTheme.colorScheme.secondaryContainer else Color(0xFFFFF3E0),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = if (isOnline) Icons.Filled.CloudDone else Icons.Filled.PhoneAndroid,
                            contentDescription = if (isOnline) "Cloud Synced" else "Local Storage",
                            tint = if (isOnline) MaterialTheme.colorScheme.primary else Color(0xFFE65100),
                            modifier = Modifier.size(32.dp),
                        )
                        Column {
                            Text(
                                if (isOnline) "Saved & Synced to Cloud" else "Saved to Local Database (Offline)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isOnline) MaterialTheme.colorScheme.onSecondaryContainer else Color(0xFFE65100),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (isOnline) "Tamper-proof record archived locally in Room and synced to Firebase Cloud. Visible to all officers."
                                else "Record safely stored in offline database. It will sync to Firebase Cloud automatically as soon as internet connects.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isOnline) MaterialTheme.colorScheme.onSecondaryContainer else Color(0xFF5D4037),
                            )
                        }
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        "Please verify all details and location below. Once saved, the cryptographic signature is locked into local storage.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (record != null) {
                RecordFields(record!!)
            } else {
                Text(
                    "No active record found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
