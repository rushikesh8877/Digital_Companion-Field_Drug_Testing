package app.ui.screens

import android.net.Uri
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import app.ui.data.DemoTestViewModel
import com.sih.drugtestclassifier.camera.CameraCaptureFragment
import com.sih.drugtestclassifier.location.LocationHelper
import com.sih.drugtestclassifier.models.TestImage
import kotlinx.coroutines.launch

/**
 * Capture screen matching Page 3 of reference design:
 * - Immersive dark camera screen
 * - Top bar with close [X] and "Align Reference Card"
 * - Prominent Orange guide frame with color calibration dividation (color reference vs test strip)
 * - Translucent floating instruction card
 * - Circular white shutter button
 * - Clean "Upload Photo" pill button
 */
@Composable
fun CaptureScreen(viewModel: DemoTestViewModel, onCaptured: () -> Unit, onBack: () -> Unit) {
    val activity = LocalContext.current as? FragmentActivity
    val context = LocalContext.current
    val containerId = remember { View.generateViewId() }
    val attached = remember { mutableStateOf(false) }
    var activeFragment by remember { mutableStateOf<CameraCaptureFragment?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var isProcessingUpload by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessingUpload = true
            coroutineScope.launch {
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090E16)),
    ) {
        // Camera preview layer
        if (activity != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> FragmentContainerView(ctx).apply { id = containerId } },
                update = { container ->
                    if (!attached.value) {
                        attached.value = true
                        val fragment = CameraCaptureFragment().apply {
                            hideDefaultControls = true
                            onImageCaptured = { image: TestImage ->
                                coroutineScope.launch {
                                    val loc = LocationHelper.getFreshLocation(context)
                                    viewModel.onImageCaptured(image, loc)
                                    isCapturing = false
                                    onCaptured()
                                }
                            }
                        }
                        activeFragment = fragment
                        activity.supportFragmentManager
                            .beginTransaction()
                            .replace(container.id, fragment)
                            .commitNowAllowingStateLoss()
                    }
                },
            )
        }

        // Camera guide with the requested color dividation
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 120.dp, top = 60.dp),
        ) {
            val guideWidth = size.width * 0.88f
            val guideHeight = size.height * 0.48f
            val left = (size.width - guideWidth) / 2f
            val top = size.height * 0.16f
            val right = left + guideWidth
            val bottom = top + guideHeight

            // Main outer Orange rounded rectangle
            drawRoundRect(
                color = Color(0xFFE58325),
                topLeft = Offset(left, top),
                size = Size(guideWidth, guideHeight),
                cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                style = Stroke(width = 3.5f.dp.toPx()),
            )

            // Dividation for color in camera guide
            val dividerX = left + guideWidth * 0.58f

            // Vertical divider dividing Color Reference Card and Test Strip
            drawLine(
                color = Color(0xFFE58325),
                start = Offset(dividerX, top),
                end = Offset(dividerX, bottom),
                strokeWidth = 2.dp.toPx(),
            )

            // Horizontal color calibration band dividers on the left partition
            val bandHeight = guideHeight / 4f
            for (i in 1..3) {
                val y = top + i * bandHeight
                drawLine(
                    color = Color(0x66E58325),
                    start = Offset(left + 8.dp.toPx(), y),
                    end = Offset(dividerX - 8.dp.toPx(), y),
                    strokeWidth = 1.5f.dp.toPx(),
                )
            }
        }

        // Labels over the guide partitions
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .align(Alignment.TopCenter)
                .offset(y = 135.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(0.86f).align(Alignment.Center),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    color = Color(0x99101820),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        "COLOR MATRIX",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                Surface(
                    color = Color(0x99101820),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        "TEST STRIP",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }

        // Top App Bar: Close [X] and "Align Reference Card"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Color(0xFF131D2A), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            Text(
                "Align Reference Card",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(42.dp))
        }

        // Floating Instruction pill below guide
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 170.dp)
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                color = Color(0xEE111923),
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 4.dp,
            ) {
                Text(
                    "Place the reference card and test strip fully inside\nthe frame, in even lighting.",
                    color = Color.White,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
        }

        // Shutter Button & Upload Photo Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (isProcessingUpload || isCapturing) {
                CircularProgressIndicator(color = Color.White)
            }

            // Big White Circle Shutter Button
            Surface(
                modifier = Modifier
                    .size(72.dp)
                    .clickable(enabled = !isCapturing && !isProcessingUpload) {
                        isCapturing = true
                        activeFragment?.triggerCapture()
                    },
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 8.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .border(2.dp, Color(0xFFE2E8F0), CircleShape),
                    )
                }
            }

            // Upload Photo Pill Button matching screenshot
            Surface(
                modifier = Modifier
                    .clickable(enabled = !isProcessingUpload && !isCapturing) {
                        galleryLauncher.launch("image/*")
                    },
                shape = RoundedCornerShape(8.dp),
                color = Color.White,
                shadowElevation = 4.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudUpload,
                        contentDescription = "Upload Photo",
                        tint = Color(0xFF0A2342),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Upload Photo",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0A2342),
                    )
                }
            }
        }
    }
}
