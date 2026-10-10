package com.example.resqcall.data

data class LocationPoint(
    val lat: Double,
    val lon: Double,
    val updatedAt: String? = null
)

data class LocationResponse(
    val location: LocationPoint,
    val deviceId: String
)

data class VitalStats(
    val minBpm: Int,
    val maxBpm: Int,
    val avgBpm: Int
)

data class CriticalEvent(
    val status: String,
    val bpm: Int,
    val timestamp: String
)

data class VitalReading(
    val bpm: Int,
    val timestamp: String
)

data class VitalsDay(
    val dateString: String,
    val stats: VitalStats,
    val criticalEvents: List<CriticalEvent> = emptyList(),
    val readings: List<VitalReading>? = null
)
