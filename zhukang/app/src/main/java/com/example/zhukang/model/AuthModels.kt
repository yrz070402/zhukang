package com.example.zhukang.model

import com.google.gson.annotations.SerializedName

/**
 * 认证相关数据模型（预留）
 * 后端接口完成后可直接复用。
 */
data class LoginRequest(
    @SerializedName("account") val account: String,
    @SerializedName("password") val password: String
)

data class RegisterRequest(
    @SerializedName("account") val account: String,
    @SerializedName("password") val password: String
)

data class AuthResponse(
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("token_type") val tokenType: String? = null,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("account") val account: String? = null
)
