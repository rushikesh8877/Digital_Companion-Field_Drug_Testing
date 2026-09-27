package com.sih.drugtestclassifier.camera

/*
 * Camera & Image Capture module (Person 1's workstream).
 * Ported unchanged from the original CameraCaptureModule.kt — only the
 * package declaration and the TestImage import were adjusted so it builds
 * against the project's single shared contract
 * (com.sih.drugtestclassifier.models.TestImage) instead of declaring its
 * own local copy of that data class.
 *
 * Manifest requirement: <uses-permission android:name="android.permission.CAMERA" />
 */

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.sih.drugtestclassifier.models.TestImage
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.pow

/** Manifest: <uses-permission android:name="android.permission.CAMERA" /> */
class CameraCaptureFragment : Fragment() {
    var onImageCaptured: ((TestImage) -> Unit)? = null
    var hideDefaultControls: Boolean = true
    private var preview: PreviewView? = null
    private var message: TextView? = null
    private var shutter: Button? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var provider: ProcessCameraProvider? = null
    private var busy = false
    private val worker = Executors.newSingleThreadExecutor()

    fun triggerCapture() {
        lockThenCapture()
    }

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) startCamera() else show("Camera permission is required.")
    }

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        val root = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        val stage = FrameLayout(requireContext())
        root.addView(stage, LinearLayout.LayoutParams(-1, 0, 1f))
        preview = PreviewView(requireContext()).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        stage.addView(preview, FrameLayout.LayoutParams(-1, -1))
        
        // If Compose handles the guide and buttons, don't show legacy Android views
        if (!hideDefaultControls) {
            stage.addView(Guide(requireContext()), FrameLayout.LayoutParams(-1, -1))
            message = TextView(requireContext()).apply {
                text = "Fit both the test strip and reference color card inside the guide."
                setTextColor(Color.WHITE); textSize = 16f; gravity = Gravity.CENTER
                setPadding(16, 14, 16, 14); setBackgroundColor(0x99000000.toInt())
            }
            root.addView(message, LinearLayout.LayoutParams(-1, -2))
            val row = LinearLayout(requireContext()).apply { gravity = Gravity.CENTER; setPadding(8, 8, 8, 16) }
            row.addView(Button(requireContext()).apply {
                text = "Unlock / retry"; setOnClickListener { unlockAndRetry() }
            }, LinearLayout.LayoutParams(0, -2, 1f))
            shutter = Button(requireContext()).apply {
                text = "Capture"; isEnabled = false; setOnClickListener { lockThenCapture() }
            }
            row.addView(shutter, LinearLayout.LayoutParams(0, -2, 1f)); root.addView(row)
        }
        return root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED) startCamera() else permission.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val ctx = context ?: return
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            show("Camera permission is required to start preview."); return
        }
        show("Starting camera preview…")
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            if (!isAdded) return@addListener
            try {
                val p = future.get(); provider = p
                val pv = Preview.Builder().build().also { it.setSurfaceProvider(preview?.surfaceProvider) }
                val ic = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
                p.unbindAll()
                camera = p.bindToLifecycle(viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, pv, ic)
                imageCapture = ic; shutter?.isEnabled = true
                show("Preview ready. Frame both items, then capture.")
            } catch (e: Exception) { show("Could not start preview: ${e.message ?: "camera unavailable"}") }
        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun lockThenCapture() {
        val active = camera ?: run { show("Camera is not ready."); return }
        if (imageCapture == null || busy) return
        setBusy(true); show("Locking exposure and white balance…")
        try {
            val options = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true).build()
            Camera2CameraControl.from(active.cameraControl).setCaptureRequestOptions(options).addListener({
                if (isAdded) { show("Exposure / white balance lock requested. Capturing…"); saveCapture() }
            }, ContextCompat.getMainExecutor(requireContext()))
        } catch (e: Exception) {
            setBusy(false); show("Could not request camera lock: ${e.message ?: "unsupported camera control"}")
        }
    }

    private fun saveCapture() {
        val ic = imageCapture ?: run { setBusy(false); show("Camera capture is unavailable."); return }
        val ctx = context ?: return
        try {
            val dir = File(ctx.filesDir, "captures")
            if (!dir.exists() && !dir.mkdirs()) throw IOException("cannot create captures directory")
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            ic.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(ctx),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                        val resultImage = TestImage(Uri.fromFile(file).toString(), System.currentTimeMillis())
                        setBusy(false); show("Photo saved. Checking quality…")
                        checkQuality(file) { warning ->
                            show(if (warning == null) "Photo saved. Quality check passed."
                                 else "Quality warning: $warning. Photo was saved.")
                            onImageCaptured?.invoke(resultImage)
                        }
                    }
                    override fun onError(error: ImageCaptureException) {
                        setBusy(false); show("Could not save photo: ${error.message ?: "capture failed"}")
                    }
                })
        } catch (e: Exception) { setBusy(false); show("Could not save photo: ${e.message ?: "storage error"}") }
    }

    private fun checkQuality(file: File, done: (String?) -> Unit) {
        worker.execute {
            val warning = try {
                val bitmap = decodeSmall(file)
                try {
                    val q = Quality.measure(bitmap)
                    when {
                        q.laplacianVariance < MIN_LAPLACIAN_VARIANCE -> "image may be blurry"
                        q.clippedWhiteFraction > MAX_CLIPPED_WHITE_FRACTION -> "overexposure or glare detected"
                        else -> null
                    }
                } finally { bitmap.recycle() }
            } catch (_: Exception) { "could not analyze image quality" }
            activity?.runOnUiThread { if (isAdded) done(warning) }
        }
    }

    private fun decodeSmall(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("invalid JPEG")
        var sample = 1
        while (bounds.outWidth / sample > 256 || bounds.outHeight / sample > 256) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
            inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565
        }) ?: throw IOException("could not decode JPEG")
    }

    private fun unlockAndRetry() {
        val active = camera ?: run { show("Camera is not ready."); return }
        if (busy) return
        setBusy(true)
        try {
            val options = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, false)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, false).build()
            Camera2CameraControl.from(active.cameraControl).setCaptureRequestOptions(options).addListener({
                setBusy(false); show("Exposure unlocked. Reposition both items and retry.")
            }, ContextCompat.getMainExecutor(requireContext()))
        } catch (e: Exception) {
            setBusy(false); show("Could not unlock camera: ${e.message ?: "unsupported control"}")
        }
    }

    private fun setBusy(value: Boolean) { busy = value; shutter?.isEnabled = !value && camera != null }
    private fun show(text: String) { message?.text = text }

    override fun onDestroyView() {
        provider?.unbindAll(); camera = null; imageCapture = null
        preview = null; message = null; shutter = null
        super.onDestroyView()
    }
    override fun onDestroy() { worker.shutdown(); super.onDestroy() }

    private companion object {
        const val MIN_LAPLACIAN_VARIANCE = 85.0
        const val MAX_CLIPPED_WHITE_FRACTION = 0.12
    }
}

/** Static framing aid with color dividation and test strip alignment guide. */
private class Guide(context: Context) : View(context) {
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E58325"); style = Paint.Style.STROKE; strokeWidth = 6f
        setShadowLayer(8f, 0f, 0f, Color.BLACK)
    }
    private val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E58325"); style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val colorBandDivider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88FFA726.toInt(); style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 26f; textAlign = Paint.Align.CENTER; isFakeBoldText = true
        setShadowLayer(6f, 0f, 1f, Color.BLACK)
    }
    private val subLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt(); textSize = 18f; textAlign = Paint.Align.LEFT
        setShadowLayer(4f, 0f, 1f, Color.BLACK)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val r = RectF(width * .08f, height * .22f, width * .92f, height * .68f)
        canvas.drawRoundRect(r, 22f, 22f, outline)
        val x = r.left + r.width() * .58f
        // Vertical divider separating Color Matrix and Test Strip
        canvas.drawLine(x, r.top, x, r.bottom, divider)
        
        // Color Matrix horizontal divisions
        val bandHeight = r.height() / 4f
        for (i in 1..3) {
            val y = r.top + i * bandHeight
            canvas.drawLine(r.left, y, x, y, colorBandDivider)
        }
        
        // Labels
        canvas.drawText("COLOR MATRIX", r.left + (x - r.left) / 2f, r.top - 14f, label)
        canvas.drawText("TEST STRIP", x + (r.right - x) / 2f, r.top - 14f, label)
        
        // Swatch division guides inside left area
        canvas.drawText("1: Baseline", r.left + 16f, r.top + bandHeight * 0.6f, subLabel)
        canvas.drawText("2: Ref A", r.left + 16f, r.top + bandHeight * 1.6f, subLabel)
        canvas.drawText("3: Ref B", r.left + 16f, r.top + bandHeight * 2.6f, subLabel)
        canvas.drawText("4: Control", r.left + 16f, r.top + bandHeight * 3.6f, subLabel)
    }
}

private object Quality {
    data class Metrics(val laplacianVariance: Double, val clippedWhiteFraction: Double)
    fun measure(bitmap: Bitmap): Metrics {
        val w = bitmap.width; val h = bitmap.height
        if (w < 3 || h < 3) throw IOException("image too small")
        val pixels = IntArray(w * h); bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val gray = IntArray(pixels.size); var clipped = 0
        for (i in pixels.indices) {
            val c = pixels[i]; val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
            gray[i] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            if (r >= 250 && g >= 250 && b >= 250) clipped++
        }
        var sum = 0.0; var squares = 0.0; var count = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val lap = (gray[i-w] + gray[i-1] - 4*gray[i] + gray[i+1] + gray[i+w]).toDouble()
            sum += lap; squares += lap.pow(2); count++
        }
        val mean = sum / count
        return Metrics(squares / count - mean.pow(2), clipped.toDouble() / pixels.size)
    }
}
