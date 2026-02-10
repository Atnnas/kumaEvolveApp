package com.kuma.evolve.data

data class Athlete(
    val name: String,
    val category: String,
    val rank: String,
    val imageUrl: String? = null
)
