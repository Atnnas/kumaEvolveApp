package com.kuma.evolve

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.kuma.evolve.data.Athlete
import com.kuma.evolve.data.Attendance
import com.kuma.evolve.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class AttendanceFragment : Fragment() {

    private lateinit var rvAttendance: RecyclerView
    private lateinit var adapter: AttendanceAdapter
    private lateinit var btnDateFrom: Button
    private lateinit var btnDateTo: Button
    private lateinit var spinnerAthleteFilter: AutoCompleteTextView
    private lateinit var fabAddAttendance: FloatingActionButton

    private val attendances = mutableListOf<Attendance>()
    private val athletes = mutableListOf<Athlete>()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    
    private var dateFrom: Date? = null
    private var dateTo: Date? = null
    private var selectedAthleteId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_attendance, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvAttendance = view.findViewById(R.id.rv_attendance)
        btnDateFrom = view.findViewById(R.id.btn_date_from)
        btnDateTo = view.findViewById(R.id.btn_date_to)
        spinnerAthleteFilter = view.findViewById(R.id.spinner_athlete_filter)
        fabAddAttendance = view.findViewById(R.id.fab_add_attendance)

        // Setup RecyclerView
        adapter = AttendanceAdapter(attendances)
        rvAttendance.layoutManager = LinearLayoutManager(context)
        rvAttendance.adapter = adapter

        // Setup listeners
        btnDateFrom.setOnClickListener { showDatePickerFrom() }
        btnDateTo.setOnClickListener { showDatePickerTo() }
        fabAddAttendance.setOnClickListener { openAttendanceCamera() }

        // Load initial data
        loadAthletes()
        loadAttendances()
    }

    private fun loadAthletes() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.instance.getAthletes().execute()
                if (response.isSuccessful && response.body() != null) {
                    athletes.clear()
                    athletes.addAll(response.body()!!)
                    
                    withContext(Dispatchers.Main) {
                        setupAthleteFilter()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("Attendance", "Error loading athletes", e)
            }
        }
    }

    private fun setupAthleteFilter() {
        val items = mutableListOf("Todos los atletas")
        items.addAll(athletes.map { it.name })

        val arrayAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, items)
        spinnerAthleteFilter.setAdapter(arrayAdapter)
        spinnerAthleteFilter.setText("Todos los atletas", false)

        spinnerAthleteFilter.setOnItemClickListener { _, _, position, _ ->
            selectedAthleteId = if (position == 0) null else athletes[position - 1]._id
            loadAttendances()
        }
    }

    private fun loadAttendances() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val fromParam = dateFrom?.let { dateFormat.format(it) }
                val toParam = dateTo?.let { dateFormat.format(it) }
                
                val response = RetrofitClient.instance.getAttendances(
                    from = fromParam,
                    to = toParam,
                    athleteId = selectedAthleteId
                ).execute()

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        attendances.clear()
                        attendances.addAll(response.body()!!)
                        adapter.notifyDataSetChanged()
                    } else {
                        Toast.makeText(context, "Error al cargar asistencias", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error de conexión: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showDatePickerFrom() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Seleccionar fecha desde")
            .setSelection(dateFrom?.time ?: MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            dateFrom = Date(selection)
            btnDateFrom.text = dateFormat.format(dateFrom!!)
            loadAttendances()
        }

        picker.show(parentFragmentManager, "DATE_PICKER_FROM")
    }

    private fun showDatePickerTo() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Seleccionar fecha hasta")
            .setSelection(dateTo?.time ?: MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            dateTo = Date(selection)
            btnDateTo.text = dateFormat.format(dateTo!!)
            loadAttendances()
        }

        picker.show(parentFragmentManager, "DATE_PICKER_TO")
    }

    private fun openAttendanceCamera() {
        val intent = android.content.Intent(requireContext(), AttendanceCameraActivity::class.java)
        startActivity(intent)
    }
}
