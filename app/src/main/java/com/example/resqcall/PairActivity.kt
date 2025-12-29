package com.example.resqcall

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.resqcall.api.RetrofitClient
import com.example.resqcall.data.PairRequest
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class PairActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pair)

        val btnPair = findViewById<Button>(R.id.btnPair)
        val etId = findViewById<EditText>(R.id.etDeviceId)
        val etPin = findViewById<EditText>(R.id.etPin)

        btnPair.setOnClickListener {
            val deviceId = etId.text.toString()
            val pin = etPin.text.toString()
            val uid = Firebase.auth.currentUser?.uid ?: ""

            if (deviceId.isNotEmpty() && pin.isNotEmpty()) {
                performPairing(deviceId, pin, uid)
            }
        }
    }

    private fun performPairing(deviceId: String, pin: String, uid: String) {
        val request = PairRequest(deviceId, pin, uid)

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.pairDevice(request)
                if (response.isSuccessful) {
                    Toast.makeText(this@PairActivity, "Successfully Paired!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@PairActivity, DashboardActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this@PairActivity, "Invalid ID or PIN", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@PairActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}