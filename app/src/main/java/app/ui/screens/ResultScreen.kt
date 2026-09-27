package app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ui.data.DemoTestViewModel
import com.sih.drugtestclassifier.models.ClassificationResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val GovNavy = Color(0xFF0A2342)
private val GovBg = Color(0xFFF5F7FA)
private val GovBorder = Color(0xFFE2E8F0)
private val GovGreen = Color(0xFF1B8A44)
private val GovGreenBg = Color(0xFFEBF7EE)
private val GovGreenBorder = Color(0xFFD0EEDB)
private val GovGreenText = Color(0xFF1A7038)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    viewModel: DemoTestViewModel,
    onContinue: () -> Unit,
    onRetake: () -> Unit,
    onBack: () -> Unit = onRetake,
) {
    val result by viewModel.classification.collectAsState()
    val record by viewModel.currentRecord.collectAsState()
    val location by viewModel.capturedLocation.collectAsState()
    val officerName by viewModel.officerName.collectAsState()
    val value = result ?: ClassificationResult("Negative", 0.98f, true)

    val outcome = value.result.uppercase()
    val isNegative = outcome.contains("NEGATIVE")
    val isPositive = outcome.contains("POSITIVE")

    val testIdFormatted = remember(record?.testId) {
        record?.testId ?: "FT-2026-004417"
    }

    val timestampFormatted = remember(record?.timestamp) {
        val time = record?.timestamp ?: System.currentTimeMillis()
        SimpleDateFormat("dd-MM-yyyy, HH:mm", Locale.getDefault()).format(Date(time))
    }

    val locationAddress = remember(location?.address, record?.locationAddress) {
        val addr = location?.address?.ifBlank { null }
            ?: record?.locationAddress?.ifBlank { null }
        addr ?: "Makhmalabad Road, Nashik"
    }

    Scaffold(
        containerColor = GovBg,
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
                            "Test Result",
                            color = GovNavy,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text(
                            "‹",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = GovNavy,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ================= 1. STATUS CARD (Matches Page 4) =================
            val cardBg = if (isNegative) GovGreenBg else if (isPositive) Color(0xFFFDE8E8) else Color(0xFFFFF3CD)
            val cardBorder = if (isNegative) GovGreenBorder else if (isPositive) Color(0xFFF8B4B4) else Color(0xFFFFE082)
            val titleColor = if (isNegative) GovGreenText else if (isPositive) Color(0xFFC53030) else Color(0xFFB7791F)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Outcome icon inside circle matching Page 4
                    Surface(
                        shape = CircleShape,
                        color = if (isNegative) GovGreen else if (isPositive) Color(0xFFC53030) else Color(0xFFD97706),
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isNegative) Icons.Filled.Check else if (isPositive) Icons.Filled.PriorityHigh else Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }

                    // Outcome text
                    Text(
                        text = if (isNegative) "NEGATIVE" else if (isPositive) "POSITIVE" else "INCONCLUSIVE",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = titleColor,
                        letterSpacing = 1.sp,
                    )

                    // Subtitle
                    Text(
                        text = if (isNegative) {
                            "No controlled substance detected against\nconfigured reference profile"
                        } else if (isPositive) {
                            "Controlled substance detected against\nconfigured reference profile"
                        } else {
                            "Test outcome inconclusive.\nPlease retake photo in even lighting."
                        },
                        fontSize = 13.sp,
                        color = if (isNegative) Color(0xFF2C5E3B) else if (isPositive) Color(0xFF742A2A) else Color(0xFF78350F),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                    )
                }
            }

            // ================= 2. CALIBRATION MATRIX BADGE (Matches Page 4) =================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, GovBorder),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Calibrated",
                        tint = GovGreen,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = if (value.calibrated) "Colorimetric Matrix Calibrated" else "Field Colorimetric Calibrated",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                    )
                }
            }

            // ================= 3. TEST SITE CARD (Matches Page 4) =================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, GovBorder),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Place,
                        contentDescription = "Test Site Location Pin",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(24.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "TEST SITE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GovNavy,
                            letterSpacing = 0.8.sp,
                        )
                        Text(
                            text = locationAddress,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B),
                        )
                    }
                }
            }

            // ================= 4. DETAILS TABLE (Matches Page 4) =================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, GovBorder),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Test ID Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Test ID",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            testIdFormatted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B),
                        )
                    }

                    HorizontalDivider(color = GovBorder, thickness = 0.8.dp)

                    // Officer Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Officer",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            officerName.ifBlank { "Insp. R. Sharma" },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B),
                        )
                    }

                    HorizontalDivider(color = GovBorder, thickness = 0.8.dp)

                    // Timestamp Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Timestamp",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            timestampFormatted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ================= 5. PRIMARY BUTTON (Matches Page 4) =================
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GovNavy),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "Review & Record Report",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        "›",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }

            // ================= 6. SECONDARY BUTTON (Matches Page 4) =================
            TextButton(
                onClick = onRetake,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
            ) {
                Text(
                    "Retake Photo",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GovNavy,
                )
            }
        }
    }
}
