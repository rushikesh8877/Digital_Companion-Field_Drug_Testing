package app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ui.data.DemoTestViewModel
import com.sih.drugtestclassifier.auth.OfficerDepartmentVerifier
import com.sih.drugtestclassifier.location.LocationHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: DemoTestViewModel,
    onStart: () -> Unit,
    onHistory: () -> Unit,
    onCalibrate: () -> Unit,
    onLogout: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val operator by viewModel.officerId.collectAsState()
    val officerName by viewModel.officerName.collectAsState()
    val department by viewModel.department.collectAsState()
    val context = LocalContext.current

    var locationText by remember { mutableStateOf("Acquiring GPS fix…") }

    // Officer Uniqueness Audit Modal state
    var showAuditDialog by remember { mutableStateOf(false) }
    var auditReport by remember { mutableStateOf<OfficerDepartmentVerifier.OfficerVerificationReport?>(null) }
    var isAuditing by remember { mutableStateOf(false) }

    // Interactive Grant Rule Tester inside Audit Modal
    var testDept by remember { mutableStateOf(department) }
    var testId by remember { mutableStateOf(operator) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var isTestDenied by remember { mutableStateOf(false) }
    var isTestingGrant by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val loc = LocationHelper.lastKnown(context)
        if (loc.available && loc.address.isNotBlank()) {
            locationText = loc.address
            viewModel.setLocation(loc)
        } else {
            val fresh = LocationHelper.getFreshLocation(context)
            locationText = fresh.address.ifBlank { "Coordinates available" }
            viewModel.setLocation(fresh)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Digital Field Companion",
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    TextButton(onClick = {
                        viewModel.logout()
                        onLogout()
                    }) {
                        Text(
                            "Logout",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Officer Badge Profile Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("👮", fontSize = 24.sp)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                officerName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                "ID: $operator",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                department,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            )
                        }
                    }

                    // Verified Badge Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFE8F5E9),
                        ) {
                            Text(
                                "✓ Unique ID Verified in Department",
                                color = Color(0xFF2E7D32),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }

                        TextButton(
                            onClick = {
                                testDept = department
                                testId = operator
                                testResultText = null
                                showAuditDialog = true
                                isAuditing = true
                                coroutineScope.launch {
                                    auditReport = viewModel.runOfficerVerification()
                                    isAuditing = false
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text("🛡️ Audit IDs", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Location Bar Card
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
                            "CURRENT TEST SITE",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            locationText,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Main Action Buttons
            Button(
                onClick = {
                    viewModel.resetNewTest()
                    onStart()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    "📷 Start New Test",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }

            OutlinedButton(
                onClick = onHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("📋 View Digital Evidence History")
            }

            OutlinedButton(
                onClick = {
                    testDept = department
                    testId = operator
                    testResultText = null
                    showAuditDialog = true
                    isAuditing = true
                    coroutineScope.launch {
                        auditReport = viewModel.runOfficerVerification()
                        isAuditing = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("🛡️ Verify Officer ID Uniqueness Across Departments")
            }

            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = onCalibrate,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("⚙️ Kit Calibration & Diagnostics")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    "🔒 Unique ID per Department · Tamper-Evident SHA-256",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // Officer ID & Department Uniqueness Audit Dialog
    if (showAuditDialog) {
        AlertDialog(
            onDismissRequest = { showAuditDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🛡️", fontSize = 22.sp)
                    Text("Officer ID Uniqueness Audit", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "RULE: Each officer provides their own ID. No two officers may be granted the same ID in the same department.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )

                    if (isAuditing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(vertical = 12.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Text("Auditing all officers across Room DB & Firestore…", style = MaterialTheme.typography.bodySmall)
                        }
                    } else if (auditReport != null) {
                        val report = auditReport!!
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (report.isCompliant) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.errorContainer,
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    if (report.isCompliant) "✅ AUDIT STATUS: COMPLIANT" else "❌ AUDIT STATUS: VIOLATIONS FOUND",
                                    fontWeight = FontWeight.Bold,
                                    color = if (report.isCompliant) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    report.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (report.isCompliant) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Total Departments: ${report.totalDepartments} · Total Officers: ${report.totalOfficers}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // Department Breakdown
                        if (report.departmentBreakdown.isNotEmpty()) {
                            Text("Registered Departments & Officers:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                            report.departmentBreakdown.forEach { (deptName, officerList) ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("🏢 $deptName", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                        Spacer(Modifier.height(4.dp))
                                        officerList.forEach { off ->
                                            Text(
                                                "• ID: ${off.badgeId} (${off.name})",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // Live Uniqueness Test Tool
                    Text(
                        "Interactive ID Grant Validator",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        "Test providing an ID in a department to verify that duplicates are denied and distinct IDs or different departments are granted:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedTextField(
                        value = testId,
                        onValueChange = {
                            testId = it
                            testResultText = null
                        },
                        label = { Text("Candidate Officer ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = testDept,
                        onValueChange = {
                            testDept = it
                            testResultText = null
                        },
                        label = { Text("Department") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    Button(
                        onClick = {
                            isTestingGrant = true
                            testResultText = null
                            coroutineScope.launch {
                                val res = viewModel.checkOfficerIdAvailability(testDept, testId)
                                isTestingGrant = false
                                when (res) {
                                    is OfficerDepartmentVerifier.GrantValidationResult.Denied -> {
                                        testResultText = "❌ DENIED: ${res.reason}"
                                        isTestDenied = true
                                    }
                                    is OfficerDepartmentVerifier.GrantValidationResult.Granted -> {
                                        testResultText = "✅ GRANTED: Officer ID '${testId.trim()}' is strictly unique and available in '$testDept'."
                                        isTestDenied = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isTestingGrant && testId.isNotBlank() && testDept.isNotBlank(),
                    ) {
                        if (isTestingGrant) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Test Grant Rule")
                        }
                    }

                    if (testResultText != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isTestDenied) MaterialTheme.colorScheme.errorContainer else Color(0xFFE8F5E9),
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = testResultText!!,
                                modifier = Modifier.padding(10.dp),
                                color = if (isTestDenied) MaterialTheme.colorScheme.onErrorContainer else Color(0xFF2E7D32),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAuditDialog = false }) {
                    Text("Close")
                }
            },
        )
    }
}
