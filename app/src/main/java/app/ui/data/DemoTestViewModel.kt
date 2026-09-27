package app.ui.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sih.drugtestclassifier.auth.EmailOtpService
import com.sih.drugtestclassifier.auth.FirebaseAuthManager
import com.sih.drugtestclassifier.auth.OfficerDepartmentVerifier
import com.sih.drugtestclassifier.location.LocationHelper
import com.sih.drugtestclassifier.models.ClassificationResult
import com.sih.drugtestclassifier.models.DigitalTestRecord
import com.sih.drugtestclassifier.models.TestImage
import com.sih.drugtestclassifier.security.VerificationResult
import com.sih.drugtestclassifier.sync.FirebaseSyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DemoTestViewModel(
    private val repository: TestRecordRepository = FakeTestRecordRepository(),
) : ViewModel() {

    // --- Officer Authentication Session ---
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _officerId = MutableStateFlow("OFFICER-7421")
    val officerId: StateFlow<String> = _officerId.asStateFlow()

    private val _officerName = MutableStateFlow("Insp. R. Sharma")
    val officerName: StateFlow<String> = _officerName.asStateFlow()

    private val _department = MutableStateFlow("Narcotics Enforcement Unit - Nashik")
    val department: StateFlow<String> = _department.asStateFlow()

    private val _gender = MutableStateFlow("Male")
    val gender: StateFlow<String> = _gender.asStateFlow()

    private val _rank = MutableStateFlow("Inspector")
    val rank: StateFlow<String> = _rank.asStateFlow()

    private val _serviceNumber = MutableStateFlow("MH-POL-7421")
    val serviceNumber: StateFlow<String> = _serviceNumber.asStateFlow()

    private val _district = MutableStateFlow("Nashik")
    val district: StateFlow<String> = _district.asStateFlow()

    private val _phone = MutableStateFlow("+91 98230 44521")
    val phone: StateFlow<String> = _phone.asStateFlow()

    // --- Official Email Verification System ---
    private val emailOtpService = EmailOtpService.getInstance()
    private val _isEmailVerified = MutableStateFlow(false)
    val isEmailVerified: StateFlow<Boolean> = _isEmailVerified.asStateFlow()

    suspend fun sendEmailVerificationOtp(targetEmail: String): EmailOtpService.SendResult {
        _isEmailVerified.value = false
        return emailOtpService.sendOtp(targetEmail)
    }

    suspend fun verifyEmailOtp(targetEmail: String, enteredOtp: String): EmailOtpService.VerifyResult {
        val result = emailOtpService.verifyOtp(targetEmail, enteredOtp)
        if (result is EmailOtpService.VerifyResult.Success) {
            _isEmailVerified.value = true
        }
        return result
    }

    fun resetEmailVerification() {
        _isEmailVerified.value = false
    }

    fun setEmailVerifiedManually(verified: Boolean) {
        _isEmailVerified.value = verified
    }

    // --- Officer ID & Department Uniqueness Verification ---
    private val _officerVerificationReport = MutableStateFlow<OfficerDepartmentVerifier.OfficerVerificationReport?>(null)
    val officerVerificationReport: StateFlow<OfficerDepartmentVerifier.OfficerVerificationReport?> = _officerVerificationReport.asStateFlow()

    // --- Active Test Pipeline State ---
    private val _image = MutableStateFlow<TestImage?>(null)
    val image: StateFlow<TestImage?> = _image.asStateFlow()

    private val _capturedLocation = MutableStateFlow<LocationHelper.LatLon?>(null)
    val capturedLocation: StateFlow<LocationHelper.LatLon?> = _capturedLocation.asStateFlow()

    private val _classification = MutableStateFlow<ClassificationResult?>(null)
    val classification: StateFlow<ClassificationResult?> = _classification.asStateFlow()

    private val _reason = MutableStateFlow<String?>(null)
    val reason: StateFlow<String?> = _reason.asStateFlow()

    private val _currentRecord = MutableStateFlow<DigitalTestRecord?>(null)
    val currentRecord: StateFlow<DigitalTestRecord?> = _currentRecord.asStateFlow()

    private val _records = MutableStateFlow(repository.getRecords())
    val records: StateFlow<List<DigitalTestRecord>> = _records.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private val _verification = MutableStateFlow<Boolean?>(null)
    val verification: StateFlow<Boolean?> = _verification.asStateFlow()

    private val _verificationDetail = MutableStateFlow<VerificationResult?>(null)
    val verificationDetail: StateFlow<VerificationResult?> = _verificationDetail.asStateFlow()

    // --- Cloud Sync Pipeline ---
    private var syncManager: FirebaseSyncManager? = null

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _pendingSyncCount = MutableStateFlow(0)
    val pendingSyncCount: StateFlow<Int> = _pendingSyncCount.asStateFlow()

    fun attachSyncManager(manager: FirebaseSyncManager) {
        this.syncManager = manager
        manager.setDataChangedListener {
            refreshRecords()
        }
        viewModelScope.launch {
            manager.isOnline.collect { _isOnline.value = it }
        }
        viewModelScope.launch {
            manager.isSyncing.collect { _isSyncing.value = it }
        }
        viewModelScope.launch {
            manager.pendingSyncCount.collect { _pendingSyncCount.value = it }
        }
        manager.startRealtimeSync()
        viewModelScope.launch {
            manager.triggerSync()
            refreshRecords()
        }
    }

    fun syncNow() = viewModelScope.launch {
        syncManager?.triggerSync()
        refreshRecords()
    }

    // --- Authentication Actions ---
    private var authManager: FirebaseAuthManager? = null

    fun attachAuthManager(manager: FirebaseAuthManager) {
        this.authManager = manager
        manager.getCurrentOfficer()?.let { profile ->
            _officerId.value = profile.badgeId
            _officerName.value = profile.name
            _department.value = profile.department
            _gender.value = profile.gender
            _rank.value = profile.rank
            _serviceNumber.value = profile.serviceNumber
            _district.value = profile.district
            _phone.value = profile.phone
            _isLoggedIn.value = true
        }
    }

    suspend fun checkOfficerIdAvailability(
        dept: String,
        id: String,
    ): OfficerDepartmentVerifier.GrantValidationResult {
        val manager = authManager ?: return OfficerDepartmentVerifier.GrantValidationResult.Granted()
        return manager.checkOfficerIdAvailability(dept, id)
    }

    suspend fun runOfficerVerification(): OfficerDepartmentVerifier.OfficerVerificationReport {
        val manager = authManager
        val report = if (manager != null) {
            manager.verifyAllOfficers()
        } else {
            OfficerDepartmentVerifier.verifyOfficerUniqueness(
                listOf(
                    OfficerDepartmentVerifier.OfficerRecord(
                        uid = "current-session",
                        email = "officer@narcotics.gov.in",
                        badgeId = _officerId.value,
                        name = _officerName.value,
                        department = _department.value,
                    ),
                ),
            )
        }
        _officerVerificationReport.value = report
        return report
    }

    fun clearOfficerVerificationReport() {
        _officerVerificationReport.value = null
    }

    suspend fun firebaseSignIn(email: String, pass: String): FirebaseAuthManager.AuthResult {
        val manager = authManager ?: return FirebaseAuthManager.AuthResult.Error("Firebase Auth manager not initialized.")
        val result = manager.signIn(email, pass)
        if (result is FirebaseAuthManager.AuthResult.Success) {
            _officerId.value = result.profile.badgeId
            _officerName.value = result.profile.name
            _department.value = result.profile.department
            _gender.value = result.profile.gender
            _rank.value = result.profile.rank
            _serviceNumber.value = result.profile.serviceNumber
            _district.value = result.profile.district
            _phone.value = result.profile.phone
            _isLoggedIn.value = true
        }
        return result
    }

    suspend fun firebaseRegister(
        email: String,
        pass: String,
        badgeId: String,
        name: String,
        dept: String,
        gender: String = "Male",
        rank: String = "Inspector",
        serviceNumber: String = "MH-POL-7421",
        district: String = "Nashik",
        phone: String = "+91 98230 44521",
        isEmailVerified: Boolean = true,
    ): FirebaseAuthManager.AuthResult {
        val manager = authManager ?: return FirebaseAuthManager.AuthResult.Error("Firebase Auth manager not initialized.")
        val result = manager.register(
            email = email,
            password = pass,
            badgeId = badgeId,
            name = name,
            department = dept,
            gender = gender,
            rank = rank,
            serviceNumber = serviceNumber,
            district = district,
            phone = phone,
            isEmailVerified = isEmailVerified,
        )
        if (result is FirebaseAuthManager.AuthResult.Success) {
            _officerId.value = result.profile.badgeId
            _officerName.value = result.profile.name
            _department.value = result.profile.department
            _gender.value = result.profile.gender
            _rank.value = result.profile.rank
            _serviceNumber.value = result.profile.serviceNumber
            _district.value = result.profile.district
            _phone.value = result.profile.phone
            _isLoggedIn.value = true
        }
        return result
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        val manager = authManager ?: return Result.failure(IllegalStateException("Firebase Auth manager not initialized."))
        return manager.resetPassword(email)
    }

    fun login(
        id: String,
        name: String,
        pin: String,
        department: String = "Narcotics Enforcement Unit - Nashik",
        gender: String = "Male",
        rank: String = "Inspector",
        serviceNumber: String = "MH-POL-7421",
        district: String = "Nashik",
        phone: String = "+91 98230 44521",
    ): Boolean {
        if (id.isBlank() || pin.length < 4) return false
        _officerId.value = id.trim()
        _officerName.value = name.trim().ifBlank { "Insp. R. Sharma" }
        _department.value = department.trim().ifBlank { "Narcotics Enforcement Unit - Nashik" }
        _gender.value = gender
        _rank.value = rank
        _serviceNumber.value = serviceNumber
        _district.value = district
        _phone.value = phone
        _isLoggedIn.value = true
        return true
    }

    fun logout() {
        authManager?.signOut()
        _isLoggedIn.value = false
        _currentRecord.value = null
        _image.value = null
        _saved.value = false
        _officerVerificationReport.value = null
    }

    fun setOperatorId(value: String) { _officerId.value = value }

    fun setLocation(loc: LocationHelper.LatLon) {
        _capturedLocation.value = loc
    }

    fun onImageCaptured(image: TestImage, location: LocationHelper.LatLon? = null) {
        _image.value = image
        if (location != null) {
            _capturedLocation.value = location
        }
        _classification.value = null
        _reason.value = null
        _saved.value = false
        _currentRecord.value = null
        _verification.value = null
        _verificationDetail.value = null
    }

    fun classify() = viewModelScope.launch {
        val image = _image.value ?: TestImage("mock://capture", System.currentTimeMillis()).also { _image.value = it }
        val result = repository.classify(image)
        _classification.value = result
        _reason.value = result.reason ?: (repository as? FakeTestRecordRepository)?.reasonFor(result)
        _currentRecord.value = repository.createRecord(
            operatorId = _officerId.value,
            image = image,
            classification = result,
            customLocation = _capturedLocation.value,
        )
    }

    fun save() {
        _currentRecord.value?.let {
            repository.saveRecord(it)
            _saved.value = true
            refreshRecords()
            syncManager?.updatePendingCount()
        }
    }

    fun resetNewTest() {
        _image.value = null
        _classification.value = null
        _reason.value = null
        _saved.value = false
        _currentRecord.value = null
        _verification.value = null
        _verificationDetail.value = null
    }

    fun refreshRecords() {
        _records.value = repository.getRecords()
    }

    fun record(testId: String): DigitalTestRecord? =
        _records.value.firstOrNull { it.testId == testId } ?: _currentRecord.value?.takeIf { it.testId == testId }

    fun verify(record: DigitalTestRecord) {
        val detail = repository.verifyDetailed(record)
        _verificationDetail.value = detail
        _verification.value = detail.allPassed
    }

    fun clearVerification() {
        _verification.value = null
        _verificationDetail.value = null
    }
}
