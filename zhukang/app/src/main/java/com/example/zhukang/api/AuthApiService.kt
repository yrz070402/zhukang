package com.example.zhukang.api

import com.example.zhukang.model.DietMapResponse
import com.example.zhukang.model.LoginRequest
import com.example.zhukang.model.LoginResponse
import com.example.zhukang.model.PopularTagsResponse
import com.example.zhukang.model.ProfileSetupRequest
import com.example.zhukang.model.ProfileSetupResponse
import com.example.zhukang.model.RegisterRequest
import com.example.zhukang.model.RegisterResponse
import com.example.zhukang.model.UserProfileDetailResponse
import com.example.zhukang.model.UserProfileUpdateRequest
import com.example.zhukang.model.UserReportResponse
import com.example.zhukang.model.UserDailyIntakeSummaryResponse
import com.example.zhukang.model.UserDailyGoalTargetsResponse
import com.example.zhukang.model.UserTagsUpdateRequest
import com.example.zhukang.model.UserTagsUpdateResponse
import com.example.zhukang.model.RecommendRequest
import com.example.zhukang.model.RecommendResponse
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
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

    @GET("api/v1/auth/user/intake-summary")
    suspend fun getUserDailyIntakeSummary(
        @Query("user_id") userId: String
    ): Response<UserDailyIntakeSummaryResponse>

    @GET("api/v1/auth/user/profile")
    suspend fun getUserProfile(
        @Query("user_id") userId: String
    ): Response<UserProfileDetailResponse>

    @PATCH("api/v1/auth/user/profile")
    suspend fun patchUserProfile(
        @Body request: UserProfileUpdateRequest
    ): Response<UserProfileDetailResponse>

    @PUT("api/v1/auth/user/tags")
    suspend fun putUserTags(
        @Body request: UserTagsUpdateRequest
    ): Response<UserTagsUpdateResponse>

    @GET("api/v1/report/daily")
    suspend fun getDailyReport(
        @Query("user_id") userId: String
    ): Response<UserReportResponse>

    @GET("api/v1/report/weekly")
    suspend fun getWeeklyReport(
        @Query("user_id") userId: String
    ): Response<UserReportResponse>

    @GET("api/v1/report/monthly")
    suspend fun getMonthlyReport(
        @Query("user_id") userId: String
    ): Response<UserReportResponse>

    @GET("api/v1/report/diet-map")
    suspend fun getDietMap(
        @Query("user_id") userId: String,
        @Query("period") period: String,
        @Query("offset") offset: Int = 0
    ): Response<DietMapResponse>

    // 饮食推荐
    @POST("api/v1/recommend/next-meal")
    suspend fun getRecommendation(
        @Body request: RecommendRequest
    ): Response<RecommendResponse>

    // 饮食推荐 Mock 接口（用于展示测试）
    @POST("api/v1/recommend/next-meal/mock")
    suspend fun getRecommendationMock(
        @Body request: RecommendRequest
    ): Response<RecommendResponse>

    companion object {
        private val BASE_URL: String = BackendUrls.BASE_URL

        fun create(): AuthApiService {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)      // LLM 调用需要更长时间
                .writeTimeout(60, TimeUnit.SECONDS)
                .callTimeout(180, TimeUnit.SECONDS)      // 整体超时 3 分钟
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
