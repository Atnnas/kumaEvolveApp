package com.kuma.evolve.auth

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AuthManager(private val context: Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO)
    
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun signInWithGoogle(idToken: String, onComplete: (Boolean) -> Unit) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("KumaAuth", "Login exitoso en Firebase")
                    syncUserToMongo(task.result?.user)
                } else {
                    Log.e("KumaAuth", "Error en signInWithCredential: ${task.exception?.message}")
                }
                onComplete(task.isSuccessful)
            }
    }

    fun signOut() {
        auth.signOut()
    }

    private fun syncUserToMongo(user: FirebaseUser?) {
        if (user == null) return
        
        scope.launch {
            try {
                val request = com.kuma.evolve.network.UserRequest(
                    uid = user.uid,
                    email = user.email ?: "",
                    name = user.displayName ?: "Usuario",
                    photoUrl = user.photoUrl?.toString()
                )

                val call = com.kuma.evolve.network.RetrofitClient.instance.syncUser(request)
                val response = call.execute() // Blocking call safe within Dispatchers.IO

                if (response.isSuccessful && response.body()?.success == true) {
                    Log.d("KumaAuth", "Usuario sincronizado con API: ${response.body()?.user?.name}")
                } else {
                    Log.e("KumaAuth", "Error en API Auth: ${response.code()} ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("KumaAuth", "Excepción al conectar con API: ${e.message}")
            }
        }
    }
}
