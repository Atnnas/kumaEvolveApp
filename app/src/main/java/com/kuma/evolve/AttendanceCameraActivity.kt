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
                    this, cameraSelector, preview, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e("AttendanceCam", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private var lastPoseCaptureTime = 0L
    private val POSE_CAPTURE_DELAY = 800L // Delay to avoid multiple captures in same angle

    fun onFacePoseDetected(pitch: Float, yaw: Float, frame: Bitmap? = null) {
        if (!isEnrollmentMode || currentStage == EnrollmentStage.DONE || isScanPaused) return

        val now = System.currentTimeMillis()
        if (now - lastPoseCaptureTime < POSE_CAPTURE_DELAY) return

        val isCorrectPosition = when (currentStage) {
            EnrollmentStage.CENTER -> Math.abs(pitch) < 14 && Math.abs(yaw) < 14 // Rangos más amplios
            EnrollmentStage.LEFT -> yaw > 12
            EnrollmentStage.RIGHT -> yaw < -12
            EnrollmentStage.UP -> pitch > 8
            EnrollmentStage.DOWN -> pitch < -8
            else -> false
        }

        if (isCorrectPosition && frame != null) {
            lastPoseCaptureTime = now
            runOnUiThread {
                tvStatusHint.text = "🎯 CAPTURANDO..."
                tvStatusHint.setTextColor(getColor(R.color.kuma_gold))
            }
            
            // Envío directo del frame capturado por el analizador
            sendToRecognition(frame)
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

        // Usamos scanFace para modo atómico (Reconoce + Registra en un solo paso)
        com.kuma.evolve.network.RetrofitClient.instance.scanFace(body).enqueue(object : retrofit2.Callback<com.kuma.evolve.network.RecognitionResponse> {
            override fun onResponse(call: retrofit2.Call<com.kuma.evolve.network.RecognitionResponse>, response: retrofit2.Response<com.kuma.evolve.network.RecognitionResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val res = response.body()!!
                    if (isEnrollmentMode) {
                        // En enrolamiento solo nos importa el descriptor si lo devolviera, 
                        // pero aquí usamos la respuesta de éxito de reconocimiento
                        if (res.recognized && res.descriptor != null) {
                            handleEnrollmentDescriptor(res.descriptor)
                        } else if (res.recognized) {
                           // Fallback: si no viene el descriptor en scanFace, lo pedimos o lo simulamos
                           // (En el server actual scanFace no devuelve descriptor para ahorrar ancho de banda, 
                           // pero para enrolamiento usaremos el endpoint de recognize si es necesario)
                        }
                    } else {
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
        if (isScanPaused || (lastRecognizedId == result.athleteId && result.message != "YA_REGISTRADO")) return

        if (result.recognized) {
            vibrateEffect()
            isScanPaused = true // Detener escaneo tras resultado (asistencia automática en backend)
            lastRecognizedId = result.athleteId

            if (result.success && result.attendance != null) {
                // REGISTRO EXITOSO (NUEVO)
                tvStatusHint.text = "✅ REGISTRADO: ${result.name}"
                tvStatusHint.setTextColor(getColor(R.color.kuma_gold))
                Toast.makeText(this, "Asistencia #${result.attendance.dailySequence}: ${result.name}", Toast.LENGTH_SHORT).show()
            } else if (result.message == "YA_REGISTRADO") {
                // YA ESTABA REGISTRADO HOY
                tvStatusHint.text = "⚠️ YA REGISTRADO: ${result.name}"
                tvStatusHint.setTextColor(getColor(R.color.kuma_red))
            }
        } else {
            vibrateEffect()
            isScanPaused = true 
            tvStatusHint.text = "👤 ROSTRO NO RECONOCIDO"
            tvStatusHint.setTextColor(getColor(R.color.kuma_red))
        }
    }

    // submitAttendance is now deprecated in favor of atomic scanFace
    private fun submitAttendance(athleteId: String?, name: String?, mode: String) {
        // Obsolete
    }

    private fun openManualRegistration() {
        // Mode Strict: Manual registration disabled
        Toast.makeText(this, "Modo Estricto: Solo reconocimiento facial permitido", Toast.LENGTH_LONG).show()
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        val nv21 = yuv420888ToNv21(image)
        val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, image.width, image.height, null)
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        
        // Rotate bitmap if necessary
        val matrix = android.graphics.Matrix()
        matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        return nv21
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
                            
                            // Extraer bitmap del frame para enrolamiento o asistencia instantánea
                            val frameBitmap = activity?.imageProxyToBitmap(imageProxy)

                            // Report head pose for premium enrollment
                            if (activity?.isEnrollmentMode == true) {
                                activity.onFacePoseDetected(face.headEulerAngleX, face.headEulerAngleY, frameBitmap)
                            } else {
                                // For regular attendance, use the frame directly
                                if (frameBitmap != null && !activity!!.isScanPaused) {
                                    activity.sendToRecognition(frameBitmap)
                                }
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
