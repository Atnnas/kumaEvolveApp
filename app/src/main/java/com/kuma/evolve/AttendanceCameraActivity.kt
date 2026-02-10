package com.kuma.evolve

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.Image
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class AttendanceCameraActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var graphicOverlay: GraphicOverlay
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var tvStatusHint: TextView
    
    private var imageCapture: ImageCapture? = null

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance_camera)

        previewView = findViewById(R.id.preview_view)
        graphicOverlay = findViewById(R.id.graphic_overlay)
        tvStatusHint = findViewById(R.id.tv_status_hint)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        findViewById<MaterialButton>(R.id.btn_manual_mode).visibility = android.view.View.GONE
        findViewById<FloatingActionButton>(R.id.fab_capture).visibility = android.view.View.GONE

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            // Face Detection Analysis
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, FaceAnalyzer(graphicOverlay))
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e("AttendanceCam", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        tvStatusHint.text = "Analizando rostro..."
        
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    processCapturedImage(image)
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e("AttendanceCam", "Photo capture failed: ${exc.message}", exc)
                    tvStatusHint.text = "Error al capturar"
                }
            }
        )
    }

    private fun processCapturedImage(image: ImageProxy) {
        // Convert ImageProxy to Bitmap
        val bitmap = imageProxyToBitmap(image)
        image.close()

        if (bitmap == null) {
            tvStatusHint.text = "Error procesando imagen"
            return
        }

        sendToRecognition(bitmap)
    }

    private fun sendToRecognition(bitmap: Bitmap) {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        val byteArray = stream.toByteArray()

        val requestFile = byteArray.toRequestBody("image/jpeg".toMediaTypeOrNull())
        val body = okhttp3.MultipartBody.Part.createFormData("image", "capture.jpg", requestFile)

        com.kuma.evolve.network.RetrofitClient.instance.recognizeFace(body).enqueue(object : retrofit2.Callback<com.kuma.evolve.network.RecognitionResponse> {
            override fun onResponse(call: retrofit2.Call<com.kuma.evolve.network.RecognitionResponse>, response: retrofit2.Response<com.kuma.evolve.network.RecognitionResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    showRecognitionResult(response.body()!!)
                } else {
                    tvStatusHint.text = "Error en el servidor"
                }
            }

            override fun onFailure(call: retrofit2.Call<com.kuma.evolve.network.RecognitionResponse>, t: Throwable) {
                tvStatusHint.text = "Error de conexión"
            }
        })
    }

    private var isScanPaused = false

    private fun showRecognitionResult(result: com.kuma.evolve.network.RecognitionResponse) {
        if (isScanPaused) return

        if (result.recognized) {
            tvStatusHint.text = "✅ RECONOCIDO: ${result.name}"
            tvStatusHint.setTextColor(getColor(R.color.kuma_gold))
            
            // Auto-submit attendance immediately
            submitAttendance(result.athleteId, result.name, "facial")
        } else {
            tvStatusHint.text = "👤 ROSTRO NO RECONOCIDO"
            tvStatusHint.setTextColor(getColor(R.color.kuma_red))
            // Continuous scanning will naturally resume as we don't pause here for errors
        }
    }

    private fun submitAttendance(athleteId: String?, name: String?, mode: String) {
        if (athleteId == null) return
        
        isScanPaused = true // Pause to avoid double registrations
        
        val athleteIdBody = athleteId.toRequestBody("text/plain".toMediaTypeOrNull())
        val nameBody = name?.toRequestBody("text/plain".toMediaTypeOrNull()) ?: "Desconocido".toRequestBody("text/plain".toMediaTypeOrNull())
        val modeBody = mode.toRequestBody("text/plain".toMediaTypeOrNull())
        
        // We'll use a dummy multi-part for the image as the backend expects it, 
        // but for high speed we could optimize it later.
        val emptyPart = okhttp3.MultipartBody.Part.createFormData("image", "none.jpg", ByteArray(0).toRequestBody("image/jpeg".toMediaTypeOrNull()))

        com.kuma.evolve.network.RetrofitClient.instance.registerAttendance(
            athleteIdBody, nameBody, modeBody, null, emptyPart
        ).enqueue(object : retrofit2.Callback<com.kuma.evolve.network.AttendanceResponse> {
            override fun onResponse(call: retrofit2.Call<com.kuma.evolve.network.AttendanceResponse>, response: retrofit2.Response<com.kuma.evolve.network.AttendanceResponse>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@AttendanceCameraActivity, "Asistencia: $name", Toast.LENGTH_SHORT).show()
                }
                resetScannerAfterDelay()
            }

            override fun onFailure(call: retrofit2.Call<com.kuma.evolve.network.AttendanceResponse>, t: Throwable) {
                tvStatusHint.text = "Error de conexión"
                resetScannerAfterDelay()
            }
        })
    }

    private fun resetScannerAfterDelay() {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            isScanPaused = false
            tvStatusHint.text = "Esperando atleta..."
            tvStatusHint.setTextColor(getColor(R.color.kuma_white))
        }, 3000) // 3 seconds cooldown
    }

    private fun openManualRegistration() {
        // Mode Strict: Manual registration disabled
        Toast.makeText(this, "Modo Estricto: Solo reconocimiento facial permitido", Toast.LENGTH_LONG).show()
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private class FaceAnalyzer(private val overlay: GraphicOverlay) : ImageAnalysis.Analyzer {
        private val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .build()

        private val detector = FaceDetection.getClient(options)

        @ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                
                // If rotation is 90 or 270, swap width and height for coordinate mapping
                if (rotationDegrees == 90 || rotationDegrees == 270) {
                    overlay.setCameraInfo(imageProxy.height, imageProxy.width, true)
                } else {
                    overlay.setCameraInfo(imageProxy.width, imageProxy.height, true)
                }

                val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)
                detector.process(image)
                    .addOnSuccessListener { faces ->
                        overlay.clear()
                        for (face in faces) {
                            overlay.add(FaceGraphic(overlay, face))
                            
                            // Trigger recognition if not paused
                            if (faces.isNotEmpty()) {
                                // For speed, we use the analyzer directly to send to recognition
                                (overlay.context as? AttendanceCameraActivity)?.takePhoto()
                            }
                        }
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }
}
