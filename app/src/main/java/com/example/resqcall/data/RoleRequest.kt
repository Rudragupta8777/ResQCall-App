package com.example.resqcall.data

data class RoleRequest(
    val role: String,
    val firebaseUid: String,
    val phoneNumber: String? = null
)