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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
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
import com.sih.drugtestclassifier.auth.EmailOtpConfig
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

    // States and Districts mapping
    val statesAndDistrictsMap = remember {
        mapOf(
            "Maharashtra" to listOf(
                "Nashik", "Mumbai City", "Mumbai Suburban", "Pune", "Thane", "Nagpur",
                "Chhatrapati Sambhajinagar (Aurangabad)", "Ahilyanagar (Ahmednagar)", "Amravati",
                "Akola", "Beed", "Bhandara", "Buldhana", "Chandrapur", "Dhule", "Gadchiroli",
                "Gondia", "Hingoli", "Jalgaon", "Jalna", "Kolhapur", "Latur", "Nanded",
                "Nandurbar", "Palghar", "Parbhani", "Raigad", "Ratnagiri", "Sangli", "Satara",
                "Sindhudurg", "Solapur", "Wardha", "Washim", "Yavatmal",
            ),
            "Goa" to listOf("North Goa", "South Goa"),
            "Gujarat" to listOf(
                "Ahmedabad", "Surat", "Vadodara", "Rajkot", "Bhavnagar", "Jamnagar",
                "Gandhinagar", "Junagadh", "Anand", "Bharuch", "Kutch", "Mehsana", "Patan", "Valsad",
            ),
            "Karnataka" to listOf(
                "Bengaluru Urban", "Bengaluru Rural", "Mysuru", "Mangaluru (Dakshina Kannada)",
                "Hubballi-Dharwad", "Belagavi", "Kalaburagi", "Ballari", "Shivamogga", "Tumakuru", "Udupi",
            ),
            "Madhya Pradesh" to listOf(
                "Bhopal", "Indore", "Gwalior", "Jabalpur", "Ujjain", "Sagar", "Satna", "Rewa", "Ratlam", "Vidisha",
            ),
            "Delhi (UT)" to listOf(
                "New Delhi", "Central Delhi", "East Delhi", "North Delhi", "South Delhi", "West Delhi",
            ),
            "Telangana" to listOf("Hyderabad", "Rangareddy", "Medchal-Malkajgiri", "Warangal", "Nizamabad", "Karimnagar"),
            "Tamil Nadu" to listOf("Chennai", "Coimbatore", "Madurai", "Tiruchirappalli", "Salem", "Tirunelveli"),
            "Uttar Pradesh" to listOf("Lucknow", "Kanpur", "Varanasi", "Agra", "Gautam Buddha Nagar (Noida)", "Ghaziabad", "Prayagraj"),
            "West Bengal" to listOf("Kolkata", "Howrah", "North 24 Parganas", "South 24 Parganas", "Darjeeling", "Siliguri"),
        )
    }

    var selectedState by remember { mutableStateOf("Maharashtra") }
    var regDistrict by remember { mutableStateOf("Nashik") }
    var stateDropdownExpanded by remember { mutableStateOf(false) }
    var districtDropdownExpanded by remember { mutableStateOf(false) }

    // Preset Departments list (Departments handling narcotics, field drug testing, and interdiction operations)
    val presetDepartments = remember {
        listOf(
            "Narcotics Enforcement Unit",
            "Narcotics Control Bureau (NCB)",
            "Anti-Narcotics Task Force (ANTF)",
            "Anti-Narcotics Cell (ANC)",
            "State Police Department",
            "Crime Branch / Special Cell",
            "State Crime Investigation Department (CID)",
            "State Excise & Prohibition Department",
            "Directorate of Revenue Intelligence (DRI)",
            "Central Bureau of Narcotics (CBN)",
            "Forensic Science Laboratory (FSL)",
            "Customs & Border Control",
            "Railway Protection Force (RPF)",
            "Special Task Force (STF)",
            "Other (Type manually)",
        )
    }
    var selectedDepartmentPreset by remember { mutableStateOf("Narcotics Enforcement Unit") }
    var customDepartmentName by remember { mutableStateOf("") }
    var departmentDropdownExpanded by remember { mutableStateOf(false) }

    // Official Registration fields
    var regOfficerId by remember { mutableStateOf("") }
    var regOfficerName by remember { mutableStateOf("") }
    var regDepartment by remember { mutableStateOf("Narcotics Enforcement Unit") }
    var regGender by remember { mutableStateOf("Male") }
    var regRank by remember { mutableStateOf("Inspector") }
    var regServiceNumber by remember { mutableStateOf("") }
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
    var showDuplicateOfficerPopup by remember { mutableStateOf(false) }

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

    // Check departmental & district uniqueness whenever ID, department, or district is modified during registration
    LaunchedEffect(regOfficerId, regDepartment, regDistrict, selectedTab) {
        val effectiveDeptBoundary = if (regDistrict.isNotBlank()) "$regDepartment - $regDistrict" else regDepartment
        if (selectedTab == 1 && regOfficerId.isNotBlank() && effectiveDeptBoundary.isNotBlank()) {
            val res = viewModel.checkOfficerIdAvailability(effectiveDeptBoundary, regOfficerId)
            when (res) {
                is OfficerDepartmentVerifier.GrantValidationResult.Denied -> {
                    availabilityStatus = "Officer ID is already registered in this department."
                    isIdTaken = true
                    showDuplicateOfficerPopup = true
                }
                is OfficerDepartmentVerifier.GrantValidationResult.Granted -> {
                    availabilityStatus = "Officer ID is available."
                    isIdTaken = false
                    showDuplicateOfficerPopup = false
                }
            }
        } else {
            availabilityStatus = null
            isIdTaken = false
            showDuplicateOfficerPopup = false
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
                                    placeholder = { Text("OFFICER-7421", color = Color(0xFF94A3B8)) },
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
                                    placeholder = { Text("Insp. R. Sharma", color = Color(0xFF94A3B8)) },
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

                            // 5. STATE & DISTRICT SELECTION (Filtered Districts by Selected State)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                // State Dropdown Picker
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = "State",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E293B),
                                        maxLines = 1,
                                    )
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        OutlinedTextField(
                                            value = selectedState,
                                            onValueChange = {},
                                            readOnly = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { stateDropdownExpanded = !stateDropdownExpanded },
                                            shape = RoundedCornerShape(8.dp),
                                            trailingIcon = {
                                                IconButton(onClick = { stateDropdownExpanded = !stateDropdownExpanded }) {
                                                    Icon(
                                                        imageVector = if (stateDropdownExpanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                                                        contentDescription = "Select State",
                                                    )
                                                }
                                            },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = GovNavy,
                                                unfocusedBorderColor = GovBorder,
                                            ),
                                        )
                                        DropdownMenu(
                                            expanded = stateDropdownExpanded,
                                            onDismissRequest = { stateDropdownExpanded = false },
                                            modifier = Modifier.fillMaxWidth(0.45f),
                                        ) {
                                            statesAndDistrictsMap.keys.forEach { stateName ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = stateName,
                                                            fontSize = 13.sp,
                                                            fontWeight = if (stateName == selectedState) FontWeight.Bold else FontWeight.Normal,
                                                        )
                                                    },
                                                    onClick = {
                                                        selectedState = stateName
                                                        stateDropdownExpanded = false
                                                        val districts = statesAndDistrictsMap[stateName].orEmpty()
                                                        if (districts.isNotEmpty() && !districts.contains(regDistrict)) {
                                                            regDistrict = districts.first()
                                                        }
                                                        errorMessage = null
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }

                                // District Dropdown Picker (Filtered to selected State)
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
                                    val currentDistricts = statesAndDistrictsMap[selectedState] ?: listOf("Nashik")
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        OutlinedTextField(
                                            value = regDistrict,
                                            onValueChange = {},
                                            readOnly = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { districtDropdownExpanded = !districtDropdownExpanded },
                                            shape = RoundedCornerShape(8.dp),
                                            trailingIcon = {
                                                IconButton(onClick = { districtDropdownExpanded = !districtDropdownExpanded }) {
                                                    Icon(
                                                        imageVector = if (districtDropdownExpanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                                                        contentDescription = "Select District",
                                                    )
                                                }
                                            },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = GovNavy,
                                                unfocusedBorderColor = GovBorder,
                                            ),
                                        )
                                        DropdownMenu(
                                            expanded = districtDropdownExpanded,
                                            onDismissRequest = { districtDropdownExpanded = false },
                                            modifier = Modifier.fillMaxWidth(0.45f),
                                        ) {
                                            currentDistricts.forEach { distName ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = distName,
                                                            fontSize = 13.sp,
                                                            fontWeight = if (distName == regDistrict) FontWeight.Bold else FontWeight.Normal,
                                                        )
                                                    },
                                                    onClick = {
                                                        regDistrict = distName
                                                        districtDropdownExpanded = false
                                                        errorMessage = null
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 6. Department / Unit Dropdown Picker with Custom Option
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Department / Unit",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B),
                                )
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = selectedDepartmentPreset,
                                        onValueChange = {},
                                        readOnly = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { departmentDropdownExpanded = !departmentDropdownExpanded },
                                        shape = RoundedCornerShape(8.dp),
                                        trailingIcon = {
                                            IconButton(onClick = { departmentDropdownExpanded = !departmentDropdownExpanded }) {
                                                Icon(
                                                    imageVector = if (departmentDropdownExpanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                                                    contentDescription = "Select Department",
                                                )
                                            }
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = GovNavy,
                                            unfocusedBorderColor = GovBorder,
                                        ),
                                    )
                                    DropdownMenu(
                                        expanded = departmentDropdownExpanded,
                                        onDismissRequest = { departmentDropdownExpanded = false },
                                        modifier = Modifier.fillMaxWidth(0.85f),
                                    ) {
                                        presetDepartments.forEach { dept ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = dept,
                                                        fontSize = 14.sp,
                                                        fontWeight = if (dept == selectedDepartmentPreset) FontWeight.Bold else FontWeight.Normal,
                                                    )
                                                },
                                                onClick = {
                                                    selectedDepartmentPreset = dept
                                                    departmentDropdownExpanded = false
                                                    if (dept != "Other (Type manually)") {
                                                        regDepartment = dept
                                                    } else {
                                                        regDepartment = customDepartmentName
                                                    }
                                                    errorMessage = null
                                                },
                                            )
                                        }
                                    }
                                }

                                if (selectedDepartmentPreset == "Other (Type manually)") {
                                    OutlinedTextField(
                                        value = customDepartmentName,
                                        onValueChange = {
                                            customDepartmentName = it
                                            regDepartment = it
                                            errorMessage = null
                                        },
                                        placeholder = { Text("Enter Department Name", color = Color(0xFF94A3B8)) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = GovNavy,
                                            unfocusedBorderColor = GovBorder,
                                        ),
                                    )
                                }

                                Text(
                                    text = "Unique Officer ID boundary enforced within $regDepartment ($regDistrict).",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B),
                                )
                            }

                            // 7. Official Police ID Number
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Police ID / Service Number",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B),
                                )
                                OutlinedTextField(
                                    value = regServiceNumber,
                                    onValueChange = { regServiceNumber = it },
                                    placeholder = { Text("MH-POL-7421", color = Color(0xFF94A3B8)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GovNavy,
                                        unfocusedBorderColor = GovBorder,
                                    ),
                                )
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
                                    placeholder = { Text("+91 98230 44521", color = Color(0xFF94A3B8)) },
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
                                        placeholder = { Text("officer@narcotics.gov.in", color = Color(0xFF94A3B8)) },
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
                                            text = toShortUserFriendlyError(emailVerificationError),
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
                                                    placeholder = { Text("123456") },
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
                                                    text = toShortUserFriendlyError(otpError),
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
                                        errorMessage = availabilityStatus ?: "Officer ID is already registered in this department."
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

                // Duplicate Officer ID Popup Modal
                if (showDuplicateOfficerPopup && isIdTaken) {
                    AlertDialog(
                        onDismissRequest = { showDuplicateOfficerPopup = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = Color.White,
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFEE2E2),
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.Warning,
                                            contentDescription = "Alert",
                                            tint = Color(0xFFDC2626),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "Officer ID Registered",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = GovNavy,
                                )
                            }
                        },
                        text = {
                            Text(
                                text = "Officer ID is already registered in this department.",
                                fontSize = 14.sp,
                                color = Color(0xFF334155),
                                lineHeight = 20.sp,
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = { showDuplicateOfficerPopup = false },
                                colors = ButtonDefaults.buttonColors(containerColor = GovNavy),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("OK", fontWeight = FontWeight.Bold)
                            }
                        },
                    )
                }

                // Short, Easy-to-Understand Error Popup Modal
                if (errorMessage != null) {
                    AlertDialog(
                        onDismissRequest = { errorMessage = null },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = Color.White,
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFEE2E2),
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.Warning,
                                            contentDescription = "Alert",
                                            tint = Color(0xFFDC2626),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "Notice",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = GovNavy,
                                )
                            }
                        },
                        text = {
                            Text(
                                text = toShortUserFriendlyError(errorMessage),
                                fontSize = 14.sp,
                                color = Color(0xFF334155),
                                lineHeight = 20.sp,
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = { errorMessage = null },
                                colors = ButtonDefaults.buttonColors(containerColor = GovNavy),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("OK", fontWeight = FontWeight.Bold)
                            }
                        },
                    )
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
                            text = toShortUserFriendlyError(errorMessage),
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
                            placeholder = { Text("123456") },
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
                                text = toShortUserFriendlyError(otpError),
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

/**
 * Converts raw technical error strings into short, easy-to-understand user messages.
 */
private fun toShortUserFriendlyError(rawMessage: String?): String {
    if (rawMessage.isNullOrBlank()) return "An unexpected error occurred. Please try again."
    val msg = rawMessage.trim()
    val lower = msg.lowercase()

    return when {
        // Network / Connectivity Errors
        lower.contains("unable to resolve host") ||
            lower.contains("no address associated") ||
            lower.contains("unknownhostexception") ||
            lower.contains("sockettimeoutexception") ||
            lower.contains("connectexception") ||
            lower.contains("network") ||
            lower.contains("internet") ||
            lower.contains("offline") ||
            lower.contains("failed to connect") ||
            lower.contains("check your internet") ->
            "No internet connection. Please check your network and try again."

        // Sign In Credentials
        lower.contains("invalid-credential") ||
            lower.contains("invalid_login_credentials") ||
            lower.contains("invalid credentials") ||
            lower.contains("wrong password") ||
            lower.contains("invalid password") ->
            "Incorrect email or password. Please try again."

        lower.contains("no officer account found") ||
            lower.contains("user-not-found") ||
            lower.contains("account not found") ->
            "No officer account found with this email address."

        // Registration & Account Conflicts
        lower.contains("account already exists") ||
            lower.contains("usercollision") ||
            lower.contains("email-already-in-use") ->
            "An account with this email address already exists."

        lower.contains("password is too weak") ||
            lower.contains("weakpassword") ||
            lower.contains("at least 6 characters") ->
            "Password is too weak. Please use at least 6 characters."

        lower.contains("email address format is invalid") ||
            lower.contains("invalid email") ||
            lower.contains("invalid-email") ->
            "Please enter a valid official email address."

        // Officer Badge ID Conflicts
        lower.contains("already registered") ||
            lower.contains("already taken") ||
            lower.contains("id is already taken") ->
            "Officer ID is already registered in this department."

        // Rate Limiting
        lower.contains("too-many-requests") ||
            lower.contains("rate limit") ||
            lower.contains("rate-limited") ||
            lower.contains("throttled") ||
            lower.contains("quota") ||
            lower.contains("maximum attempts exceeded") ->
            "Too many attempts. Please wait 1-2 minutes and try again."

        // OTP Code Validation
        lower.contains("invalid verification code") ||
            lower.contains("invalid code") ||
            lower.contains("incorrect code") ->
            "Invalid verification code. Please check your email and try again."

        lower.contains("expired") ->
            "Verification code has expired. Please request a new code."

        // Form Validation
        lower.contains("email and password are required") ||
            lower.contains("please enter officer email and password") ||
            lower.contains("fill in all mandatory") ||
            lower.contains("both email and password") ->
            "Please fill in all required credentials."

        lower.contains("passwords do not match") ->
            "Passwords do not match."

        lower.contains("complete official email verification") ||
            lower.contains("verify your email") ->
            "Please verify your email address before registering."

        // Email Dispatch Errors
        lower.contains("brevo") || lower.contains("sender") || lower.contains("dispatch") || lower.contains("verification email") -> {
            if (msg.length <= 160 && !msg.contains("{") && !msg.contains("}")) {
                msg
            } else {
                "Unable to send verification email. Please try again shortly."
            }
        }

        else -> {
            if (msg.length <= 60 && !msg.contains("Exception") && !msg.contains("http", ignoreCase = true) && !msg.contains("{") && !msg.contains(":")) {
                msg
            } else {
                "Operation failed. Please check your connection and try again."
            }
        }
    }
}
