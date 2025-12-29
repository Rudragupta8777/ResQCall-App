package com.example.resqcall

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.resqcall.adapter.WearerAdapter
import com.example.resqcall.api.RetrofitClient
import com.example.resqcall.data.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.launch
import org.json.JSONObject

class DashboardActivity : AppCompatActivity() {

    private lateinit var mSocket: Socket
    private lateinit var batteryBanner: View
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: WearerAdapter
    private lateinit var loadingProgress: LinearProgressIndicator
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private var userRole: String? = null
    private var monitoredList = mutableListOf<MonitoredUser>()

    private val scanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val scannedUid = result.data?.getStringExtra("SCANNED_UID")
            scannedUid?.let { mapWearerToCaregiver(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        loadingProgress = findViewById(R.id.loadingProgress)
        swipeRefresh = findViewById(R.id.swipeRefresh)

        // Setup Pull-to-Refresh
        swipeRefresh.setOnRefreshListener {
            fetchUserStatus()
        }

        // Optional: Customize SwipeRefresh colors to match your theme
        swipeRefresh.setColorSchemeColors(Color.parseColor("#2196F3"))
        swipeRefresh.setProgressBackgroundColorSchemeColor(Color.parseColor("#1E1E1E"))

        requestAppPermissions()
        initViews()
        setupSocketConnection()
        fetchUserStatus()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.wearerRecyclerView)
        batteryBanner = findViewById(R.id.batteryBanner)

        findViewById<View>(R.id.btnLogout)?.setOnClickListener { performLogout() }

        adapter = WearerAdapter(monitoredList,
            onLongClick = { item -> showRenameDialog(item) },
            onResolve = { item -> confirmResolution(item) },
            onDirections = { lat, lon -> openGoogleMaps(lat, lon) }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun requestAppPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        }
    }

    private fun fetchUserStatus() {
        // Show LinearProgress only if it's NOT a pull-to-refresh action
        if (!swipeRefresh.isRefreshing) {
            loadingProgress.visibility = View.VISIBLE
        }

        Firebase.auth.currentUser?.getIdToken(false)?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val idToken = task.result.token ?: ""
                FirebaseMessaging.getInstance().token.addOnCompleteListener { fcmTask ->
                    val fcmToken = fcmTask.result ?: ""
                    lifecycleScope.launch {
                        try {
                            val response = RetrofitClient.instance.syncUser(AuthRequest(idToken, fcmToken))

                            // Hide both indicators
                            loadingProgress.visibility = View.GONE
                            swipeRefresh.isRefreshing = false

                            if (response.isSuccessful) {
                                val user = response.body()
                                userRole = user?.role
                                setupRoleUI(userRole)
                                monitoredList.clear()
                                if (userRole == "wearer") {
                                    user?.let { monitoredList.add(MonitoredUser(it, "My Device")) }
                                    subscribeToSocketRoom(user?._id ?: "")
                                } else {
                                    user?.monitoring?.let {
                                        monitoredList.addAll(it)
                                        it.forEach { item -> subscribeToSocketRoom(item.wearer._id) }
                                    }
                                }
                                adapter.notifyDataSetChanged()
                            }
                        } catch (e: Exception) {
                            loadingProgress.visibility = View.GONE
                            swipeRefresh.isRefreshing = false
                            Log.e("Dashboard", "Sync Error: ${e.message}")
                        }
                    }
                }
            } else {
                loadingProgress.visibility = View.GONE
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun confirmResolution(item: MonitoredUser) {
        val alertId = item.activeAlert?._id
        if (alertId == null) {
            Toast.makeText(this, "No active alert found", Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(this, R.style.Theme_ResQcall_DarkDialog)
            .setTitle("Confirm Safety")
            .setMessage("Is the wearer safe now?")
            .setPositiveButton("Yes, Resolved") { _, _ ->
                resolveAlertOnServer(alertId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resolveAlertOnServer(alertId: String) {
        loadingProgress.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.resolveAlert(ResolveRequest(alertId))
                if (response.isSuccessful) {
                    Toast.makeText(this@DashboardActivity, "Alert Resolved", Toast.LENGTH_SHORT).show()
                    fetchUserStatus()
                } else {
                    loadingProgress.visibility = View.GONE
                }
            } catch (e: Exception) {
                loadingProgress.visibility = View.GONE
                Toast.makeText(this@DashboardActivity, "Resolution failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSocketConnection() {
        try {
            mSocket = IO.socket("https://7237b85ffa34.ngrok-free.app/")
            mSocket.connect()

            mSocket.on("device_status") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    runOnUiThread {
                        adapter.updateBattery(data.getString("wearerId"), data.getInt("battery"))
                    }
                }
            }

            mSocket.on("new_alert") { runOnUiThread { fetchUserStatus() } }
            mSocket.on("alert_resolved") { runOnUiThread { fetchUserStatus() } }

        } catch (e: Exception) {
            Log.e("Socket", "Connection error: ${e.message}")
        }
    }

    private fun openGoogleMaps(lat: Double, lon: Double) {
        val gmmIntentUri = Uri.parse("google.navigation:q=$lat,$lon")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")
        if (mapIntent.resolveActivity(packageManager) != null) {
            startActivity(mapIntent)
        } else {
            Toast.makeText(this, "Google Maps not found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performLogout() {
        MaterialAlertDialogBuilder(this, R.style.Theme_ResQcall_DarkDialog)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                Firebase.auth.signOut()
                if (::mSocket.isInitialized) mSocket.disconnect()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameDialog(item: MonitoredUser) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_rename, null)
        val editText = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.nicknameEditText)
        editText.setText(item.nickname)

        MaterialAlertDialogBuilder(this, R.style.Theme_ResQcall_DarkDialog)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) renameOnServer(item.wearer._id, newName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renameOnServer(wearerId: String, nickname: String) {
        val caregiverUid = Firebase.auth.currentUser?.uid ?: return
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.renameWearer(RenameRequest(caregiverUid, wearerId, nickname))
                if (response.isSuccessful) fetchUserStatus()
            } catch (e: Exception) {
                Toast.makeText(this@DashboardActivity, "Rename failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun subscribeToSocketRoom(roomId: String) {
        if (::mSocket.isInitialized && roomId.isNotEmpty()) {
            mSocket.emit("subscribe_to_wearer", roomId)
        }
    }

    private fun setupRoleUI(role: String?) {
        findViewById<View>(R.id.fabShowQR).visibility = if (role == "wearer") View.VISIBLE else View.GONE
        findViewById<View>(R.id.fabScanQR).visibility = if (role == "caregiver") View.VISIBLE else View.GONE
        findViewById<View>(R.id.fabShowQR).setOnClickListener { showMyQR() }
        findViewById<View>(R.id.fabScanQR).setOnClickListener { startScanner() }
    }

    private fun startScanner() {
        val intent = Intent(this, ScannerActivity::class.java)
        scanLauncher.launch(intent)
    }

    private fun mapWearerToCaregiver(wearerUid: String) {
        val caregiverUid = Firebase.auth.currentUser?.uid ?: return
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.addCaregiver(AddCaregiverRequest(wearerUid, caregiverUid))
                if (response.isSuccessful) {
                    Toast.makeText(this@DashboardActivity, "Linked!", Toast.LENGTH_SHORT).show()
                    fetchUserStatus()
                }
            } catch (e: Exception) {
                Log.e("Dashboard", "Mapping failed: ${e.message}")
            }
        }
    }

    private fun showMyQR() {
        val myUid = Firebase.auth.currentUser?.uid ?: return
        try {
            val bitmap = generateQRCode(myUid)
            val imageView = ImageView(this).apply {
                setPadding(64, 64, 64, 64)
                setImageBitmap(bitmap)
                setBackgroundColor(Color.WHITE)
            }
            AlertDialog.Builder(this).setTitle("Pair Caregiver").setView(imageView).show()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun generateQRCode(text: String): Bitmap {
        val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 512, 512)
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565)
        for (x in 0 until 512) {
            for (y in 0 until 512) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::mSocket.isInitialized) mSocket.disconnect()
    }
}