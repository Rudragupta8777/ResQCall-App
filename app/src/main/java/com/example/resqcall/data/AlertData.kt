package com.example.resqcall.data

data class AlertData(
    val _id: String,
    val location: LocationData,
    val resolved: Boolean,
    val timestamp: String,
    val resolvedBy: UserResponse? = null // Added this to match backend population
)