package com.sih.drugtestclassifier.integration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.ui.data.DemoTestViewModel
import app.ui.data.TestRecordRepository

/** Supplies the real (camera+classifier+security+storage-backed) repository to [DemoTestViewModel]. */
class DemoTestViewModelFactory(private val repository: TestRecordRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(DemoTestViewModel::class.java)) {
            "Unknown ViewModel class: $modelClass"
        }
        return DemoTestViewModel(repository) as T
    }
}
