package com.kuma.evolve

import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.imageview.ShapeableImageView
import com.kuma.evolve.data.Attendance
import java.text.SimpleDateFormat
import java.util.*

class AttendanceAdapter(
    private val attendances: List<Attendance>
) : RecyclerView.Adapter<AttendanceAdapter.AttendanceViewHolder>() {

    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    class AttendanceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPhoto: ShapeableImageView = view.findViewById(R.id.iv_attendance_photo)
        val tvName: TextView = view.findViewById(R.id.tv_attendance_name)
        val tvTime: TextView = view.findViewById(R.id.tv_attendance_time)
        val tvMode: TextView = view.findViewById(R.id.tv_attendance_mode)
        val tvNumber: TextView = view.findViewById(R.id.tv_attendance_number)
        val tvVisitorBadge: TextView = view.findViewById(R.id.tv_visitor_badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendanceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendance, parent, false)
        return AttendanceViewHolder(view)
    }

    override fun onBindViewHolder(holder: AttendanceViewHolder, position: Int) {
        val attendance = attendances[position]

        // Load photo with robust Base64 decoding
        attendance.evidencePhotoUrl?.let { url ->
            loadPhotoFromBase64(url, holder.ivPhoto)
        }

        // Name (use athleteRef if available, otherwise studentName)
        holder.tvName.text = attendance.athleteRef?.name ?: attendance.studentName

        // Time
        holder.tvTime.text = timeFormat.format(attendance.timestamp)

        // Mode indicator
        val modeIcon = if (attendance.registrationMode == "facial") "🤖" else "✍️"
        val modeText = if (attendance.registrationMode == "facial") "Facial" else "Manual"
        holder.tvMode.text = " • $modeIcon $modeText"

        // Attendance number (daily sequence)
        holder.tvNumber.text = "#${attendance.dailySequence}"

        // Visitor badge
        if (attendance.isVisitor) {
            holder.tvVisitorBadge.visibility = View.VISIBLE
        } else {
            holder.tvVisitorBadge.visibility = View.GONE
        }
    }

    override fun getItemCount() = attendances.size

    private fun loadPhotoFromBase64(imageUrl: String, imageView: ShapeableImageView) {
        try {
            if (imageUrl.startsWith("data:") && imageUrl.contains("base64,")) {
                val base64Data = imageUrl.substring(imageUrl.indexOf("base64,") + 7)
                val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
                imageView.load(imageBytes) {
                    crossfade(true)
                    transformations(CircleCropTransformation())
                    placeholder(android.R.drawable.ic_menu_camera)
                    error(android.R.drawable.ic_dialog_alert)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AttendanceAdapter", "Error decoding photo", e)
            imageView.load(android.R.drawable.ic_menu_camera)
        }
    }
}
