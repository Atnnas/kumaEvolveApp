package com.kuma.evolve.data

import java.io.Serializable
import java.util.*

data class Athlete(
    val idCard: String,
    val name: String,
    val birthDate: Date,
    val grade: String? = null,
    val weight: Double? = null,
    val imageUrl: String? = null,
    val consecutive: Int? = null,
    val _id: String? = null
) : Serializable {
    // Helper to calculate age automatically
    val age: Int
        get() {
            val today = Calendar.getInstance()
            val birth = Calendar.getInstance()
            birth.time = birthDate
            var age = today.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
            if (today.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) {
                age--
            }
            return age
        }
}
