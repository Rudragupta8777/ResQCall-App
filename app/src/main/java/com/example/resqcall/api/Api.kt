package com.example.resqcall.api

import com.example.resqcall.data.AddCaregiverRequest
import com.example.resqcall.data.AuthRequest
import com.example.resqcall.data.PairRequest
import com.example.resqcall.data.RenameRequest
import com.example.resqcall.data.ResolveRequest
import com.example.resqcall.data.RoleRequest
import com.example.resqcall.data.UserResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ResQcallApi {
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
}