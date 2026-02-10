package com.kuma.evolve

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.kuma.evolve.auth.AuthManager
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private lateinit var authManager: AuthManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        authManager = AuthManager(requireContext())
        val logoCard = view.findViewById<View>(R.id.logo_card)

        // Logo Animation: Heartbeat
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.08f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.08f)
        
        ObjectAnimator.ofPropertyValuesHolder(logoCard, scaleX, scaleY).apply {
            duration = 1200
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        val btnLogin = view.findViewById<View>(R.id.btn_google_login)
        val credentialManager = CredentialManager.create(requireContext())

        fun updateUI() {
            val user = authManager.currentUser
            if (user != null) {
                btnLogin.visibility = View.GONE
                (activity as? MainActivity)?.updateNavHeader()
            } else {
                btnLogin.visibility = View.VISIBLE
            }
        }

        updateUI()

        fun performLogin() {
            val serverClientId = getString(R.string.google_web_client_id)
            Log.d("KumaAuth", "Iniciando login con ClientID: $serverClientId")

            val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(true)
                .build()

            val request: GetCredentialRequest = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            lifecycleScope.launch {
                try {
                    val result = credentialManager.getCredential(
                        request = request,
                        context = requireContext(),
                    )
                    handleSignIn(result)
                } catch (e: GetCredentialException) {
                    Log.e("KumaAuth", "Error CredentialManager: ${e.message}")
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnLogin.setOnClickListener {
            performLogin()
        }
    }

    private fun handleSignIn(result: GetCredentialResponse) {
        try {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
            val idToken = googleIdTokenCredential.idToken
            Log.d("KumaAuth", "Token obtenido mediante createFrom, validando con Firebase...")
            
            authManager.signInWithGoogle(idToken) { success ->
                if (success) {
                    val user = authManager.currentUser
                    Log.d("KumaAuth", "Login exitoso: ${user?.displayName}")
                    view?.findViewById<View>(R.id.btn_google_login)?.visibility = View.GONE
                    (activity as? MainActivity)?.updateNavHeader()
                    Toast.makeText(context, "¡Bienvenido ${user?.displayName}!", Toast.LENGTH_SHORT).show()
                } else {
                    Log.e("KumaAuth", "Firebase falló al autenticar el token")
                    Toast.makeText(context, "Error de validación en Firebase", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e("KumaAuth", "Error al procesar la credencial: ${e.message}")
            Toast.makeText(context, "Error al procesar la cuenta de Google", Toast.LENGTH_SHORT).show()
        }
    }
}
