package com.example.resqcall.data

data class PairRequest(
    val deviceId: String,
    val secretPin: String,
    val firebaseUid: String
)