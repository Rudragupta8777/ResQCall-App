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
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var keepSplashScreen = true

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

        // Timer to release the splash screen after 2000ms
        Handler(Looper.getMainLooper()).postDelayed({
            keepSplashScreen = false
        }, 5000)

        auth = Firebase.auth

        checkUserStatus()
    }

    private fun checkUserStatus() {
        val currentUser = auth.currentUser

        if (currentUser != null) {
            if (intent.hasExtra("type") && intent.getStringExtra("type") == "FALL_DETECTED") {
                val emergencyIntent = Intent(this, EmergencyActivity::class.java).apply {
                    putExtras(intent)
                }
                startActivity(emergencyIntent)
            } else {
                val intent = Intent(this, DashboardActivity::class.java)
                startActivity(intent)
            }
        } else {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
    }
}