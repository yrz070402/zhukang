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

data class LoginResponse(
    @SerializedName("user_id") val userId: String,
    @SerializedName("account") val account: String,
    @SerializedName("nickname") val nickname: String,
    @SerializedName("message") val message: String
)

data class RegisterRequest(
    @SerializedName("account") val account: String,
    @SerializedName("password") val password: String
)

data class RegisterResponse(
    @SerializedName("user_id") val userId: String,
    @SerializedName("account") val account: String,
    @SerializedName("nickname") val nickname: String,
    @SerializedName("message") val message: String
)

data class ProfileSetupRequest(
    @SerializedName("user_id") val userId: String,
    @SerializedName("age") val age: Int,
    @SerializedName("sex") val sex: String?,
    @SerializedName("height_cm") val heightCm: Float,
    @SerializedName("weight_kg") val weightKg: Float,
    @SerializedName("activity_level") val activityLevel: Int,
    @SerializedName("goal_index") val goalIndex: Int,
    @SerializedName("dietary_preferences") val dietaryPreferences: List<String>
)

data class ProfileSetupResponse(
    @SerializedName("user_id") val userId: String,
    @SerializedName("goal_type") val goalType: String,
    @SerializedName("tag_ids") val tagIds: List<Int>,
    @SerializedName("message") val message: String
)

data class PopularTagItem(
    @SerializedName("display_name") val displayName: String,
    @SerializedName("user_num") val userNum: Int
)

data class PopularTagsResponse(
    @SerializedName("items") val items: List<PopularTagItem>
)

data class UserDailyGoalTargetsResponse(
    @SerializedName("user_id") val userId: String,
    @SerializedName("target_daily_calories_kcal") val targetDailyCaloriesKcal: Float,
    @SerializedName("target_protein_g") val targetProteinG: Float,
    @SerializedName("target_fat_g") val targetFatG: Float,
    @SerializedName("target_carb_g") val targetCarbG: Float
)
