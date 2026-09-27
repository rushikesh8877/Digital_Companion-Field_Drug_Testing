package app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ui.data.DemoTestViewModel
import com.sih.drugtestclassifier.auth.FirebaseAuthManager
import com.sih.drugtestclassifier.auth.OfficerDepartmentVerifier
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: DemoTestViewModel,
    onLoginSuccess: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Sign In, 1 = Register

    // Form fields
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Registration extra fields (Officer provides their own ID & Department)
    var officerId by remember { mutableStateOf("OFFICER-7421") }
    var officerName by remember { mutableStateOf("Insp. R. Sharma") }
    var department by remember { mutableStateOf("Narcotics Enforcement Unit - Nashik") }

    // Real-time departmental uniqueness validation
    var availabilityStatus by remember { mutableStateOf<String?>(null) }
    var isIdTaken by remember { mutableStateOf(false) }

    // UI state
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    // Check departmental uniqueness whenever ID or department is modified during registration
    LaunchedEffect(officerId, department, selectedTab) {
        if (selectedTab == 1 && officerId.isNotBlank() && department.isNotBlank()) {
            val res = viewModel.checkOfficerIdAvailability(department, officerId)
            when (res) {
                is OfficerDepartmentVerifier.GrantValidationResult.Denied -> {
                    availabilityStatus = res.reason
                    isIdTaken = true
                }
                is OfficerDepartmentVerifier.GrantValidationResult.Granted -> {
                    availabilityStatus = "✓ Unique Officer ID available for this department"
                    isIdTaken = false
                }
            }
        } else {
            availabilityStatus = null
            isIdTaken = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Forensic Digital Companion",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Forensic Shield Header Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(54.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("🛡️", fontSize = 26.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "OFFICER AUTHENTICATION",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Strict Departmental Officer ID Uniqueness · Cloud Cryptographic Chain of Custody",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Authentication Mode Tabs
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = {
                        selectedTab = 0
                        errorMessage = null
                        successMessage = null
                    },
                    text = { Text("Sign In", fontWeight = FontWeight.SemiBold) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        errorMessage = null
                        successMessage = null
                    },
                    text = { Text("Register Officer", fontWeight = FontWeight.SemiBold) },
                )
            }

            // Input Fields based on Selected Tab
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    errorMessage = null
                    successMessage = null
                },
                label = { Text("Officer Email") },
                placeholder = { Text("officer@narcotics.gov.in") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = null
                    successMessage = null
                },
                label = { Text("Password") },
                placeholder = { Text(if (selectedTab == 1) "Minimum 6 characters" else "Enter password") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Text(if (passwordVisible) "🙈" else "👁️")
                    }
                },
            )

            if (selectedTab == 1) {
                // Officer Registration Details (ID provided by officer)
                OutlinedTextField(
                    value = officerId,
                    onValueChange = {
                        officerId = it
                        errorMessage = null
                    },
                    label = { Text("Officer / Badge ID (Unique in Dept)") },
                    placeholder = { Text("e.g. OFFICER-7421") },
                    supportingText = {
                        Text("Each officer must provide their unique ID. No two officers can share the same ID in the same department.")
                    },
                    isError = isIdTaken,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                )

                // Real-time Availability & Uniqueness Indicator
                if (availabilityStatus != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isIdTaken) MaterialTheme.colorScheme.errorContainer else Color(0xFFE8F5E9),
                        ),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = availabilityStatus!!,
                            modifier = Modifier.padding(10.dp),
                            color = if (isIdTaken) MaterialTheme.colorScheme.onErrorContainer else Color(0xFF2E7D32),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        )
                    }
                }

                OutlinedTextField(
                    value = officerName,
                    onValueChange = {
                        officerName = it
                        errorMessage = null
                    },
                    label = { Text("Officer Full Name") },
                    placeholder = { Text("e.g. Insp. R. Sharma") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = department,
                    onValueChange = {
                        department = it
                        errorMessage = null
                    },
                    label = { Text("Department / Unit") },
                    placeholder = { Text("e.g. Narcotics Enforcement Unit - Nashik") },
                    supportingText = {
                        Text("Department boundary within which your Officer ID is verified unique.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                )
            }

            // Error Feedback Message
            if (errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        errorMessage!!,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // Success Feedback Message
            if (successMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        successMessage!!,
                        modifier = Modifier.padding(12.dp),
                        color = Color(0xFF2E7D32),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // Forgot Password (Sign In tab only)
            if (selectedTab == 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            if (email.isBlank()) {
                                errorMessage = "Please enter your email above to receive a password reset link."
                            } else {
                                coroutineScope.launch {
                                    isLoading = true
                                    errorMessage = null
                                    successMessage = null
                                    val res = viewModel.sendPasswordReset(email)
                                    isLoading = false
                                    if (res.isSuccess) {
                                        successMessage = "Password reset instructions sent to $email."
                                    } else {
                                        errorMessage = res.exceptionOrNull()?.localizedMessage ?: "Failed to send reset email."
                                    }
                                }
                            }
                        },
                        enabled = !isLoading,
                    ) {
                        Text("Forgot Password?", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Primary Firebase Action Button
            Button(
                onClick = {
                    if (selectedTab == 0) {
                        // Sign In
                        if (email.isBlank() || password.isBlank()) {
                            errorMessage = "Please enter both email and password."
                            return@Button
                        }
                        coroutineScope.launch {
                            isLoading = true
                            errorMessage = null
                            successMessage = null
                            val res = viewModel.firebaseSignIn(email, password)
                            isLoading = false
                            when (res) {
                                is FirebaseAuthManager.AuthResult.Success -> {
                                    onLoginSuccess()
                                }
                                is FirebaseAuthManager.AuthResult.Error -> {
                                    errorMessage = res.message
                                }
                            }
                        }
                    } else {
                        // Register
                        if (email.isBlank() || password.isBlank() || officerId.isBlank() || officerName.isBlank() || department.isBlank()) {
                            errorMessage = "Please fill in all required registration fields."
                            return@Button
                        }
                        if (isIdTaken) {
                            errorMessage = availabilityStatus ?: "Officer ID '$officerId' is already registered in '$department'. Each officer must have a unique ID within their department."
                            return@Button
                        }
                        coroutineScope.launch {
                            isLoading = true
                            errorMessage = null
                            successMessage = null
                            val res = viewModel.firebaseRegister(email, password, officerId, officerName, department)
                            isLoading = false
                            when (res) {
                                is FirebaseAuthManager.AuthResult.Success -> {
                                    onLoginSuccess()
                                }
                                is FirebaseAuthManager.AuthResult.Error -> {
                                    errorMessage = res.message
                                }
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        if (selectedTab == 0) "Sign In with Firebase" else "Register Officer Account",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }

            // Offline / Demo Mode Button
            OutlinedButton(
                onClick = {
                    val success = viewModel.login("OFFICER-7421", "Insp. R. Sharma", "1234", "Narcotics Enforcement Unit - Nashik")
                    if (success) onLoginSuccess()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading,
            ) {
                Text("Quick Demo Login (Insp. Sharma - Offline)")
            }

            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "🔒 Unique ID per Department · Verified Cryptographic Chain of Custody",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
