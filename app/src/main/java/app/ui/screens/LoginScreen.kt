package app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import app.ui.data.DemoTestViewModel
import com.sih.drugtestclassifier.auth.EmailOtpService
import com.sih.drugtestclassifier.auth.FirebaseAuthManager
import com.sih.drugtestclassifier.auth.OfficerDepartmentVerifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Custom Theme Colors matching the reference screenshots exactly
private val GovNavy = Color(0xFF0A2342)
private val GovNavyHeader = Color(0xFF091F38)
private val GovOrange = Color(0xFFE58325)
private val GovBg = Color(0xFFF5F7FA)
private val GovBorder = Color(0xFFE2E8F0)
private val GovGreen = Color(0xFF1B8A44)
private val GovGreenBg = Color(0xFFEBF7EE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: DemoTestViewModel,
    onLoginSuccess: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Sign In, 1 = Register Officer

    // Sign In fields
    var signInEmail by remember { mutableStateOf("") }
    var signInPassword by remember { mutableStateOf("") }
    var signInPasswordVisible by remember { mutableStateOf(false) }

    // Official Registration fields (default to empty so example background hints are displayed)
    var regOfficerId by remember { mutableStateOf("") }
    var regOfficerName by remember { mutableStateOf("") }
    var regDepartment by remember { mutableStateOf("") }
    var regGender by remember { mutableStateOf("Male") }
    var regRank by remember { mutableStateOf("Inspector") }
    var regServiceNumber by remember { mutableStateOf("") }
    var regDistrict by remember { mutableStateOf("") }
    var regPhone by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regPasswordVisible by remember { mutableStateOf(false) }
    var regConfirmPassword by remember { mutableStateOf("") }
    var regConfirmPasswordVisible by remember { mutableStateOf(false) }

    // Email verification state
    val isEmailVerified by viewModel.isEmailVerified.collectAsState()
    var isSendingOtp by remember { mutableStateOf(false) }
    var isVerifyingOtp by remember { mutableStateOf(false) }
    var isOtpDispatched by remember { mutableStateOf(false) }
    var showOtpDialog by remember { mutableStateOf(false) }
    var otpInput by remember { mutableStateOf("") }
    var otpError by remember { mutableStateOf<String?>(null) }
    var emailVerificationError by remember { mutableStateOf<String?>(null) }
    var otpTimer by remember { mutableIntStateOf(30) }
    var canResendOtp by remember { mutableStateOf(false) }

    // Real-time departmental uniqueness validation
    var availabilityStatus by remember { mutableStateOf<String?>(null) }
    var isIdTaken by remember { mutableStateOf(false) }

    // UI Feedback state
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    // Timer countdown for OTP resend
    LaunchedEffect(isOtpDispatched, showOtpDialog) {
        if (isOtpDispatched || showOtpDialog) {
            canResendOtp = false
            otpTimer = 30
            while (otpTimer > 0) {
                delay(1000)
                otpTimer--
            }
            canResendOtp = true
        }
    }

    // Check departmental uniqueness whenever ID or department is modified during registration
    LaunchedEffect(regOfficerId, regDepartment, selectedTab) {
        if (selectedTab == 1 && regOfficerId.isNotBlank() && regDepartment.isNotBlank()) {
            val res = viewModel.checkOfficerIdAvailability(regDepartment, regOfficerId)
            when (res) {
                is OfficerDepartmentVerifier.GrantValidationResult.Denied -> {
                    availabilityStatus = res.reason
                    isIdTaken = true
                }
                is OfficerDepartmentVerifier.GrantValidationResult.Granted -> {
                    availabilityStatus = "Unique Officer ID available for this department"
                    isIdTaken = false
                }
            }
        } else {
            availabilityStatus = null
            isIdTaken = false
        }
    }

    Scaffold(
        containerColor = GovBg,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ================= 1. TOP NAVY EMBLEM & HEADER (Matches Page 1) =================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GovNavyHeader)
                    .padding(top = 36.dp, bottom = 28.dp, start = 20.dp, end = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Official Government Digital Forensics Law Enforcement Application Logo
                    Image(
                        painter = painterResource(id = com.sih.drugtestclassifier.R.drawable.app_logo),
                        contentDescription = "Government Digital Forensics Law Enforcement Logo",
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Fit,
                    )

                    Text(
                        text = "Digital Field Companion",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    Text(
                        text = "Government of Maharashtra · Narcotics Enforcement",
                        color = Color(0xFF9FB6D0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // ================= 2. TAB ROW (Sign In | Register Officer) =================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Sign In Tab
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedTab = 0
                                errorMessage = null
                                successMessage = null
                            }
                            .padding(top = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Sign In",
                            fontSize = 15.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (selectedTab == 0) GovNavy else Color(0xFF64748B),
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(if (selectedTab == 0) GovOrange else Color.Transparent),
                        )
                    }

                    // Register Officer Tab
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedTab = 1
                                errorMessage = null
                                successMessage = null
                            }
                            .padding(top = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Register Officer",
                            fontSize = 15.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (selectedTab == 1) GovNavy else Color(0xFF64748B),
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(if (selectedTab == 1) GovOrange else Color.Transparent),
                        )
                    }
                }
            }

            // ================= 3. FORM CARD CONTENT =================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (selectedTab == 0) {
                    // ================= SIGN IN TAB (Matches Page 1) =================
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GovBorder),
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            // Officer Email Field
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Officer Email",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B),
                                )
                                OutlinedTextField(
                                    value = signInEmail,
                                    onValueChange = {
                                        signInEmail = it
                                        errorMessage = null
                                    },
                                    placeholder = { Text("officer@narcotics.gov.in", color = Color(0xFF94A3B8)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GovNavy,
                                        unfocusedBorderColor = GovBorder,
                                    ),
                                )
                            }

                            // Password Field
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Password",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B),
                                )
                                OutlinedTextField(
                                    value = signInPassword,
                                    onValueChange = {
                                        signInPassword = it
                                        errorMessage = null
                                    },
                                    placeholder = { Text("Enter your password", color = Color(0xFF94A3B8)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true,
                                    visualTransformation = if (signInPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    trailingIcon = {
                                        IconButton(onClick = { signInPasswordVisible = !signInPasswordVisible }) {
                                            Icon(
                                                imageVector = if (signInPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                contentDescription = if (signInPasswordVisible) "Hide password" else "Show password",
                                                tint = Color(0xFF1E293B),
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GovNavy,
                                        unfocusedBorderColor = GovBorder,
                                    ),
                                )
                            }

                            // Forgot Password Link
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                Text(
                                    text = "Forgot Password?",
                                    color = GovNavy,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable {
                                        if (signInEmail.isBlank()) {
                                            errorMessage = "Please enter your officer email above to receive password reset."
                                        } else {
                                            coroutineScope.launch {
                                                isLoading = true
                                                val res = viewModel.sendPasswordReset(signInEmail)
                                                isLoading = false
                                                if (res.isSuccess) {
                                                    successMessage = "Password reset instructions sent to $signInEmail."
                                                } else {
                                                    errorMessage = res.exceptionOrNull()?.localizedMessage ?: "Failed to send reset link."
                                                }
                                            }
                                        }
                                    },
                                )
                            }

                            // Sign In Button
                            Button(
                                onClick = {
                                    if (signInEmail.isBlank() || signInPassword.isBlank()) {
                                        errorMessage = "Please enter officer email and password."
                                        return@Button
                                    }
                                    coroutineScope.launch {
                                        isLoading = true
                                        errorMessage = null
                                        val res = viewModel.firebaseSignIn(signInEmail, signInPassword)
                                        isLoading = false
                                        when (res) {
                                            is FirebaseAuthManager.AuthResult.Success -> onLoginSuccess()
                                            is FirebaseAuthManager.AuthResult.Error -> errorMessage = res.message
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GovNavy),
                                enabled = !isLoading,
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                                } else {
                                    Text(
                                        "Sign In",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                    }

                } else {
                    // ================= REGISTER OFFICER TAB (Matches Page 1 + User Requirements) =================
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GovBorder),
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                text = "REGISTER OFFICER — OFFICIAL ID ENROLLMENT",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF64748B),
                                letterSpacing = 0.8.sp,
                            )

                            // 1. Officer / Badge ID Field
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Officer / Badge ID",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B),
                                )
                                OutlinedTextField(
                                    value = regOfficerId,
                                    onValueChange = {
                                        regOfficerId = it
                                        errorMessage = null
                                    },
                                    placeholder = { Text("e.g. OFFICER-7421", color = Color(0xFF94A3B8)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true,
                                    isError = isIdTaken,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GovNavy,
                                        unfocusedBorderColor = GovBorder,
                                    ),
                                )
                                if (availabilityStatus != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(top = 2.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (isIdTaken) Icons.Filled.Cancel else Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = if (isIdTaken) MaterialTheme.colorScheme.error else GovGreen,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Text(
                                            text = availabilityStatus!!,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isIdTaken) MaterialTheme.colorScheme.error else GovGreen,
                                        )
                                    }
                                }
                            }

                            // 2. Officer Full Name Field
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Officer Full Name",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B),
                                )
                                OutlinedTextField(
                                    value = regOfficerName,
                                    onValueChange = {
                                        regOfficerName = it
                                        errorMessage = null
                                    },
                                    placeholder = { Text("e.g. Insp. R. Sharma", color = Color(0xFF94A3B8)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GovNavy,
                                        unfocusedBorderColor = GovBorder,
                                    ),
                                )
                            }

                            // 3. USER REQUIREMENT: GENDER BUTTONS (Male / Female / Other)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Gender",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    // Male Button
                                    val isMale = regGender == "Male"
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .clickable { regGender = "Male" },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isMale) GovNavy else Color.White,
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = if (isMale) 2.dp else 1.dp,
                                            color = if (isMale) GovNavy else GovBorder,
                                        ),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = "Male",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = if (isMale) Color.White else Color(0xFF334155),
                                            )
                                        }
                                    }

                                    // Female Button
                                    val isFemale = regGender == "Female"
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .clickable { regGender = "Female" },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isFemale) GovNavy else Color.White,
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = if (isFemale) 2.dp else 1.dp,
                                            color = if (isFemale) GovNavy else GovBorder,
                                        ),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = "Female",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = if (isFemale) Color.White else Color(0xFF334155),
                                            )
                                        }
                                    }

                                    // Other Button
                                    val isOther = regGender == "Other"
                                    Surface(
                                        modifier = Modifier
                                            .weight(0.8f)
                                            .height(44.dp)
                                            .clickable { regGender = "Other" },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isOther) GovNavy else Color.White,
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = if (isOther) 2.dp else 1.dp,
                                            color = if (isOther) GovNavy else GovBorder,
                                        ),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = "Other",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = if (isOther) Color.White else Color(0xFF334155),
                                            )
                                        }
                                    }
                                }
                            }

                            // 4. ESSENTIAL OFFICIAL DATA: Rank / Designation (Detective placed directly below Inspector)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Rank / Designation",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B),
                                )
                                // Row 1: Inspector (Left), Sub-Inspector (Right)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    listOf("Inspector", "Sub-Inspector").forEach { r ->
                                        val isSelected = regRank == r
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) GovNavy else Color.White,
                                            border = androidx.compose.foundation.BorderStroke(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) GovNavy else GovBorder,
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                                .clickable { regRank = r },
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = r,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) Color.White else Color(0xFF334155),
                                                )
                                            }
                                        }
                                    }
                                }
                                // Row 2: Detective (Left - directly below Inspector), DySP (Right)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    listOf("Detective", "DySP").forEach { r ->
                                        val isSelected = regRank == r
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) GovNavy else Color.White,
                                            border = androidx.compose.foundation.BorderStroke(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) GovNavy else GovBorder,
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                                .clickable { regRank = r },
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = r,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) Color.White else Color(0xFF334155),
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 5. Department / Unit Field
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Department / Unit",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B),
                                )
                                OutlinedTextField(
                                    value = regDepartment,
                                    onValueChange = {
                                        regDepartment = it
                                        errorMessage = null
                                    },
                                    placeholder = { Text("e.g. Narcotics Enforcement Unit - Nashik", color = Color(0xFF94A3B8)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GovNavy,
                                        unfocusedBorderColor = GovBorder,
                                    ),
                                )
                                Text(
                                    text = "The boundary within which your Officer ID is verified unique.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B),
                                )
                            }

                            // 6. ESSENTIAL OFFICIAL DATA: Police ID & District (Aligned in a clean horizontal line)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = "Police ID",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E293B),
                                        maxLines = 1,
                                    )
                                    OutlinedTextField(
                                        value = regServiceNumber,
                                        onValueChange = { regServiceNumber = it },
                                        placeholder = { Text("e.g. MH-POL-7421", color = Color(0xFF94A3B8), fontSize = 13.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = GovNavy,
                                            unfocusedBorderColor = GovBorder,
                                        ),
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = "District",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E293B),
                                        maxLines = 1,
                                    )
                                    OutlinedTextField(
                                        value = regDistrict,
                                        onValueChange = { regDistrict = it },
                                        placeholder = { Text("e.g. Nashik City", color = Color(0xFF94A3B8), fontSize = 13.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = GovNavy,
                                            unfocusedBorderColor = GovBorder,
                                        ),
                                    )
                                }
                            }

                            // 7. Official Phone Number
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Official Mobile / Contact",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B),
                                )
                                OutlinedTextField(
                                    value = regPhone,
                                    onValueChange = { regPhone = it },
                                    placeholder = { Text("e.g. +91 98230 44521", color = Color(0xFF94A3B8)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GovNavy,
                                        unfocusedBorderColor = GovBorder,
                                    ),
                                )
                            }

                            // 8. USER REQUIREMENT: EMAIL VERIFICATION BEFORE REGISTERING (Real Email Pipeline)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Official Government Email (Verification Mandatory)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OutlinedTextField(
                                        value = regEmail,
                                        onValueChange = {
                                            regEmail = it
                                            viewModel.resetEmailVerification()
                                            errorMessage = null
                                            emailVerificationError = null
                                            isOtpDispatched = false
                                            showOtpDialog = false
                                        },
                                        placeholder = { Text("e.g. officer@narcotics.gov.in", color = Color(0xFF94A3B8)) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = GovNavy,
                                            unfocusedBorderColor = GovBorder,
                                        ),
                                    )

                                    Button(
                                        onClick = {
                                            if (regEmail.isBlank() || !regEmail.contains("@") || !regEmail.contains(".")) {
                                                emailVerificationError = "Please enter a valid official government email to verify."
                                                return@Button
                                            }
                                            coroutineScope.launch {
                                                isSendingOtp = true
                                                emailVerificationError = null
                                                errorMessage = null
                                                val res = viewModel.sendEmailVerificationOtp(regEmail)
                                                isSendingOtp = false
                                                when (res) {
                                                    is EmailOtpService.SendResult.Success -> {
                                                        otpInput = ""
                                                        otpError = null
                                                        isOtpDispatched = true
                                                        showOtpDialog = true
                                                    }
                                                    is EmailOtpService.SendResult.Error -> {
                                                        emailVerificationError = res.message
                                                        errorMessage = res.message
                                                    }
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isEmailVerified) GovGreen else GovOrange,
                                        ),
                                        enabled = !isSendingOtp && !isEmailVerified,
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                    ) {
                                        if (isSendingOtp) {
                                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        } else {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                if (isEmailVerified) {
                                                    Icon(
                                                        imageVector = Icons.Filled.CheckCircle,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                                Text(
                                                    text = if (isEmailVerified) "Verified" else "Verify",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = Color.White,
                                                )
                                            }
                                        }
                                    }
                                }

                                if (emailVerificationError != null) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFFEF2F2),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            text = emailVerificationError!!,
                                            color = Color(0xFFB91C1C),
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp,
                                            modifier = Modifier.padding(10.dp),
                                        )
                                    }
                                }

                                // Dedicated inline OTP submission card right on the registration page
                                if (isOtpDispatched && !isEmailVerified) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFF8FAFC),
                                        border = androidx.compose.foundation.BorderStroke(1.5.dp, GovOrange),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Email,
                                                    contentDescription = "Email",
                                                    tint = GovNavy,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                                Text(
                                                    "OTP Code Dispatched to $regEmail",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = GovNavy,
                                                )
                                            }

                                            Text(
                                                "Please enter the 6-digit verification code sent to your email:",
                                                fontSize = 12.sp,
                                                color = Color(0xFF334155),
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                OutlinedTextField(
                                                    value = otpInput,
                                                    onValueChange = {
                                                        if (it.length <= 6) {
                                                            otpInput = it
                                                            otpError = null
                                                        }
                                                    },
                                                    placeholder = { Text("e.g. 123456") },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = GovNavy,
                                                    ),
                                                )

                                                Button(
                                                    onClick = {
                                                        if (otpInput.trim().length != 6) {
                                                            otpError = "Please enter the complete 6-digit code."
                                                            return@Button
                                                        }
                                                        coroutineScope.launch {
                                                            isVerifyingOtp = true
                                                            otpError = null
                                                            val verifyResult = viewModel.verifyEmailOtp(regEmail, otpInput)
                                                            isVerifyingOtp = false
                                                            when (verifyResult) {
                                                                is EmailOtpService.VerifyResult.Success -> {
                                                                    showOtpDialog = false
                                                                    isOtpDispatched = false
                                                                    emailVerificationError = null
                                                                    successMessage = "Official email ($regEmail) verified successfully."
                                                                }
                                                                is EmailOtpService.VerifyResult.Invalid -> {
                                                                    otpError = verifyResult.message
                                                                }
                                                                is EmailOtpService.VerifyResult.Expired -> {
                                                                    otpError = verifyResult.message
                                                                }
                                                                is EmailOtpService.VerifyResult.RateLimited -> {
                                                                    otpError = verifyResult.message
                                                                }
                                                            }
                                                        }
                                                    },
                                                    enabled = !isVerifyingOtp,
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = GovNavy),
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                                ) {
                                                    if (isVerifyingOtp) {
                                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                    } else {
                                                        Text("Submit OTP", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    }
                                                }
                                            }

                                            if (otpError != null) {
                                                Text(
                                                    text = otpError!!,
                                                    color = MaterialTheme.colorScheme.error,
                                                    fontSize = 12.sp,
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                if (!canResendOtp) {
                                                    Text(
                                                        "Resend code in ${otpTimer}s",
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF64748B),
                                                    )
                                                } else {
                                                    Text(
                                                        "Resend Code",
                                                        fontSize = 12.sp,
                                                        color = GovNavy,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.clickable {
                                                            coroutineScope.launch {
                                                                isSendingOtp = true
                                                                val res = viewModel.sendEmailVerificationOtp(regEmail)
                                                                isSendingOtp = false
                                                                when (res) {
                                                                    is EmailOtpService.SendResult.Success -> {
                                                                        otpInput = ""
                                                                        otpError = null
                                                                        otpTimer = 30
                                                                        canResendOtp = false
                                                                    }
                                                                    is EmailOtpService.SendResult.Error -> {
                                                                        otpError = res.message
                                                                    }
                                                                }
                                                            }
                                                        },
                                                    )
                                                }

                                                Text(
                                                    "Open Popup Modal",
                                                    fontSize = 12.sp,
                                                    color = GovOrange,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.clickable {
                                                        showOtpDialog = true
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }

                                // Email verification status badge
                                if (isEmailVerified) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = GovGreenBg,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC3E6CB)),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = null,
                                                tint = GovGreen,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                "Official Email Verified: $regEmail",
                                                color = GovGreen,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Warning,
                                            contentDescription = "Warning",
                                            tint = Color(0xFFB45309),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            "Email verification is required before official registration can be approved.",
                                            fontSize = 12.sp,
                                            color = Color(0xFFB45309),
                                        )
                                    }
                                }
                            }

                            // 9. Password & Confirm Password
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Password (Min 6 Characters)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B),
                                )
                                OutlinedTextField(
                                    value = regPassword,
                                    onValueChange = { regPassword = it },
                                    placeholder = { Text("Minimum 6 characters", color = Color(0xFF94A3B8)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true,
                                    visualTransformation = if (regPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    trailingIcon = {
                                        IconButton(onClick = { regPasswordVisible = !regPasswordVisible }) {
                                            Icon(
                                                imageVector = if (regPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                contentDescription = if (regPasswordVisible) "Hide password" else "Show password",
                                                tint = Color(0xFF1E293B),
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GovNavy,
                                        unfocusedBorderColor = GovBorder,
                                    ),
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Confirm Password",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B),
                                )
                                OutlinedTextField(
                                    value = regConfirmPassword,
                                    onValueChange = { regConfirmPassword = it },
                                    placeholder = { Text("Re-enter password", color = Color(0xFF94A3B8)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true,
                                    visualTransformation = if (regConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    trailingIcon = {
                                        IconButton(onClick = { regConfirmPasswordVisible = !regConfirmPasswordVisible }) {
                                            Icon(
                                                imageVector = if (regConfirmPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                contentDescription = if (regConfirmPasswordVisible) "Hide password" else "Show password",
                                                tint = Color(0xFF1E293B),
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GovNavy,
                                        unfocusedBorderColor = GovBorder,
                                    ),
                                )
                            }

                            // Register Officer Button
                            Button(
                                onClick = {
                                    if (regEmail.isBlank() || regPassword.isBlank() || regOfficerId.isBlank() || regOfficerName.isBlank() || regDepartment.isBlank()) {
                                        errorMessage = "Please fill in all mandatory officer credentials."
                                        return@Button
                                    }
                                    if (!isEmailVerified) {
                                        errorMessage = "Please complete official email verification before registering."
                                        return@Button
                                    }
                                    if (regPassword != regConfirmPassword && regConfirmPassword.isNotBlank()) {
                                        errorMessage = "Passwords do not match."
                                        return@Button
                                    }
                                    if (isIdTaken) {
                                        errorMessage = availabilityStatus ?: "Officer ID is already taken in this department."
                                        return@Button
                                    }

                                    coroutineScope.launch {
                                        isLoading = true
                                        errorMessage = null
                                        val res = viewModel.firebaseRegister(
                                            email = regEmail,
                                            pass = regPassword,
                                            badgeId = regOfficerId,
                                            name = regOfficerName,
                                            dept = regDepartment,
                                            gender = regGender,
                                            rank = regRank,
                                            serviceNumber = regServiceNumber,
                                            district = regDistrict,
                                            phone = regPhone,
                                            isEmailVerified = true,
                                        )
                                        isLoading = false
                                        when (res) {
                                            is FirebaseAuthManager.AuthResult.Success -> onLoginSuccess()
                                            is FirebaseAuthManager.AuthResult.Error -> errorMessage = res.message
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GovNavy),
                                enabled = !isLoading,
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                                } else {
                                    Text(
                                        "Register Officer Account",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                    }
                }

                // Error Feedback Card
                if (errorMessage != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFEE2E2),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)),
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = Color(0xFFB91C1C),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }

                // Success Feedback Card
                if (successMessage != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = GovGreenBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF86EFAC)),
                    ) {
                        Text(
                            text = successMessage!!,
                            color = GovGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }

                // Bottom Cryptographic Audit Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 20.dp),
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
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // ================= 4. EMAIL VERIFICATION OTP MODAL =================
        if (showOtpDialog) {
            AlertDialog(
                onDismissRequest = { showOtpDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Email,
                            contentDescription = "Email",
                            tint = GovNavy,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            "Officer Email Verification",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = GovNavy,
                        )
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "To ensure official law enforcement identity, an official 6-digit verification code has been dispatched to your email:",
                            fontSize = 13.sp,
                            color = Color(0xFF334155),
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF1F5F9),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = regEmail,
                                fontWeight = FontWeight.Bold,
                                color = GovNavy,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(10.dp),
                            )
                        }

                        Text(
                            text = "Please check your inbox (and spam folder), then enter the 6-digit OTP code below:",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                        )

                        OutlinedTextField(
                            value = otpInput,
                            onValueChange = {
                                if (it.length <= 6) {
                                    otpInput = it
                                    otpError = null
                                }
                            },
                            label = { Text("Enter 6-Digit Code") },
                            placeholder = { Text("e.g. 123456") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GovNavy,
                            ),
                        )

                        if (otpError != null) {
                            Text(
                                text = otpError!!,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!canResendOtp) {
                                Text(
                                    "Resend in ${otpTimer}s",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B),
                                )
                            } else {
                                Text(
                                    "Resend Code",
                                    fontSize = 12.sp,
                                    color = GovNavy,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        coroutineScope.launch {
                                            isSendingOtp = true
                                            val res = viewModel.sendEmailVerificationOtp(regEmail)
                                            isSendingOtp = false
                                            when (res) {
                                                is EmailOtpService.SendResult.Success -> {
                                                    otpInput = ""
                                                    otpError = null
                                                    otpTimer = 30
                                                    canResendOtp = false
                                                }
                                                is EmailOtpService.SendResult.Error -> {
                                                    otpError = res.message
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (otpInput.trim().length != 6) {
                                otpError = "Please enter the complete 6-digit verification code."
                                return@Button
                            }
                            coroutineScope.launch {
                                isVerifyingOtp = true
                                otpError = null
                                val verifyResult = viewModel.verifyEmailOtp(regEmail, otpInput)
                                isVerifyingOtp = false
                                when (verifyResult) {
                                    is EmailOtpService.VerifyResult.Success -> {
                                        showOtpDialog = false
                                        isOtpDispatched = false
                                        emailVerificationError = null
                                        successMessage = "Official government email ($regEmail) verified successfully."
                                    }
                                    is EmailOtpService.VerifyResult.Invalid -> {
                                        otpError = verifyResult.message
                                    }
                                    is EmailOtpService.VerifyResult.Expired -> {
                                        otpError = verifyResult.message
                                    }
                                    is EmailOtpService.VerifyResult.RateLimited -> {
                                        otpError = verifyResult.message
                                    }
                                }
                            }
                        },
                        enabled = !isVerifyingOtp,
                        colors = ButtonDefaults.buttonColors(containerColor = GovNavy),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        if (isVerifyingOtp) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Verify Email", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showOtpDialog = false }) {
                        Text("Cancel", color = Color(0xFF64748B))
                    }
                },
            )
        }
    }
}
