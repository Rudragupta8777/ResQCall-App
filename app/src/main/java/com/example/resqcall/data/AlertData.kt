package com.example.resqcall.data

data class AlertData(
    val _id: String,
    val location: LocationData,
    val resolved: Boolean,
    val timestamp: String
)