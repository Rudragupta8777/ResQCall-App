package com.example.resqcall.data

data class MonitoredUser(
    val wearer: UserResponse,
    val nickname: String? = "",
    val activeAlert: AlertData? = null
)