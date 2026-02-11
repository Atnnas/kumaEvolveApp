package com.kuma.evolve.network

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.DELETE
import retrofit2.http.Path

import com.kuma.evolve.data.Athlete
import com.kuma.evolve.data.Attendance
import com.kuma.evolve.network.RetrofitClient
import com.kuma.evolve.network.CommonResponse
import com.kuma.evolve.network.DeleteMultipleRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.Query

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

data class AthleteResponse(
    val success: Boolean,
    val athlete: Athlete?
)

data class AttendanceResponse(
    val success: Boolean,
    val attendance: Attendance?
)

interface ApiService {
    @POST("/api/auth")
    fun syncUser(@Body user: UserRequest): Call<UserResponse>

    @GET("/api/athletes")
    fun getAthletes(): Call<List<Athlete>>

    @GET("/api/athletes/{id}")
    fun getAthleteById(@Path("id") id: String): Call<Athlete>

    @Multipart
    @POST("/api/athletes")
    fun registerAthlete(
        @Part("idCard") idCard: RequestBody,
        @Part("name") name: RequestBody,
        @Part("birthDate") birthDate: RequestBody,
        @Part("grade") grade: RequestBody,
        @Part("weight") weight: RequestBody,
        @Part("consecutive") consecutive: RequestBody?,
        @Part image: MultipartBody.Part?
    ): Call<AthleteResponse>

    @Multipart
    @PUT("/api/athletes/{id}")
    fun updateAthlete(
        @Path("id") id: String,
        @Part("idCard") idCard: RequestBody,
        @Part("name") name: RequestBody,
        @Part("birthDate") birthDate: RequestBody,
        @Part("grade") grade: RequestBody,
        @Part("weight") weight: RequestBody,
        @Part image: MultipartBody.Part?
    ): Call<AthleteResponse>

    @DELETE("/api/athletes/{id}")
    fun deleteAthlete(@Path("id") id: String): Call<CommonResponse>

    @POST("/api/athletes/delete-multiple")
    fun deleteMultipleAthletes(@Body request: DeleteMultipleRequest): Call<CommonResponse>

    // Attendance endpoints
    @Multipart
    @POST("/api/attendance")
    fun registerAttendance(
        @Part("athleteId") athleteId: RequestBody?,
        @Part("studentName") studentName: RequestBody,
        @Part("registrationMode") registrationMode: RequestBody,
        @Part("recognitionConfidence") recognitionConfidence: RequestBody?,
        @Part image: MultipartBody.Part
    ): Call<AttendanceResponse>

    @GET("/api/attendance")
    fun getAttendances(
        @Query("from") from: String?,
        @Query("to") to: String?,
        @Query("athleteId") athleteId: String?
    ): Call<List<Attendance>>

    @PUT("/api/attendance/{id}")
    fun updateAttendance(
        @Path("id") id: String,
        @Body updateData: Map<String, String>
    ): Call<AttendanceResponse>

    @DELETE("/api/attendance/{id}")
    fun deleteAttendance(@Path("id") id: String): Call<CommonResponse>
    @Multipart
    @POST("/api/attendance/scan")
    fun scanFace(
        @Part image: MultipartBody.Part
    ): Call<RecognitionResponse>

    @Multipart
    @POST("/api/attendance/recognize")
    fun recognizeFace(
        @Part image: MultipartBody.Part
    ): Call<RecognitionResponse>

    @GET("/api/attendance/export")
    fun exportAttendance(
        @Query("from") from: String?,
        @Query("to") to: String?,
        @Query("athleteId") athleteId: String?
    ): Call<okhttp3.ResponseBody>

    @GET("/api/attendance/stats")
    fun getAttendanceStats(): Call<AttendanceStats>

    @POST("/api/athletes/{id}/enroll")
    fun enrollAthlete(
        @Path("id") id: String,
        @Body enrollment: EnrollmentRequest
    ): Call<okhttp3.ResponseBody>
}

data class EnrollmentRequest(
    val descriptors: List<List<Double>>
)

data class AttendanceStats(
    val total: Int,
    val facial: Int,
    val visitors: Int,
    val today: Int,
    val facialPercentage: Int
)

data class RecognitionResponse(
    val success: Boolean,
    val recognized: Boolean,
    val athleteId: String?,
    val name: String?,
    val confidence: Int?,
    val message: String? = null,
    val descriptor: List<Double>? = null,
    val attendance: com.kuma.evolve.data.Attendance? = null
)

data class CommonResponse(val success: Boolean, val message: String? = null, val error: String? = null)
data class DeleteMultipleRequest(val ids: List<String>)
