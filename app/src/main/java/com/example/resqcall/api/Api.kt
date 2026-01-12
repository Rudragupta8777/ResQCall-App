package com.example.resqcall.api

import com.example.resqcall.data.*
import retrofit2.Response
import retrofit2.http.*

interface Api {
    @POST("api/auth/sync-user")
    suspend fun syncUser(@Body request: AuthRequest): Response<UserResponse>

    @POST("api/device/pair")
    suspend fun pairDevice(@Body request: PairRequest): Response<Unit>

    @POST("api/auth/update-role")
    suspend fun updateRole(@Body request: RoleRequest): Response<UserResponse>

    @POST("api/auth/add-caregiver")
    suspend fun addCaregiver(@Body request: AddCaregiverRequest): Response<Unit>

    @POST("api/emergency/resolve")
    suspend fun resolveAlert(@Body request: ResolveRequest): Response<Unit>

    @POST("api/auth/rename-wearer")
    suspend fun renameWearer(@Body request: RenameRequest): Response<Unit>

    @GET("api/emergency/history/{wearerId}")
    suspend fun getAlertHistory(@Path("wearerId") wearerId: String): Response<List<AlertData>>

    @HTTP(method = "DELETE", path = "api/auth/remove-wearer", hasBody = true)
    suspend fun removeWearer(@Body request: RemoveWearerRequest): Response<Unit>
}