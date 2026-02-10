package com.kuma.evolve.network

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

// Data Models
data class UserRequest(
    val uid: String,
    val email: String,
    val name: String,
    @SerializedName("photoUrl") val photoUrl: String?
)

data class UserResponse(
    val success: Boolean,
    val user: UserData?
)

data class UserData(
    val uid: String,
    val email: String,
    val name: String
)

data class Athlete(
    val _id: String,
    val name: String,
    val category: String,
    val rank: String,
    val imageUrl: String
)

interface ApiService {
    @POST("/api/auth")
    fun syncUser(@Body user: UserRequest): Call<UserResponse>

    @GET("/api/athletes")
    fun getAthletes(): Call<List<Athlete>>
}
