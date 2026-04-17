package com.example.zhukang.api

import com.example.zhukang.model.LoginRequest
import com.example.zhukang.model.LoginResponse
import com.example.zhukang.model.PopularTagsResponse
import com.example.zhukang.model.ProfileSetupRequest
import com.example.zhukang.model.ProfileSetupResponse
import com.example.zhukang.model.RegisterRequest
import com.example.zhukang.model.RegisterResponse
import com.example.zhukang.model.UserDailyGoalTargetsResponse
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * 认证 API 服务接口（预留）
 * 当前后端尚未实现 auth 路由，以下接口先用于前端占位。
 */
interface AuthApiService {

    /**
     * 预留接口：用户登录
     * POST /api/v1/auth/login
     * Request: {"account": "...", "password": "..."}
     * Response(建议): {"access_token": "...", "token_type": "bearer", "user_id": "..."}
     */
    @POST("api/v1/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    /**
     * 预留接口：用户注册
     * POST /api/v1/auth/register
     * Request: {"account": "...", "password": "..."}
     * Response(建议): {"user_id": "...", "account": "..."}
     */
    @POST("api/v1/auth/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<RegisterResponse>

    @POST("api/v1/auth/register/profile")
    suspend fun setupProfile(
        @Body request: ProfileSetupRequest
    ): Response<ProfileSetupResponse>

    @GET("api/v1/auth/tags/popular")
    suspend fun getPopularTags(
        @Query("limit") limit: Int = 6
    ): Response<PopularTagsResponse>

    @GET("api/v1/auth/user/goal-targets")
    suspend fun getUserGoalTargets(
        @Query("user_id") userId: String
    ): Response<UserDailyGoalTargetsResponse>

    companion object {
        private const val BASE_URL = "http://10.0.2.2:8000/"

        fun create(): AuthApiService {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(60, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            return retrofit.create(AuthApiService::class.java)
        }
    }
}
