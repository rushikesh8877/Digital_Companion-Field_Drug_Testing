package com.sih.drugtestclassifier

import android.Manifest
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ui.DemoApp
import app.ui.data.DemoTestViewModel
import com.sih.drugtestclassifier.integration.DemoTestViewModelFactory
import com.sih.drugtestclassifier.integration.RealTestRecordRepository

/**
 * Single-activity host for the whole app. Requests the camera + location
 * permissions the capture/record flow needs, wires the real
 * [RealTestRecordRepository] (camera → classification → security →
 * storage) into Person 5's Compose navigation graph, and hands off to
 * [DemoApp].
 */
class MainActivity : AppCompatActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* Individual screens re-check and explain if something was denied. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )

        val repository = RealTestRecordRepository(applicationContext)
        val authManager = com.sih.drugtestclassifier.auth.FirebaseAuthManager.getInstance(applicationContext)

        setContent {
            val viewModel: DemoTestViewModel = viewModel(
                factory = DemoTestViewModelFactory(repository, authManager),
            )
            DemoApp(testViewModel = viewModel)
        }
    }
}
