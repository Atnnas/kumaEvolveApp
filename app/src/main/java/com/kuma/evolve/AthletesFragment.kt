package com.kuma.evolve

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import com.kuma.evolve.data.Athlete
import com.kuma.evolve.network.RetrofitClient
import com.kuma.evolve.network.DeleteMultipleRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.google.android.material.floatingactionbutton.FloatingActionButton

class AthletesFragment : Fragment() {
    
    private lateinit var adapter: AthletesAdapter
    private val athletes = mutableListOf<Athlete>()
    private var selectedCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_athletes, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvAthletes = view.findViewById<RecyclerView>(R.id.rv_athletes)
        val fabAdd = view.findViewById<FloatingActionButton>(R.id.fab_add_athlete)
        
        adapter = AthletesAdapter(athletes) { count ->
            selectedCount = count
            activity?.invalidateOptionsMenu()
        }
        rvAthletes.adapter = adapter

        fabAdd.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AddAthleteFragment())
                .addToBackStack(null)
                .commit()
        }

        loadAthletes()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.athletes_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        val editItem = menu.findItem(R.id.action_edit)
        val deleteItem = menu.findItem(R.id.action_delete)
        
        editItem?.isVisible = selectedCount == 1
        deleteItem?.isVisible = selectedCount > 0
        
        super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_edit -> {
                val athlete = adapter.getSelectedAthlete()
                if (athlete != null) {
                    // --- TRANSACTION ARMOR ---
                    // Don't carry the heavy Base64 string in the fragment transaction.
                    // This prevents TransactionTooLargeException on high-res devices.
                    val safeAthlete = athlete.copy(imageUrl = null)
                    
                    val fragment = AddAthleteFragment.newInstance(safeAthlete)
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, fragment)
                        .addToBackStack(null)
                        .commit()
                }
                true
            }
            R.id.action_delete -> {
                deleteSelectedAthletes()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun deleteSelectedAthletes() {
        val ids = adapter.getSelectedIds()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.instance.deleteMultipleAthletes(DeleteMultipleRequest(ids)).execute()
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(context, "Atletas eliminados", Toast.LENGTH_SHORT).show()
                        adapter.clearSelection()
                        selectedCount = 0
                        activity?.invalidateOptionsMenu()
                        loadAthletes()
                    } else {
                        Toast.makeText(context, "Error al eliminar", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error de red: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadAthletes() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.instance.getAthletes().execute()
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        athletes.clear()
                        athletes.addAll(response.body()!!)
                        adapter.clearSelection()
                        adapter.notifyDataSetChanged() // 🥋 IMPORTANTE: Notificar al adaptador
                        selectedCount = 0
                        activity?.invalidateOptionsMenu()
                    } else {
                        Toast.makeText(context, "Error al cargar atletas", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Fallo de conexión: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
