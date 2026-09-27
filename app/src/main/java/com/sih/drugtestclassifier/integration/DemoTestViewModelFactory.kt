package com.sih.drugtestclassifier.integration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.ui.data.DemoTestViewModel
import app.ui.data.TestRecordRepository

import com.sih.drugtestclassifier.auth.FirebaseAuthManager
import com.sih.drugtestclassifier.sync.FirebaseSyncManager

/** Supplies the real repository, Firebase auth, and cloud sync manager to [DemoTestViewModel]. */
class DemoTestViewModelFactory(
    private val repository: TestRecordRepository,
    private val authManager: FirebaseAuthManager? = null,
    private val syncManager: FirebaseSyncManager? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(DemoTestViewModel::class.java)) {
            "Unknown ViewModel class: $modelClass"
        }
        val vm = DemoTestViewModel(repository)
        authManager?.let { vm.attachAuthManager(it) }
        syncManager?.let { vm.attachSyncManager(it) }
        return vm as T
    }
}
