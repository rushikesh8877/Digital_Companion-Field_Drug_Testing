package app.ui.screens

import android.net.Uri
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import app.ui.data.DemoTestViewModel
import com.sih.drugtestclassifier.camera.CameraCaptureFragment
import com.sih.drugtestclassifier.location.LocationHelper
import com.sih.drugtestclassifier.models.TestImage
import kotlinx.coroutines.launch

/**
 * Capture screen: supports both live CameraX capture via [CameraCaptureFragment]
 * and uploading an existing test strip photo from the device gallery.
 *
 * Location Integrity:
 * - In-app camera captures live GPS & reverse-geocoded address at shutter time.
 * - Uploaded photos inspect EXIF metadata to preserve the original location & timestamp
 *   where the photo was actually snapped, preventing location drift.
 */
@Composable
fun CaptureScreen(viewModel: DemoTestViewModel, onCaptured: () -> Unit, onBack: () -> Unit) {
    val activity = LocalContext.current as? FragmentActivity
    val context = LocalContext.current
    val containerId = remember { View.generateViewId() }
    val attached = remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var isProcessingUpload by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessingUpload = true
            coroutineScope.launch {
                // Inspect EXIF for original GPS and capture timestamp
                val (exifLoc, exifTime) = LocationHelper.extractExif(context, uri)
                val captureTime = exifTime ?: System.currentTimeMillis()
                val finalLoc = exifLoc ?: LocationHelper.getFreshLocation(context)
                val testImage = TestImage(uri.toString(), captureTime)
                viewModel.onImageCaptured(testImage, finalLoc)
                isProcessingUpload = false
                onCaptured()
            }
        }
    }

    ScreenColumn("Capture Test Strip") {
        if (activity == null) {
            Column(Modifier.fillMaxWidth().weight(1f)) {
                Text("Camera preview requires a FragmentActivity host.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { ctx -> FragmentContainerView(ctx).apply { id = containerId } },
                update = { container ->
                    if (!attached.value) {
                        attached.value = true
                        val fragment = CameraCaptureFragment().apply {
                            onImageCaptured = { image: TestImage ->
                                coroutineScope.launch {
                                    val loc = LocationHelper.getFreshLocation(context)
                                    viewModel.onImageCaptured(image, loc)
                                    onCaptured()
                                }
                            }
                        }
                        activity.supportFragmentManager
                            .beginTransaction()
                            .replace(container.id, fragment)
                            .commitNowAllowingStateLoss()
                    }
                },
            )
        }

        if (isProcessingUpload) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // Action options: Upload photo or cancel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = { galleryLauncher.launch("image/*") },
                modifier = Modifier.weight(1f).height(50.dp),
            ) {
                Text("📁 Upload Photo")
            }
            TextButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(50.dp),
            ) {
                Text("Cancel")
            }
        }
    }
}
