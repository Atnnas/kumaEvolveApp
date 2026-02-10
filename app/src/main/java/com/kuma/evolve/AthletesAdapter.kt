package com.kuma.evolve

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.kuma.evolve.data.Athlete

class AthletesAdapter(
    private val athletes: List<Athlete>,
    private val onSelectionChanged: (selectedCount: Int) -> Unit
) : RecyclerView.Adapter<AthletesAdapter.AthleteViewHolder>() {

    private val selectedIds = mutableSetOf<String>()

    class AthleteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: CheckBox = view.findViewById(R.id.athlete_checkbox)
        val txtConsecutive: TextView = view.findViewById(R.id.athlete_consecutive)
        val imgPhoto: ImageView = view.findViewById(R.id.athlete_image)
        val txtIdCard: TextView = view.findViewById(R.id.athlete_id_card)
        val txtName: TextView = view.findViewById(R.id.athlete_name)
        val txtAge: TextView = view.findViewById(R.id.athlete_age)
        val txtRank: TextView = view.findViewById(R.id.athlete_rank)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AthleteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_athlete, parent, false)
        return AthleteViewHolder(view)
    }

    override fun onBindViewHolder(holder: AthleteViewHolder, position: Int) {
        val athlete = athletes[position]
        
        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.isChecked = selectedIds.contains(athlete._id)
        
        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            athlete._id?.let { id ->
                if (isChecked) selectedIds.add(id) else selectedIds.remove(id)
                onSelectionChanged(selectedIds.size)
            }
        }

        holder.txtConsecutive.text = athlete.consecutive?.toString() ?: "-"
        holder.txtIdCard.text = athlete.idCard
        holder.txtName.text = athlete.name
        holder.txtAge.text = athlete.age.toString()
        holder.txtRank.text = athlete.grade ?: "-"

        // --- ROBUST IMAGE LOADING ---
        val imageUrl = athlete.imageUrl
        if (imageUrl != null && imageUrl.startsWith("data:")) {
            try {
                // Extract base64 part and handle corrupt prefixes client-side
                val base64Data = if (imageUrl.contains("base64,")) {
                    imageUrl.substring(imageUrl.indexOf("base64,") + 7)
                } else {
                    imageUrl
                }
                
                val imageBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                holder.imgPhoto.load(imageBytes) {
                    crossfade(true)
                    placeholder(android.R.drawable.presence_invisible)
                    error(android.R.drawable.presence_offline)
                    size(100, 100)
                    transformations(CircleCropTransformation())
                }
            } catch (e: Exception) {
                android.util.Log.e("AthletesAdapter", "Error decodificando foto para ${athlete.name}", e)
                holder.imgPhoto.load(android.R.drawable.presence_offline)
            }
        } else {
            holder.imgPhoto.load(android.R.drawable.presence_invisible)
        }
    }

    fun getSelectedIds(): List<String> = selectedIds.toList()
    
    fun getSelectedAthlete(): Athlete? {
        if (selectedIds.size == 1) {
            val id = selectedIds.first()
            return athletes.find { it._id == id }
        }
        return null
    }

    fun clearSelection() {
        selectedIds.clear()
        notifyDataSetChanged()
    }

    override fun getItemCount() = athletes.size
}
