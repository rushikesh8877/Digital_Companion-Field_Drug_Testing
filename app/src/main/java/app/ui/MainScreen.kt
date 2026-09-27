/*
 * Test Case Checklist for Person 6:
 * capture → classify → save → search → verify → tamper-detection
 */
package app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import app.ui.data.DemoTestViewModel
import app.ui.navigation.AppNavGraph

/** Entry point for the single-activity UI flow. Place inside your Material 3 app theme. */
@Composable
fun DemoApp(testViewModel: DemoTestViewModel = viewModel()) {
    val navController = rememberNavController()
    MaterialTheme {
        AppNavGraph(navController = navController, viewModel = testViewModel)
    }
}
