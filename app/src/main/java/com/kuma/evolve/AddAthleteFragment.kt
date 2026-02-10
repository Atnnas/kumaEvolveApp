package com.kuma.evolve

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import com.kuma.evolve.data.Athlete
import com.kuma.evolve.network.RetrofitClient
import coil.load
import coil.transform.CircleCropTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import android.graphics.drawable.BitmapDrawable
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import android.graphics.Matrix
import java.text.SimpleDateFormat
import java.util.*

class AddAthleteFragment : Fragment() {

    private lateinit var ivPhoto: ShapeableImageView
    private lateinit var etIdCard: TextInputEditText
    private lateinit var etName: TextInputEditText
    private lateinit var etBirthDate: TextInputEditText
    private lateinit var etGrade: TextInputEditText
    private lateinit var etWeight: TextInputEditText
    private lateinit var btnSave: Button
    private lateinit var btnEnroll: Button

    private var editingAthlete: Athlete? = null
    private var photoUri: Uri? = null
    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    companion object {
        private const val ARG_ATHLETE = "athlete"
        fun newInstance(athlete: Athlete) = AddAthleteFragment().apply {
            arguments = Bundle().apply {
                putSerializable(ARG_ATHLETE, athlete)
            }
        }
    }

    // Photo selection launcher
    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            // CRITICAL: Use Coil to load preview instead of setImageURI to avoid OOM
            ivPhoto.load(photoUri) {
                transformations(CircleCropTransformation())
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(context, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editingAthlete = arguments?.getSerializable(ARG_ATHLETE) as? Athlete
        // Restore photoUri if activity was recreated (e.g., S25 Ultra camera kill)
        savedInstanceState?.getParcelable<Uri>("photo_uri")?.let {
            photoUri = it
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        photoUri?.let {
            outState.putParcelable("photo_uri", it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_add_athlete, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ivPhoto = view.findViewById(R.id.iv_athlete_photo)
        etIdCard = view.findViewById(R.id.et_id_card)
        etName = view.findViewById(R.id.et_name)
        etBirthDate = view.findViewById(R.id.et_birth_date)
        etGrade = view.findViewById(R.id.et_grade)
        etWeight = view.findViewById(R.id.et_weight)
        btnSave = view.findViewById(R.id.btn_save_athlete)
        btnEnroll = view.findViewById(R.id.btn_enroll_face)

        // Restoration of UI state after recreation
        photoUri?.let {
            ivPhoto.load(it) {
                transformations(CircleCropTransformation())
            }
        }

        editingAthlete?.let { athlete ->
            etIdCard.setText(athlete.idCard)
            etName.setText(athlete.name)
            etBirthDate.setText(dateFormat.format(athlete.birthDate))
            etGrade.setText(athlete.grade)
            etWeight.setText(athlete.weight?.toString())
            
            // Only load server image if we haven't just taken a new photo
            if (photoUri == null) {
                athlete.imageUrl?.let { url ->
                    ivPhoto.load(url) {
                        transformations(CircleCropTransformation())
                    }
                }
            }
            btnEnroll.visibility = View.VISIBLE
            btnSave.text = "Actualizar Atleta"
        }

        btnEnroll.setOnClickListener {
            openEnrollmentCamera()
        }

        view.findViewById<View>(R.id.photo_container).setOnClickListener {
            checkPermissionAndLaunchCamera()
        }

        etBirthDate.setOnClickListener { showDatePicker() }
        btnSave.setOnClickListener { saveAthlete() }

        // --- NEW: Fetch full data if editing ---
        editingAthlete?.let { athlete ->
            loadFullAthleteData(athlete._id!!)
        }
    }

    private fun openEnrollmentCamera() {
        editingAthlete?._id?.let { id ->
            val intent = android.content.Intent(requireContext(), AttendanceCameraActivity::class.java).apply {
                putExtra("MODE", "ENROLLMENT")
                putExtra("ATHLETE_ID", id)
                putExtra("ATHLETE_NAME", etName.text.toString())
            }
            startActivity(intent)
        }
    }

    private fun loadFullAthleteData(athleteId: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.instance.getAthleteById(athleteId).execute()
                if (response.isSuccessful && response.body() != null) {
                    val fullAthlete = response.body()!!
                    withContext(Dispatchers.Main) {
                        // Restore photo visibility with robust Base64 handling
                        if (photoUri == null) {
                            fullAthlete.imageUrl?.let { url ->
                                loadPhotoFromBase64(url)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AddAthlete", "Fallo al cargar imagen original", e)
            }
        }
    }
    
    private fun loadPhotoFromBase64(imageUrl: String) {
        try {
            if (imageUrl.startsWith("data:") && imageUrl.contains("base64,")) {
                val base64Data = imageUrl.substring(imageUrl.indexOf("base64,") + 7)
                val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
                ivPhoto.load(imageBytes) {
                    crossfade(true)
                    transformations(CircleCropTransformation())
                }
                android.util.Log.d("AddAthlete", "✅ Vista previa cargada correctamente")
            }
        } catch (e: Exception) {
            android.util.Log.e("AddAthlete", "Error decodificando vista previa", e)
            ivPhoto.load(android.R.drawable.ic_menu_camera)
        }
    }
    
    // ... existing camera methods (checkPermissionAndLaunchCamera, launchCamera, processImageForUpload) ...
    // Note: I will keep the original file structure but I need to make sure I don't duplicate or lose them.
    // I will replace only the relevant parts in the next tool call if needed, 
    // but here I am modifying the top of the class.

    private fun checkPermissionAndLaunchCamera() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val photoFile = File.createTempFile("athlete_", ".jpg", requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES))
        photoUri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", photoFile)
        takePhotoLauncher.launch(photoUri)
    }


    private fun showDatePicker() {
        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, day ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, day)
            etBirthDate.setText(dateFormat.format(calendar.time))
        }
        DatePickerDialog(requireContext(), dateSetListener, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun saveAthlete() {
        val idCard = etIdCard.text.toString()
        val name = etName.text.toString()
        val birthDateStr = etBirthDate.text.toString()
        val grade = etGrade.text.toString()
        val weight = etWeight.text.toString().toDoubleOrNull()

        if (idCard.isEmpty() || name.isEmpty() || birthDateStr.isEmpty()) {
            Toast.makeText(context, "Complete los campos obligatorios", Toast.LENGTH_SHORT).show()
            return
        }

        // --- NATIVE ARMOR: UI Lockdown ---
        btnSave.isEnabled = false
        btnSave.text = "PROCESANDO FOTO..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("AddAthlete", "Iniciando Blindaje Kuma para: $name")
                
                val idCardPart = RequestBody.create(MultipartBody.FORM, idCard)
                val namePart = RequestBody.create(MultipartBody.FORM, name)
                val birthDatePart = RequestBody.create(MultipartBody.FORM, birthDateStr)
                val gradePart = RequestBody.create(MultipartBody.FORM, grade ?: "")
                val weightPart = RequestBody.create(MultipartBody.FORM, weight?.toString() ?: "0")

                var imagePart: MultipartBody.Part? = null
                
                // --- Safe Photo Extraction ---
                photoUri?.let { uri ->
                    val compressedFile = robustCompress(uri)
                    if (compressedFile != null && compressedFile.exists()) {
                        val requestFile = RequestBody.create("image/jpeg".toMediaTypeOrNull(), compressedFile)
                        imagePart = MultipartBody.Part.createFormData("image", compressedFile.name, requestFile)
                        android.util.Log.d("AddAthlete", "Escudo Activado: Foto lista (${compressedFile.length() / 1024} KB)")
                    }
                }

                val call = if (editingAthlete != null) {
                   RetrofitClient.instance.updateAthlete(
                       editingAthlete!!._id!!,
                       idCardPart, namePart, birthDatePart, gradePart, weightPart, imagePart
                   )
                } else {
                    RetrofitClient.instance.registerAthlete(
                        idCardPart, namePart, birthDatePart, gradePart, weightPart, null, imagePart
                    )
                }
                
                val response = try {
                    call.execute()
                } catch (e: Exception) {
                    throw Exception("Error de conexión: ${e.message}")
                }
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(context, "¡GUERRERO REGISTRADO! 🥋✅", Toast.LENGTH_LONG).show()
                        parentFragmentManager.popBackStack()
                    } else {
                        val errorBody = response.errorBody()?.string() ?: ""
                        val errorMessage = if (errorBody.contains("cédula ya está registrada")) {
                            "ESTA CÉDULA YA PERTENECE A OTRO GUERRERO 🛡️"
                        } else {
                            "EL DOJO RECHAZÓ EL REGISTRO: $errorBody"
                        }
                        android.util.Log.e("AddAthlete", "Fallo Servidor: $errorBody")
                        Toast.makeText(context, "⚠️ $errorMessage", Toast.LENGTH_LONG).show()
                        btnSave.isEnabled = true
                        btnSave.text = if (editingAthlete != null) "Actualizar Atleta" else "Guardar Atleta"
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.e("AddAthlete", "Fallo Crítico en Blindaje", t)
                withContext(Dispatchers.Main) {
                    val msg = when(t) {
                        is OutOfMemoryError -> "MEMORIA INSUFICIENTE: Foto demasiado grande"
                        else -> "FALLO DE CONEXIÓN: Verifique su internet 📡"
                    }
                    Toast.makeText(context, "⚠️ $msg", Toast.LENGTH_LONG).show()
                    btnSave.isEnabled = true
                    btnSave.text = if (editingAthlete != null) "Actualizar Atleta" else "Guardar Atleta"
                }
            }
 finally {
                // Garbage Collection hint for extreme devices
                System.gc()
            }
        }
    }

    private suspend fun robustCompress(uri: Uri): File? {
        val appContext = context?.applicationContext ?: return null
        return try {
            // 🥋 Decodificación Atómica: Usamos el motor de Coil que maneja EXIF y rotación automáticamente
            val imageLoader = appContext.imageLoader
            val request = ImageRequest.Builder(appContext)
                .data(uri)
                .allowHardware(false) // Necesario para manipulación de bits
                .size(1600, 1600) // Límite de seguridad para RAM
                .build()

            val result = imageLoader.execute(request)
            if (result !is SuccessResult) {
                android.util.Log.e("AddAthlete", "Fallo al decodificar con Coil")
                return null
            }

            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return null
            
            // Creamos el archivo final. Coil ya aplicó la rotación física necesaria.
            val tempFile = File(appContext.cacheDir, "kuma_final_${System.currentTimeMillis()}.jpg")
            java.io.FileOutputStream(tempFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos)
            }
            
            android.util.Log.d("AddAthlete", "Foto final generada verticalmente por Coil: ${tempFile.length() / 1024} KB")
            tempFile
        } catch (e: Exception) {
            android.util.Log.e("AddAthlete", "Error crítico procesando foto", e)
            null
        }
    }

    private fun fixImageOrientation(context: android.content.Context, uri: Uri, bitmap: Bitmap): Bitmap {
        // Obsoleto: Reemplazado por el motor de Coil en robustCompress
        return bitmap
    }
}
