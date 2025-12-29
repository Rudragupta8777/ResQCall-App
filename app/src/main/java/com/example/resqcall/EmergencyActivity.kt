package com.example.resqcall

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions

class EmergencyActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private var lat: Double = 0.0
    private var lon: Double = 0.0
    private var phoneNumber: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        setContentView(R.layout.activity_emergency)

        lat = intent.getStringExtra("lat")?.toDouble() ?: 0.0
        lon = intent.getStringExtra("lon")?.toDouble() ?: 0.0
        val wearerName = intent.getStringExtra("wearerName") ?: "User"
        phoneNumber = intent.getStringExtra("wearerPhone")

        findViewById<TextView>(R.id.wearerNameLabel).text = wearerName

        // --- CALL BUTTON LOGIC ---
        findViewById<View>(R.id.btnCall).setOnClickListener {
            if (!phoneNumber.isNullOrEmpty()) {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "No phone number available", Toast.LENGTH_SHORT).show()
            }
        }

        // --- DIRECTIONS BUTTON LOGIC ---
        findViewById<View>(R.id.btnRespond).setOnClickListener {
            val gmmIntentUri = Uri.parse("google.navigation:q=$lat,$lon")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            startActivity(mapIntent)
        }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        startPulseAnimation()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        try {
            val success = mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_dark))
            if (!success) Log.e("Map", "Style parsing failed.")
        } catch (e: Exception) { Log.e("Map", "Can't find style.", e) }

        val location = LatLng(lat, lon)
        mMap.addMarker(MarkerOptions().position(location).title("Fall Location"))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15f))
    }

    private fun startPulseAnimation() {
        val overlay = findViewById<View>(R.id.pulseOverlay)
        val anim = AlphaAnimation(0.1f, 0.5f).apply {
            duration = 800
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        overlay.startAnimation(anim)
    }
}