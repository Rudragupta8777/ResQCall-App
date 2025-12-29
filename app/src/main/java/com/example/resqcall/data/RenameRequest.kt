package com.example.resqcall.data

data class RenameRequest(
    val caregiverUid: String,
    val wearerId: String,
    val newNickname: String
)