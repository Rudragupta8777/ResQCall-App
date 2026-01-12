package com.example.resqcall

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.resqcall.adapter.HistoryAdapter
import com.example.resqcall.api.RetrofitClient
import com.example.resqcall.data.AlertData
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private var wearerId: String? = null
    private var wearerName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        wearerId = intent.getStringExtra("WEARER_ID")
        wearerName = intent.getStringExtra("WEARER_NAME")

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = "$wearerName's History"
        toolbar.setNavigationOnClickListener { finish() }

        recyclerView = findViewById(R.id.historyRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        fetchHistory()
    }

    private fun fetchHistory() {
        val id = wearerId ?: return
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getAlertHistory(id)
                if (response.isSuccessful) {
                    val alerts = response.body() ?: emptyList()
                    recyclerView.adapter = HistoryAdapter(alerts) { lat, lon ->
                        val gmmIntentUri = Uri.parse("google.navigation:q=$lat,$lon")
                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                        mapIntent.setPackage("com.google.android.apps.maps")
                        startActivity(mapIntent)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@HistoryActivity, "Error loading history", Toast.LENGTH_SHORT).show()
            }
        }
    }
}