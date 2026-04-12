package com.example.zhukang.model

import com.google.gson.annotations.SerializedName

/**
 * 食物分析响应数据模型
 */
data class FoodAnalysisResponse(
    @SerializedName("food_name")
    val foodName: String,

    @SerializedName("calories")
    val calories: Double,

    @SerializedName("protein")
    val protein: Double,

    @SerializedName("fat")
    val fat: Double,

    @SerializedName("carbs")
    val carbs: Double
) {
    override fun toString(): String {
        return """
            |食物名称: $foodName
            |热量: $calories 千卡
            |蛋白质: $protein 克
            |脂肪: $fat 克
            |碳水化合物: $carbs 克
        """.trimMargin()
    }
}
