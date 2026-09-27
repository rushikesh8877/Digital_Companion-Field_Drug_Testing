package app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.ui.data.DemoTestViewModel
import app.ui.screens.*

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val CAPTURE = "capture"
    const val PROCESSING = "processing"
    const val RESULT = "result"
    const val SAVE = "save"
    const val HISTORY = "history"
    const val DETAIL = "detail/{testId}"
    const val CALIBRATION = "calibration"
    fun detail(testId: String) = "detail/$testId"
}

@Composable
fun AppNavGraph(navController: NavHostController, viewModel: DemoTestViewModel) {
    NavHost(navController = navController, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) {
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onStart = { navController.navigate(Routes.CAPTURE) },
                onHistory = {
                    viewModel.refreshRecords()
                    navController.navigate(Routes.HISTORY)
                },
                onCalibrate = { navController.navigate(Routes.CALIBRATION) },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.CAPTURE) {
            CaptureScreen(
                viewModel = viewModel,
                onCaptured = { navController.navigate(Routes.PROCESSING) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.PROCESSING) {
            ProcessingScreen(
                viewModel = viewModel,
                onComplete = {
                    navController.navigate(Routes.RESULT) {
                        popUpTo(Routes.CAPTURE) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.RESULT) {
            ResultScreen(
                viewModel = viewModel,
                onContinue = { navController.navigate(Routes.SAVE) },
                onRetake = {
                    navController.navigate(Routes.CAPTURE) {
                        popUpTo(Routes.RESULT) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.SAVE) {
            SaveConfirmationScreen(
                viewModel = viewModel,
                onHome = { navController.popBackStack(Routes.HOME, false) },
                onViewHistory = {
                    viewModel.refreshRecords()
                    navController.navigate(Routes.HISTORY) {
                        popUpTo(Routes.HOME)
                    }
                },
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onRecord = { navController.navigate(Routes.detail(it)) },
            )
        }
        composable(Routes.DETAIL, arguments = listOf(navArgument("testId") { type = NavType.StringType })) { entry ->
            val testId = entry.arguments?.getString("testId").orEmpty()
            VerificationDetailScreen(viewModel = viewModel, testId = testId, onBack = { navController.popBackStack() })
        }
        composable(Routes.CALIBRATION) {
            CalibrationScreen(onBack = { navController.popBackStack() })
        }
    }
}

