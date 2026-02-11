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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kuma.evolve.network.EnrollmentRequest

class AttendanceCameraActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var graphicOverlay: GraphicOverlay
    private lateinit var cameraExecutor: ExecutorService
    private var isEnrollmentMode = false
    private var enrollmentAthleteId: String? = null
    private var enrollmentAthleteName: String? = null
    private val enrollmentDescriptors = mutableListOf<List<Double>>()
    private val REQUIRED_ENROLLMENT_COUNT = 5
    private lateinit var tvStatusHint: TextView
    private lateinit var enrollmentOverlay: EnrollmentOverlayView
    
    // Premium Enrollment Stages
    private enum class EnrollmentStage { CENTER, LEFT, RIGHT, UP, DOWN, DONE }
    private var currentStage = EnrollmentStage.CENTER
    private val completedStages = mutableSetOf<EnrollmentStage>()
    
    private var imageCapture: ImageCapture? = null

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance_camera)

        isEnrollmentMode = intent.getStringExtra("MODE") == "ENROLLMENT"
        enrollmentAthleteId = intent.getStringExtra("ATHLETE_ID")
        enrollmentAthleteName = intent.getStringExtra("ATHLETE_NAME")

        if (isEnrollmentMode) {
            findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_manual_mode).visibility = android.view.View.VISIBLE
            findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_manual_mode).text = "CANCELAR ENROLAMIENTO"
        }

        previewView = findViewById(R.id.preview_view)
        graphicOverlay = findViewById(R.id.graphic_overlay)
        enrollmentOverlay = findViewById(R.id.enrollment_overlay)
        tvStatusHint = findViewById(R.id.tv_status_hint)
        
        if (isEnrollmentMode) {
            enrollmentOverlay.visibility = android.view.View.VISIBLE
            updateEnrollmentGuide()
            findViewById<MaterialButton>(R.id.btn_manual_mode).visibility = android.view.View.VISIBLE
            findViewById<MaterialButton>(R.id.btn_manual_mode).text = "CANCELAR ENROLAMIENTO"
        } else {
            findViewById<MaterialButton>(R.id.btn_manual_mode).visibility = android.view.View.GONE
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        findViewById<FloatingActionButton>(R.id.fab_capture).visibility = android.view.View.GONE

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permisos de cámara no otorgados por el usuario.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
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

    private var lastPoseCaptureTime = 0L
    private val POSE_CAPTURE_DELAY = 800L // Delay to avoid multiple captures in same angle

    fun onFacePoseDetected(pitch: Float, yaw: Float) {
        if (!isEnrollmentMode || currentStage == EnrollmentStage.DONE || isScanPaused) return

        val now = System.currentTimeMillis()
        if (now - lastPoseCaptureTime < POSE_CAPTURE_DELAY) return

        val isCorrectPosition = when (currentStage) {
            EnrollmentStage.CENTER -> Math.abs(pitch) < 12 && Math.abs(yaw) < 12
            EnrollmentStage.LEFT -> yaw > 15
            EnrollmentStage.RIGHT -> yaw < -15
            EnrollmentStage.UP -> pitch > 10
            EnrollmentStage.DOWN -> pitch < -10
            else -> false
        }

        if (isCorrectPosition) {
            lastPoseCaptureTime = now
            runOnUiThread {
                tvStatusHint.text = "🎯 CAPTURANDO..."
                tvStatusHint.setTextColor(getColor(R.color.kuma_gold))
            }
            takePhoto()
            vibrateEffect()
        }
    }

    fun onFaceDetected(face: com.google.mlkit.vision.face.Face) {
        // Simple presence check for future logic if needed, but no proximity alerts
    }

    private fun vibrateEffect() {
        try {
            val vibrator = getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        } catch (e: Exception) {
            Log.e("AttendanceCam", "Error vibrando: ${e.message}")
        }
    }

    private fun updateEnrollmentGuide() {
        runOnUiThread {
            val text = when (currentStage) {
                EnrollmentStage.CENTER -> "MIRA FIJAMENTE AL CENTRO"
                EnrollmentStage.LEFT -> "GIRA LENTAMENTE A LA IZQUIERDA"
                EnrollmentStage.RIGHT -> "GIRA LENTAMENTE A LA DERECHA"
                EnrollmentStage.UP -> "INCLINA LA CABEZA HACIA ARRIBA"
                EnrollmentStage.DOWN -> "INCLINA LA CABEZA HACIA ABAJO"
                EnrollmentStage.DONE -> "¡ANÁLISIS COMPLETADO!"
            }
            
            if (tvStatusHint.text != "🎯 CAPTURANDO...") {
                tvStatusHint.text = text
                tvStatusHint.setTextColor(getColor(R.color.kuma_white))
            }
            enrollmentOverlay.setCurrentStage(currentStage.ordinal)
            
            val progress = (enrollmentDescriptors.size.toFloat() / REQUIRED_ENROLLMENT_COUNT) * 100f
            enrollmentOverlay.setProgressPercentage(progress)
        }
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
                    val res = response.body()!!
                    if (isEnrollmentMode && res.descriptor != null) {
                        handleEnrollmentDescriptor(res.descriptor)
                    } else if (!isEnrollmentMode) {
                        showRecognitionResult(res)
                    }
                } else {
                    if (!isEnrollmentMode) tvStatusHint.text = "Error en el servidor"
                }
            }

            override fun onFailure(call: retrofit2.Call<com.kuma.evolve.network.RecognitionResponse>, t: Throwable) {
                if (!isEnrollmentMode) tvStatusHint.text = "Error de conexión"
            }
        })
    }

    private var isScanPaused = false
    private var lastRecognizedId: String? = null
    private var faceWasPresent = false
    private var enrollmentCount = 0

    private fun handleEnrollmentDescriptor(descriptor: List<Double>) {
        if (currentStage == EnrollmentStage.DONE) return
        
        enrollmentDescriptors.add(descriptor)
        
        completedStages.add(currentStage)
        val stages = EnrollmentStage.values()
        val nextIndex = currentStage.ordinal + 1
        
        if (nextIndex < stages.size) {
            currentStage = stages[nextIndex]
        }
        
        updateEnrollmentGuide()

        if (enrollmentDescriptors.size >= REQUIRED_ENROLLMENT_COUNT) {
            currentStage = EnrollmentStage.DONE
            updateEnrollmentGuide()
            finalizeEnrollment()
        }
    }

    private fun finalizeEnrollment() {
        isScanPaused = true
        tvStatusHint.text = "CONSOLIDANDO ADN FACIAL..."
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val enrollmentReq = EnrollmentRequest(enrollmentDescriptors)
                val response = com.kuma.evolve.network.RetrofitClient.instance.enrollAthlete(
                    enrollmentAthleteId!!, enrollmentReq
                ).execute()
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@AttendanceCameraActivity, "¡ENTRENAMIENTO COMPLETADO!", Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        Toast.makeText(this@AttendanceCameraActivity, "Error en enrolamiento", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AttendanceCameraActivity, "Error de red", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    // Called from FaceAnalyzer when presence changes
    fun onFacePresenceChanged(present: Boolean) {
        if (!present && faceWasPresent) {
            // Face left the camera
            Log.d("AttendanceCam", "👤 El rostro se retiró.")
            if (!isEnrollmentMode) {
                lastRecognizedId = null
                isScanPaused = false
                tvStatusHint.text = "ESPERANDO ATLETA..."
                tvStatusHint.setTextColor(getColor(R.color.kuma_white))
            }
        } else if (present && !faceWasPresent) {
            // Face entered the camera
            if (isEnrollmentMode) {
                updateEnrollmentGuide() // Restore instructions immediately
            }
        }
        faceWasPresent = present
    }

    private fun showRecognitionResult(result: com.kuma.evolve.network.RecognitionResponse) {
        if (isScanPaused || lastRecognizedId == result.athleteId) return

        if (result.recognized) {
            vibrateEffect() // 📳 Feedback de éxito
            tvStatusHint.text = "✅ RECONOCIDO: ${result.name}"
            tvStatusHint.setTextColor(getColor(R.color.kuma_gold))
            
            // Auto-submit attendance immediately
            submitAttendance(result.athleteId, result.name, "facial")
        } else {
            vibrateEffect() // 📳 Feedback de no-reconocido
            isScanPaused = true // 🥋 Detener análisis hasta que el rostro se retire
            tvStatusHint.text = "👤 ROSTRO NO RECONOCIDO"
            tvStatusHint.setTextColor(getColor(R.color.kuma_red))
        }
    }

    private fun submitAttendance(athleteId: String?, name: String?, mode: String) {
        if (athleteId == null || isScanPaused) return
        
        isScanPaused = true 
        lastRecognizedId = athleteId // Memory to avoid double scans of same person
        
        val athleteIdBody = athleteId.toRequestBody("text/plain".toMediaTypeOrNull())
        val nameBody = name?.toRequestBody("text/plain".toMediaTypeOrNull()) ?: "Desconocido".toRequestBody("text/plain".toMediaTypeOrNull())
        val modeBody = mode.toRequestBody("text/plain".toMediaTypeOrNull())
        
        val emptyPart = okhttp3.MultipartBody.Part.createFormData("image", "none.jpg", ByteArray(0).toRequestBody("image/jpeg".toMediaTypeOrNull()))

        com.kuma.evolve.network.RetrofitClient.instance.registerAttendance(
            athleteIdBody, nameBody, modeBody, null, emptyPart
        ).enqueue(object : retrofit2.Callback<com.kuma.evolve.network.AttendanceResponse> {
            override fun onResponse(call: retrofit2.Call<com.kuma.evolve.network.AttendanceResponse>, response: retrofit2.Response<com.kuma.evolve.network.AttendanceResponse>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@AttendanceCameraActivity, "Asistencia: $name", Toast.LENGTH_SHORT).show()
                    tvStatusHint.text = "✅ REGISTRADO: $name"
                } else {
                    // Check for duplicate message from server
                    try {
                        val errorBody = response.errorBody()?.string()
                        if (errorBody?.contains("YA_REGISTRADO") == true) {
                            tvStatusHint.text = "⚠️ YA REGISTRADO HOY: $name"
                            tvStatusHint.setTextColor(getColor(R.color.kuma_red))
                        } else {
                            tvStatusHint.text = "Error al registrar"
                        }
                    } catch (e: Exception) {
                        tvStatusHint.text = "Error en el servidor"
                    }
                }
                // We keep isScanPaused = true until they leave the camera
                Log.d("AttendanceCam", "Registro completado. Esperando a que el rostro se retire.")
            }

            override fun onFailure(call: retrofit2.Call<com.kuma.evolve.network.AttendanceResponse>, t: Throwable) {
                tvStatusHint.text = "Error de conexión"
                isScanPaused = false // Retry on connection failure
            }
        })
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

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private class FaceAnalyzer(private val overlay: GraphicOverlay) : ImageAnalysis.Analyzer {
        private val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // Added for Euler angles
            .build()

        private val detector = FaceDetection.getClient(options)

        @ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                
                if (rotationDegrees == 90 || rotationDegrees == 270) {
                    overlay.setCameraInfo(imageProxy.height, imageProxy.width, true)
                } else {
                    overlay.setCameraInfo(imageProxy.width, imageProxy.height, true)
                }

                val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)
                detector.process(image)
                    .addOnSuccessListener { faces ->
                        overlay.clear()
                        
                        val activity = overlay.context as? AttendanceCameraActivity
                        activity?.onFacePresenceChanged(faces.isNotEmpty())

                        for (face in faces) {
                            overlay.add(FaceGraphic(overlay, face))
                            
                            // Proximity check
                            activity?.onFaceDetected(face)
                            
                            // Report head pose for premium enrollment
                            activity?.onFacePoseDetected(face.headEulerAngleX, face.headEulerAngleY)

                            // Trigger recognition if faces are present and we are not recently matched (Regular attendance)
                            if (faces.isNotEmpty() && !activity!!.isEnrollmentMode) {
                                activity.takePhoto()
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
