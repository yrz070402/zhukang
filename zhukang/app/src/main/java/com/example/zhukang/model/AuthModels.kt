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

data class UserDailyIntakeSummaryResponse(
    @SerializedName("user_id") val userId: String,
    @SerializedName("start_at") val startAt: String,
    @SerializedName("end_at") val endAt: String,
    @SerializedName("total_calories_kcal") val totalCaloriesKcal: Float,
    @SerializedName("total_protein_g") val totalProteinG: Float,
    @SerializedName("total_fat_g") val totalFatG: Float,
    @SerializedName("total_carb_g") val totalCarbG: Float
)

data class ReportSeriesPoint(
    @SerializedName("date") val date: String,
    @SerializedName("calories_kcal") val caloriesKcal: Float?,
    @SerializedName("protein_g") val proteinG: Float?,
    @SerializedName("fat_g") val fatG: Float?,
    @SerializedName("carb_g") val carbG: Float?
)

data class ReportGoalLine(
    @SerializedName("calories_kcal") val caloriesKcal: Float,
    @SerializedName("protein_g") val proteinG: Float,
    @SerializedName("fat_g") val fatG: Float,
    @SerializedName("carb_g") val carbG: Float
)

data class UserReportResponse(
    @SerializedName("user_id") val userId: String,
    @SerializedName("start_at") val startAt: String,
    @SerializedName("end_at") val endAt: String,
    @SerializedName("points") val points: List<ReportSeriesPoint>,
    @SerializedName("goal_line") val goalLine: ReportGoalLine
)

data class UserTagInfo(
    @SerializedName("id") val id: Int,
    @SerializedName("display_name") val displayName: String
)

data class UserProfileDetailResponse(
    @SerializedName("user_id") val userId: String,
    @SerializedName("account") val account: String,
    @SerializedName("nickname") val nickname: String,
    @SerializedName("avatar_index") val avatarIndex: Int,
    @SerializedName("age") val age: Int,
    @SerializedName("sex") val sex: String,
    @SerializedName("height_cm") val heightCm: Float,
    @SerializedName("weight_kg") val weightKg: Float,
    @SerializedName("activity_level") val activityLevel: Int,
    @SerializedName("goal_type") val goalType: String,
    @SerializedName("goal_index") val goalIndex: Int,
    @SerializedName("target_daily_calories_kcal") val targetDailyCaloriesKcal: Float,
    @SerializedName("target_protein_g") val targetProteinG: Float,
    @SerializedName("target_fat_g") val targetFatG: Float,
    @SerializedName("target_carb_g") val targetCarbG: Float,
    @SerializedName("dietary_tags") val dietaryTags: List<UserTagInfo>
)

data class UserProfileUpdateRequest(
    @SerializedName("user_id") val userId: String,
    @SerializedName("nickname") val nickname: String,
    @SerializedName("avatar_index") val avatarIndex: Int,
    @SerializedName("age") val age: Int,
    @SerializedName("sex") val sex: String,
    @SerializedName("height_cm") val heightCm: Float,
    @SerializedName("weight_kg") val weightKg: Float,
    @SerializedName("activity_level") val activityLevel: Int,
    @SerializedName("goal_type") val goalType: String,
    @SerializedName("target_daily_calories_kcal") val targetDailyCaloriesKcal: Float? = null,
    @SerializedName("target_protein_g") val targetProteinG: Float? = null,
    @SerializedName("target_fat_g") val targetFatG: Float? = null,
    @SerializedName("target_carb_g") val targetCarbG: Float? = null,
)

data class UserTagsUpdateRequest(
    @SerializedName("user_id") val userId: String,
    @SerializedName("dietary_preferences") val dietaryPreferences: List<String>
)

data class UserTagsUpdateResponse(
    @SerializedName("user_id") val userId: String,
    @SerializedName("tag_ids") val tagIds: List<Int>,
    @SerializedName("tags") val tags: List<UserTagInfo>,
    @SerializedName("message") val message: String,
)

data class DietMapIntakeItem(
    @SerializedName("id") val id: String,
    @SerializedName("intake_time") val intakeTime: String,
    @SerializedName("meal_type") val mealType: String,
    @SerializedName("food_name") val foodName: String,
    @SerializedName("image_url") val imageUrl: String?,
    @SerializedName("calories_kcal") val caloriesKcal: Float,
    @SerializedName("protein_g") val proteinG: Float,
    @SerializedName("fat_g") val fatG: Float,
    @SerializedName("carb_g") val carbG: Float
)

data class DietMapDay(
    @SerializedName("business_day") val businessDay: String,
    @SerializedName("weekday") val weekday: Int,
    @SerializedName("intakes") val intakes: List<DietMapIntakeItem>
)

data class DietMapResponse(
    @SerializedName("user_id") val userId: String,
    @SerializedName("period") val period: String,
    @SerializedName("offset") val offset: Int,
    @SerializedName("start_at") val startAt: String,
    @SerializedName("end_at") val endAt: String,
    @SerializedName("days") val days: List<DietMapDay>
)

// ==================== 饮食推荐相关模型 ====================

data class RecommendRequest(
    @SerializedName("userId") val userId: String,
    @SerializedName("selectedFoods") val selectedFoods: List<String>,
    @SerializedName("manualInput") val manualInput: String?,
    @SerializedName("mealType") val mealType: String
)

data class NextMealTarget(
    @SerializedName("target_calories") val targetCalories: String,
    @SerializedName("focus_macros") val focusMacros: String
)

data class RecommendedDish(
    @SerializedName("dish_name") val dishName: String,
    @SerializedName("reason") val reason: String,
    @SerializedName("estimated_calories") val estimatedCalories: String,
    @SerializedName("cooking_steps") val cookingSteps: String
)

data class RecommendResponse(
    @SerializedName("analysis") val analysis: String,
    @SerializedName("next_meal_target") val nextMealTarget: NextMealTarget,
    @SerializedName("recommendations") val recommendations: List<RecommendedDish>
)
