package com.kuma.evolve

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.kuma.evolve.data.Athlete

class AthletesAdapter(private val athletes: List<Athlete>) :
    RecyclerView.Adapter<AthletesAdapter.AthleteViewHolder>() {

    class AthleteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgPhoto: ImageView = view.findViewById(R.id.athlete_image)
        val txtName: TextView = view.findViewById(R.id.athlete_name)
        val txtCategory: TextView = view.findViewById(R.id.athlete_category)
        val txtRank: TextView = view.findViewById(R.id.athlete_rank)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AthleteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_athlete, parent, false)
        return AthleteViewHolder(view)
    }

    override fun onBindViewHolder(holder: AthleteViewHolder, position: Int) {
        val athlete = athletes[position]
        holder.txtName.text = athlete.name
        holder.txtCategory.text = athlete.category
        holder.txtRank.text = athlete.rank

        holder.imgPhoto.load(athlete.imageUrl) {
            crossfade(true)
            placeholder(android.R.drawable.presence_invisible)
            error(android.R.drawable.stat_notify_error)
            transformations(CircleCropTransformation())
        }
    }

    override fun getItemCount() = athletes.size
}
