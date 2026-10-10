package com.example.resqcall

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
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
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.example.resqcall.util.showError
import com.example.resqcall.util.showMessage
import com.example.resqcall.util.showSuccess
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

        swipeRefresh.setOnRefreshListener { fetchUserStatus() }
        swipeRefresh.setColorSchemeColors(Color.parseColor("#0D9488"))
        swipeRefresh.setProgressBackgroundColorSchemeColor(Color.parseColor("#FFFFFF"))

        requestAppPermissions()
        initViews()
        setupSocketConnection()
        fetchUserStatus()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.wearerRecyclerView)
        batteryBanner = findViewById(R.id.batteryBanner)

        findViewById<View>(R.id.btnLogout)?.setOnClickListener { performLogout() }

        // Setup Adapter with 5 parameters matching your updated WearerAdapter constructor
        adapter = WearerAdapter(
            monitoredList,
            onLongClick = { item -> showManagementMenu(item) }, // Now opens a Menu (Edit/Delete)
            onResolve = { item -> confirmResolution(item) },
            onDirections = { lat, lon -> openGoogleMaps(lat, lon) },
            onHistory = { item -> navigateToHistory(item) },    // "Fall History" action
            onVitals = { item -> navigateToVitals(item) }       // "Health Vitals" action
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    // --- NEW: MANAGEMENT MENU (Edit vs Delete) ---
    private fun showManagementMenu(item: MonitoredUser) {
        if (userRole != "caregiver") return

        val options = arrayOf("Edit Nickname", "Stop Monitoring (Remove)")

        MaterialAlertDialogBuilder(this, R.style.Theme_ResQcall_DarkDialog)
            .setTitle("Manage ${item.nickname ?: item.wearer.name}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameDialog(item)
                    1 -> confirmDeletion(item)
                }
            }
            .show()
    }

    private fun confirmDeletion(item: MonitoredUser) {
        MaterialAlertDialogBuilder(this, R.style.Theme_ResQcall_DarkDialog)
            .setTitle("Stop Monitoring?")
            .setMessage("Are you sure you want to remove ${item.nickname ?: item.wearer.name}? You will no longer receive their alerts.")
            .setPositiveButton("Remove") { _, _ ->
                deleteWearerFromServer(item.wearer._id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteWearerFromServer(wearerId: String) {
        val caregiverUid = Firebase.auth.currentUser?.uid ?: return
        loadingProgress.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.removeWearer(RemoveWearerRequest(caregiverUid, wearerId))
                if (response.isSuccessful) {
                    showSuccess("Removed successfully")
                    fetchUserStatus()
                } else {
                    loadingProgress.visibility = View.GONE
                    showError("Failed to remove")
                }
            } catch (e: Exception) {
                loadingProgress.visibility = View.GONE
                Log.e("Delete", "Error: ${e.message}")
            }
        }
    }

    private fun navigateToHistory(item: MonitoredUser) {
        val intent = Intent(this, HistoryActivity::class.java).apply {
            putExtra("WEARER_ID", item.wearer._id)
            putExtra("WEARER_NAME", item.nickname ?: item.wearer.name)
        }
        startActivity(intent)
    }

    private fun navigateToVitals(item: MonitoredUser) {
        val intent = Intent(this, VitalsActivity::class.java).apply {
            putExtra("WEARER_ID", item.wearer._id)
            putExtra("WEARER_NAME", item.nickname ?: item.wearer.name)
        }
        startActivity(intent)
    }

    private fun setupSocketConnection() {
        try {
            // Using Base URL from RetrofitClient as requested
            mSocket = IO.socket(RetrofitClient.BASE_URL)
            mSocket.connect()

            mSocket.on("device_status") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    runOnUiThread {
                        // wearerId isn't guaranteed on every status payload (the offline-timeout
                        // event in particular may only carry {"status":"offline"}), so this is
                        // read defensively rather than assumed present.
                        val wearerId = data.optString("wearerId")
                        val status = data.optString("status", "online")
                        if (status == "offline") {
                            if (wearerId.isNotEmpty()) {
                                adapter.setOffline(wearerId, true)
                            }
                            fetchUserStatus() // Keep battery/monitoring list in sync too
                        } else {
                            if (wearerId.isNotEmpty()) {
                                adapter.setOffline(wearerId, false)
                            }
                            if (data.has("battery")) {
                                adapter.updateBattery(wearerId, data.getInt("battery"))
                            }
                            if (data.has("bpm")) {
                                adapter.updateLiveBpm(wearerId, data.getInt("bpm"))
                            }
                        }
                    }
                }
            }

            mSocket.on("new_alert") { runOnUiThread { fetchUserStatus() } }

            mSocket.on("alert_resolved") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    runOnUiThread {
                        val wearerId = data.optString("wearerId")
                        val resolvedByName = data.optJSONObject("resolvedBy")?.optString("name")
                        // Clear the alert immediately so the card updates without waiting on
                        // the network round-trip, then resync to pick up everything else.
                        if (wearerId.isNotEmpty()) {
                            adapter.clearActiveAlert(wearerId)
                        }
                        if (!resolvedByName.isNullOrEmpty()) {
                            showSuccess("Alert resolved by $resolvedByName")
                        }
                        fetchUserStatus()
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("Socket", "Connection error: ${e.message}")
        }
    }

    private fun fetchUserStatus() {
        val currentUser = Firebase.auth.currentUser
        if (currentUser == null) {
            redirectToLogin()
            return
        }

        if (!swipeRefresh.isRefreshing) {
            loadingProgress.visibility = View.VISIBLE
        }

        currentUser.getIdToken(false).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val idToken = task.result.token ?: ""
                FirebaseMessaging.getInstance().token.addOnCompleteListener { fcmTask ->
                    val fcmToken = fcmTask.result ?: ""
                    lifecycleScope.launch {
                        try {
                            val response = RetrofitClient.instance.syncUser(AuthRequest(idToken, fcmToken))

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
                            } else if (response.code() == 401 || response.code() == 403) {
                                // Backend rejected the token outright - the session is dead.
                                redirectToLogin()
                            } else {
                                showError("Couldn't load your data (${response.code()}). Pull to refresh.")
                            }
                        } catch (e: Exception) {
                            loadingProgress.visibility = View.GONE
                            swipeRefresh.isRefreshing = false
                            Log.e("Dashboard", "Sync Error: ${e.message}")
                            showError("Network error. Pull to refresh.")
                        }
                    }
                }
            } else {
                loadingProgress.visibility = View.GONE
                swipeRefresh.isRefreshing = false
                val exception = task.exception
                if (exception is FirebaseAuthInvalidUserException || exception is FirebaseAuthInvalidCredentialsException) {
                    // Token refresh was explicitly rejected - the session expired or was revoked.
                    redirectToLogin()
                } else {
                    showError("Couldn't verify your session. Pull to refresh.")
                }
            }
        }
    }

    private fun redirectToLogin() {
        loadingProgress.visibility = View.GONE
        swipeRefresh.isRefreshing = false
        Firebase.auth.signOut()
        if (::mSocket.isInitialized) mSocket.disconnect()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
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
                showError("Rename failed")
            }
        }
    }

    private fun confirmResolution(item: MonitoredUser) {
        val alertId = item.activeAlert?._id ?: return
        MaterialAlertDialogBuilder(this, R.style.Theme_ResQcall_DarkDialog)
            .setTitle("Confirm Safety")
            .setMessage("Is the wearer safe now?")
            .setPositiveButton("Yes, Resolved") { _, _ -> resolveAlertOnServer(alertId) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resolveAlertOnServer(alertId: String) {
        val caregiverUid = Firebase.auth.currentUser?.uid ?: return
        loadingProgress.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.resolveAlert(ResolveRequest(alertId, caregiverUid))
                if (response.isSuccessful) {
                    showSuccess("Alert Resolved")
                    fetchUserStatus()
                } else {
                    loadingProgress.visibility = View.GONE
                }
            } catch (e: Exception) {
                loadingProgress.visibility = View.GONE
            }
        }
    }

    private fun requestAppPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        }

        // Android 14+ requires the user to explicitly allow full-screen intent notifications
        // per-app - without this, the emergency alert silently falls back to a normal
        // notification instead of launching EmergencyActivity over the lock screen/closed app.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            if (!notificationManager.canUseFullScreenIntent()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
                startActivity(intent)
            }
        }

        // Aggressive OEM battery optimization can kill the app before FCM wakes it to show the
        // emergency alert - request an exemption so fall alerts reliably arrive when closed.
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            startActivity(intent)
        }
    }

    private fun openGoogleMaps(lat: Double, lon: Double) {
        val gmmIntentUri = Uri.parse("google.navigation:q=$lat,$lon")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")
        startActivity(mapIntent)
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
                    showSuccess("Linked!")
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
            val dialogView = layoutInflater.inflate(R.layout.dialog_qr_code, null)
            dialogView.findViewById<ImageView>(R.id.qrImageView).setImageBitmap(bitmap)

            MaterialAlertDialogBuilder(this, R.style.Theme_ResQcall_DarkDialog)
                .setView(dialogView)
                .setPositiveButton("Done", null)
                .show()
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