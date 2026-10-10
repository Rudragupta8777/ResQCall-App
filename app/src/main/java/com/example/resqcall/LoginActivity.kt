package com.example.resqcall

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.lifecycle.lifecycleScope
import com.example.resqcall.api.RetrofitClient
import com.example.resqcall.data.AuthRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager
    private lateinit var signInBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = Firebase.auth
        // Created upfront (not on tap) since constructing it can take a noticeable beat
        // the first time Play Services spins up - this way the tap itself is instant.
        credentialManager = CredentialManager.create(this)
        signInBtn = findViewById(R.id.googleSignInBtn)

        signInBtn.setOnClickListener {
            startGoogleLogin()
        }
    }

    private fun setLoading(loading: Boolean) {
        signInBtn.isEnabled = !loading
        signInBtn.alpha = if (loading) 0.6f else 1f
        signInBtn.text = if (loading) "Signing in…" else "Sign in with Google"
    }

    private fun startGoogleLogin() {
        setLoading(true)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(getString(R.string.default_web_client_id))
            .setAutoSelectEnabled(true)
            .setNonce("random_nonce_for_security")
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                val result: GetCredentialResponse = credentialManager.getCredential(this@LoginActivity, request)
                handleSignIn(result.credential)
            } catch (e: Exception) {
                Log.e("Auth", "Login Failed: ${e.message}")
                setLoading(false)
                Toast.makeText(this@LoginActivity, "Sign-in cancelled or failed. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleSignIn(credential: Credential) {
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idTokenFromGoogle = googleIdTokenCredential.idToken

                val firebaseCredential = GoogleAuthProvider.getCredential(idTokenFromGoogle, null)

                // 1. Sign in into Firebase
                auth.signInWithCredential(firebaseCredential).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // 2. Get the Firebase id token
                        val user = auth.currentUser
                        user?.getIdToken(true)?.addOnCompleteListener { tokenTask ->
                            if (tokenTask.isSuccessful) {
                                val firebaseIdToken = tokenTask.result.token
                                if (firebaseIdToken != null) {
                                    syncUserWithBackend(firebaseIdToken)
                                } else {
                                    setLoading(false)
                                }
                            } else {
                                setLoading(false)
                                Toast.makeText(this@LoginActivity, "Couldn't verify your account. Try again.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        setLoading(false)
                        Toast.makeText(this@LoginActivity, "Firebase sign-in failed. Try again.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("Auth", "Error: ${e.message}")
                setLoading(false)
                Toast.makeText(this@LoginActivity, "Sign-in failed. Try again.", Toast.LENGTH_SHORT).show()
            }
        } else {
            setLoading(false)
        }
    }

    private fun syncUserWithBackend(idToken: String) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            // Don't block login just because the FCM token fetch failed - push notifications
            // are secondary to being able to sign in at all.
            if (!task.isSuccessful) {
                Log.w("FCM", "Fetching FCM registration token failed", task.exception)
            }

            val fcmToken = task.result ?: ""
            val authRequest = AuthRequest(idToken, fcmToken)

            lifecycleScope.launch {
                try {
                    val response = RetrofitClient.instance.syncUser(authRequest)
                    if (response.isSuccessful) {
                        val user = response.body()

                        when {
                            user?.role == null -> {
                                startActivity(Intent(this@LoginActivity, RoleSelectionActivity::class.java))
                            }
                            user.role == "wearer" && user.myWearable == null -> {
                                startActivity(Intent(this@LoginActivity, PairActivity::class.java))
                            }
                            else -> {
                                val intent = Intent(this@LoginActivity, DashboardActivity::class.java)
                                intent.putExtra("USER_ROLE", user.role)
                                startActivity(intent)
                            }
                        }
                        finish()
                    } else {
                        setLoading(false)
                        Toast.makeText(this@LoginActivity, "Sync failed (${response.code()}). Please try again.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    setLoading(false)
                    Log.e("Auth", "Network Error: ${e.message}")
                    Toast.makeText(this@LoginActivity, "Sync failed. Please check internet.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}