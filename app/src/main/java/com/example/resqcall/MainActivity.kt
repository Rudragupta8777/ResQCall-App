package com.example.resqcall

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var keepSplashScreen = true
    private var resolved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Install Splash Screen
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                101
            )
        }

        splashScreen.setKeepOnScreenCondition { keepSplashScreen }

        // Failsafe: never block the splash screen forever if the token check hangs
        // (e.g. no network at all) - fall through optimistically to whatever we have.
        Handler(Looper.getMainLooper()).postDelayed({
            if (!resolved) {
                resolved = true
                keepSplashScreen = false
                routeFromCachedUser()
            }
        }, 4000)

        auth = Firebase.auth

        checkUserStatus()
    }

    private fun checkUserStatus() {
        val currentUser = auth.currentUser

        if (currentUser == null) {
            finishWith { startActivity(Intent(this, LoginActivity::class.java)) }
            return
        }

        // A cached FirebaseUser only means we were logged in at some point - it does not
        // mean the session is still valid. Force a token refresh so an expired/revoked
        // session is caught here and sent back to Login, instead of landing on a Dashboard
        // that silently shows no data.
        currentUser.getIdToken(true)
            .addOnSuccessListener {
                if (resolved) return@addOnSuccessListener
                resolved = true
                keepSplashScreen = false
                routeToApp()
            }
            .addOnFailureListener { e ->
                if (resolved) return@addOnFailureListener
                resolved = true
                keepSplashScreen = false
                if (e is FirebaseAuthInvalidUserException || e is FirebaseAuthInvalidCredentialsException) {
                    // Session genuinely expired/revoked - require a fresh login.
                    auth.signOut()
                    finishWith { startActivity(Intent(this, LoginActivity::class.java)) }
                } else {
                    // Likely just a network hiccup - don't log the user out for that.
                    routeToApp()
                }
            }
    }

    private fun routeFromCachedUser() {
        if (auth.currentUser == null) {
            finishWith { startActivity(Intent(this, LoginActivity::class.java)) }
        } else {
            routeToApp()
        }
    }

    private fun routeToApp() {
        if (intent.hasExtra("type") && intent.getStringExtra("type") == "FALL_DETECTED") {
            finishWith {
                startActivity(Intent(this, EmergencyActivity::class.java).apply {
                    putExtras(intent)
                })
            }
        } else {
            finishWith { startActivity(Intent(this, DashboardActivity::class.java)) }
        }
    }

    private fun finishWith(action: () -> Unit) {
        action()
        finish()
    }
}