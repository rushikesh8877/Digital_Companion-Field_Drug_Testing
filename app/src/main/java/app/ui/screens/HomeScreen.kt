package app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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

    val initials = remember(officerName) {
        val clean = officerName.replace("Insp.", "").replace("Officer", "").trim()
        val parts = clean.split(" ").filter { it.isNotBlank() }
        when {
            parts.size >= 2 -> "${parts[0].first().uppercase()}${parts[1].first().uppercase()}"
            parts.size == 1 && parts[0].length >= 2 -> parts[0].take(2).uppercase()
            else -> "RS"
        }
    }

    Scaffold(
        containerColor = Color(0xFFF5F7FA),
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top White Bar with title and red logout
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
                                    .size(36.dp)
                                    .clip(CircleShape),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            )
                            Text(
                                "Digital Field Companion",
                                color = Color(0xFF0A2342),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            viewModel.logout()
                            onLogout()
                        }) {
                            Text(
                                "Logout",
                                color = Color(0xFFD32F2F),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White,
                    ),
                )

                // Subheader Dark Navy Ribbon matching Page 2
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0A2038))
                        .padding(vertical = 8.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Government of Maharashtra · Narcotics Enforcement Unit",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Officer Profile Card matching Page 2
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // Initials Avatar
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFFE3EEFB),
                            modifier = Modifier.size(48.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = initials,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0A2342),
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                officerName,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0A2342),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "ID: $operator · $department",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B),
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
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFEAF7EE),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC3E6CB)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Verified",
                                    tint = Color(0xFF1E824C),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    "Verified in Department",
                                    color = Color(0xFF1E824C),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Security,
                                    contentDescription = "Audit",
                                    tint = Color(0xFF0A2342),
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    "Audit IDs",
                                    fontSize = 12.sp,
                                    color = Color(0xFF0A2342),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }

            // Current Test Site Card matching Page 2
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Place,
                        contentDescription = "Google Maps Location Pin",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(24.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "CURRENT TEST SITE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0A2342),
                            letterSpacing = 0.8.sp,
                        )
                        Text(
                            locationText.ifBlank { "Makhmalabad Road, Nashik" },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B),
                        )
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            // Main Action Button (Orange / Amber matching Page 2)
            Button(
                onClick = {
                    viewModel.resetNewTest()
                    onStart()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE58325)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = "Camera",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        "Start New Test",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }

            // Secondary Action 1 (White card with navy outline)
            OutlinedButton(
                onClick = onHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF0A2342)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = "History",
                        tint = Color(0xFF0A2342),
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        "View Digital Evidence History",
                        color = Color(0xFF0A2342),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }

            // Secondary Action 2 (White card with navy outline)
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
                    .height(50.dp),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF0A2342)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.VerifiedUser,
                        contentDescription = "Verify Uniqueness",
                        tint = Color(0xFF0A2342),
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        "Verify Officer ID Uniqueness",
                        color = Color(0xFF0A2342),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Kit Calibration & Diagnostics link
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCalibrate() }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = "Calibration",
                    tint = Color(0xFF0A2342),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Kit Calibration & Diagnostics",
                    color = Color(0xFF0A2342),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }

            // Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Security",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Unique ID per Department · Tamper-Evident SHA-256",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
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
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = "Shield",
                        tint = Color(0xFF0A2342),
                        modifier = Modifier.size(22.dp),
                    )
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = if (report.isCompliant) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                                        contentDescription = null,
                                        tint = if (report.isCompliant) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        if (report.isCompliant) "AUDIT STATUS: COMPLIANT" else "AUDIT STATUS: VIOLATIONS FOUND",
                                        fontWeight = FontWeight.Bold,
                                        color = if (report.isCompliant) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
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
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Business,
                                                contentDescription = "Department",
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                            Text(deptName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                        }
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
                                        testResultText = "DENIED: ${res.reason}"
                                        isTestDenied = true
                                    }
                                    is OfficerDepartmentVerifier.GrantValidationResult.Granted -> {
                                        testResultText = "GRANTED: Officer ID '${testId.trim()}' is strictly unique and available in '$testDept'."
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
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = if (isTestDenied) Icons.Filled.Cancel else Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = if (isTestDenied) MaterialTheme.colorScheme.onErrorContainer else Color(0xFF2E7D32),
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = testResultText!!,
                                    color = if (isTestDenied) MaterialTheme.colorScheme.onErrorContainer else Color(0xFF2E7D32),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                )
                            }
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
