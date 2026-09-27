package app.ui.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sih.drugtestclassifier.location.LocationHelper
import com.sih.drugtestclassifier.models.ClassificationResult
import com.sih.drugtestclassifier.models.DigitalTestRecord
import com.sih.drugtestclassifier.models.TestImage
import com.sih.drugtestclassifier.security.VerificationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DemoTestViewModel(
    private val repository: TestRecordRepository = FakeTestRecordRepository()
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

    // --- Authentication Actions ---
    fun login(id: String, name: String, pin: String): Boolean {
        if (id.isBlank() || pin.length < 4) return false
        _officerId.value = id.trim()
        _officerName.value = name.trim().ifBlank { "Officer ${id.trim()}" }
        _isLoggedIn.value = true
        return true
    }

    fun logout() {
        _isLoggedIn.value = false
        _currentRecord.value = null
        _image.value = null
        _saved.value = false
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
