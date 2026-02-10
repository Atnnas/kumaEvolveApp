package com.kuma.evolve

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import com.kuma.evolve.data.Athlete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AthletesFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_athletes, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvAthletes = view.findViewById<RecyclerView>(R.id.rv_athletes)
        val athletes = mutableListOf<Athlete>()
        val adapter = AthletesAdapter(athletes)
        rvAthletes.adapter = adapter

        // Fetch from MongoDB using Native Driver
        // Fetch from Backend using Retrofit
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = com.kuma.evolve.network.RetrofitClient.instance.getAthletes().execute()
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        athletes.clear()
                        response.body()!!.forEach { netAthlete ->
                            athletes.add(Athlete(
                                name = netAthlete.name,
                                category = netAthlete.category,
                                rank = netAthlete.rank,
                                imageUrl = netAthlete.imageUrl
                            ))
                        }
                        adapter.notifyDataSetChanged()
                    } else {
                        Toast.makeText(context, "Error al cargar atletas", Toast.LENGTH_SHORT).show()
                        useFallbackData(athletes, adapter)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Fallo de conexión: ${e.message}", Toast.LENGTH_SHORT).show()
                    useFallbackData(athletes, adapter)
                }
            }
        }
    }

    private fun useFallbackData(athletes: MutableList<Athlete>, adapter: AthletesAdapter) {
        athletes.clear()
        athletes.addAll(listOf(
            Athlete("Ryo Kiyuna", "Kata", "Legendary Gold", "https://via.placeholder.com/150"),
            Athlete("Douglas Brose", "Kumite -60kg", "Elite Fighter", "https://via.placeholder.com/150"),
            Athlete("Anzhelika Terliuga", "Kumite -55kg", "Olympic Silver", "https://via.placeholder.com/150"),
            Athlete("Kuma Master", "All-rounder", "Sensei Rank", "https://via.placeholder.com/150")
        ))
        adapter.notifyDataSetChanged()
    }
}
