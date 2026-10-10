package com.example.resqcall

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.resqcall.api.RetrofitClient
import com.example.resqcall.data.RoleRequest
import com.example.resqcall.util.showError
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class RoleSelectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_role_selection)

        findViewById<View>(R.id.cardWearer).setOnClickListener {
            showPhoneInputDialog()
        }

        findViewById<View>(R.id.cardCaregiver).setOnClickListener {
            updateRoleOnServer("caregiver", null)
        }
    }

    private fun showPhoneInputDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_phone_input, null)
        val etPhone = dialogView.findViewById<TextInputEditText>(R.id.etPhoneNumber)

        MaterialAlertDialogBuilder(this, R.style.Theme_ResQcall_DarkDialog)
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("Confirm") { _, _ ->
                val phone = etPhone.text.toString().trim()
                if (phone.length >= 10) {
                    updateRoleOnServer("wearer", phone)
                } else {
                    showError("Please enter a valid number")
                }
            }
            .setNegativeButton("Back", null)
            .show()
    }

    private fun updateRoleOnServer(role: String, phoneNumber: String?) {
        val uid = Firebase.auth.currentUser?.uid ?: return

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.updateRole(RoleRequest(role, uid, phoneNumber))
                if (response.isSuccessful) {
                    val nextIntent = if (role == "wearer") {
                        Intent(this@RoleSelectionActivity, PairActivity::class.java)
                    } else {
                        Intent(this@RoleSelectionActivity, DashboardActivity::class.java)
                    }
                    nextIntent.putExtra("USER_ROLE", role)
                    startActivity(nextIntent)
                    finish()
                } else {
                    showError("Update failed")
                }
            } catch (e: Exception) {
                showError("Error: ${e.message}")
            }
        }
    }
}