package com.kuma.evolve.data

import java.io.Serializable
import java.util.Date

data class Attendance(
    val _id: String? = null,
    val attendanceNumber: Int,
    val timestamp: Date,
    val athleteRef: AthleteRef? = null,  // Populated athlete reference
    val studentName: String,
    val photoUrl: String?,
    val isVisitor: Boolean = false,
    val recognitionConfidence: Double? = null,
    val registrationMode: String,  // "facial" or "manual"
    val editHistory: List<EditRecord> = emptyList()
) : Serializable

data class AthleteRef(
    val _id: String,
    val name: String,
    val idCard: String
) : Serializable

data class EditRecord(
    val editedAt: Date,
    val previousName: String,
    val editedBy: String
) : Serializable
