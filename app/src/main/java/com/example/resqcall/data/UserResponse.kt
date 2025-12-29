package com.example.resqcall.data

data class UserResponse(
    val _id: String,
    val firebaseUid: String,
    val email: String,
    val name: String,
    val role: String? = null,
    val myWearable: DeviceDetails? = null,
    val monitoring: List<MonitoredUser>? = null, // Changed from List<UserResponse>
    val fcmToken: String? = null
)