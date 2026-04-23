package com.example.zhukang

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.zhukang.api.AuthApiService
import com.example.zhukang.model.RecommendRequest
import com.example.zhukang.model.RecommendResponse
import com.example.zhukang.model.RecommendedDish
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class RecommendActivity : AppCompatActivity() {

    // Views
    private lateinit var btnBack: ImageView
    private lateinit var foodGrid: GridLayout
    private lateinit var etManualInput: EditText
    private lateinit var btnGetRecommendation: android.widget.Button
    private lateinit var btnGetMockRecommendation: android.widget.Button
    private lateinit var progressBar: ProgressBar
    private lateinit var cardResult: CardView
    private lateinit var layoutMeals: LinearLayout

    // API
    private val authApiService by lazy { AuthApiService.create() }

    // Data
    private var currentUserId: String? = null
    private var selectedMealType: String? = null
    private val selectedFoods = mutableSetOf<String>()

    // 预设食物列表
    private val presetFoods = listOf(
        "牛肉", "鸡肉", "鱼虾", "鸡蛋",
        "豆腐", "西兰花", "番茄", "胡萝卜",
        "菠菜", "米饭", "面条", "馒头",
        "面包", "牛奶", "豆浆", "水果"
    )

    companion object {
        private const val TAG = "RecommendActivity"

        fun start(context: Context, userId: String, mealType: String) {
            val intent = Intent(context, RecommendActivity::class.java)
            intent.putExtra("user_id", userId)
            intent.putExtra("meal_type", mealType)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recommend)

        currentUserId = intent.getStringExtra("user_id")
        selectedMealType = intent.getStringExtra("meal_type")

        initViews()
        renderFoodGrid()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        foodGrid = findViewById(R.id.foodGrid)
        etManualInput = findViewById(R.id.etManualInput)
        btnGetRecommendation = findViewById(R.id.btnGetRecommendation)
        btnGetMockRecommendation = findViewById(R.id.btnGetMockRecommendation)
        progressBar = findViewById(R.id.progressBar)
        cardResult = findViewById(R.id.cardResult)
        layoutMeals = findViewById(R.id.layoutMeals)

        btnBack.setOnClickListener {
            finish()
        }

        btnGetRecommendation.setOnClickListener {
            getRecommendation(useMock = false)
        }

        btnGetMockRecommendation.setOnClickListener {
            getRecommendation(useMock = true)
        }
    }

    private fun renderFoodGrid() {
        foodGrid.removeAllViews()

        presetFoods.forEach { food ->
            val card = createFoodCard(food)
            foodGrid.addView(card)
        }
    }

    private fun createFoodCard(foodName: String): MaterialCardView {
        val isSelected = selectedFoods.contains(foodName)

        return MaterialCardView(this).apply {
            radius = dp(32).toFloat()
            strokeWidth = dp(2)
            strokeColor = ContextCompat.getColor(
                this@RecommendActivity,
                if (isSelected) R.color.zk_primary else R.color.zk_text_secondary
            )
            setCardBackgroundColor(
                ContextCompat.getColor(
                    this@RecommendActivity,
                    if (isSelected) R.color.zk_primary else R.color.zk_surface
                )
            )

            layoutParams = GridLayout.LayoutParams().apply {
                width = dp(76)
                height = dp(76)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }

            // 添加食物名称
            val textView = TextView(this@RecommendActivity).apply {
                text = foodName
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(
                    if (isSelected)
                        ContextCompat.getColor(this@RecommendActivity, R.color.zk_on_primary)
                    else
                        ContextCompat.getColor(this@RecommendActivity, R.color.zk_text_primary)
                )
            }
            addView(textView)

            // 点击切换选中状态
            setOnClickListener {
                toggleFoodSelection(foodName)
                renderFoodGrid()
            }
        }
    }

    private fun toggleFoodSelection(foodName: String) {
        if (selectedFoods.contains(foodName)) {
            selectedFoods.remove(foodName)
        } else {
            selectedFoods.add(foodName)
        }
    }

    private fun getRecommendation(useMock: Boolean = false) {
        val userId = currentUserId
        val mealType = selectedMealType

        if (userId.isNullOrBlank()) {
            Toast.makeText(this, "缺少用户信息", Toast.LENGTH_SHORT).show()
            return
        }

        if (mealType.isNullOrBlank()) {
            Toast.makeText(this, "缺少餐次信息", Toast.LENGTH_SHORT).show()
            return
        }

        // 获取手动输入的食物
        val manualInput = etManualInput.text.toString().trim().takeIf { it.isNotBlank() }

        // 显示加载状态
        showLoading()

        lifecycleScope.launch {
            try {
                val request = RecommendRequest(
                    userId = userId,
                    selectedFoods = selectedFoods.toList(),
                    manualInput = manualInput,
                    mealType = mealType
                )

                val response = withContext(Dispatchers.IO) {
                    if (useMock) {
                        authApiService.getRecommendationMock(request)
                    } else {
                        authApiService.getRecommendation(request)
                    }
                }

                hideLoading()

                if (response.isSuccessful && response.body() != null) {
                    showResult(response.body()!!)
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    Toast.makeText(
                        this@RecommendActivity,
                        "获取推荐失败: ${response.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                hideLoading()
                Toast.makeText(
                    this@RecommendActivity,
                    "网络错误: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
        cardResult.visibility = View.GONE
        btnGetRecommendation.isEnabled = false
    }

    private fun hideLoading() {
        progressBar.visibility = View.GONE
        btnGetRecommendation.isEnabled = true
    }

    private fun showResult(response: RecommendResponse) {
        // 显示推荐菜品
        layoutMeals.removeAllViews()
        response.recommendations.forEach { dish ->
            val dishView = createDishView(dish)
            layoutMeals.addView(dishView)
        }

        // 显示卡片
        cardResult.visibility = View.VISIBLE
    }

    private fun createDishView(dish: RecommendedDish): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))

            // 菜品名称
            val nameView = TextView(this@RecommendActivity).apply {
                text = dish.dishName
                textSize = 16f
                setTextColor(ContextCompat.getColor(this@RecommendActivity, R.color.zk_text_primary))
                setStyleBold(this)
            }
            addView(nameView)

            // 推荐理由
            val reasonView = TextView(this@RecommendActivity).apply {
                text = dish.reason
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@RecommendActivity, R.color.zk_text_secondary))
                setLineSpacing(dp(2).toFloat(), 1.0f)
            }
            addView(reasonView)

            // 热量
            val caloriesView = TextView(this@RecommendActivity).apply {
                text = "预估热量：${dish.estimatedCalories}"
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@RecommendActivity, R.color.zk_nutrient_calories))
            }
            addView(caloriesView)

            // 烹饪步骤
            if (dish.cookingSteps.isNotBlank()) {
                val stepsView = TextView(this@RecommendActivity).apply {
                    text = "做法：${dish.cookingSteps}"
                    textSize = 13f
                    setTextColor(ContextCompat.getColor(this@RecommendActivity, R.color.zk_text_secondary))
                    setLineSpacing(dp(2).toFloat(), 1.0f)
                    setPadding(0, dp(4), 0, 0)
                }
                addView(stepsView)
            }

            // 分隔线
            if (layoutMeals.childCount > 0) {
                val divider = View(this@RecommendActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1
                    ).apply {
                        setMargins(0, dp(8), 0, dp(8))
                    }
                    setBackgroundColor(ContextCompat.getColor(this@RecommendActivity, R.color.zk_divider))
                }
                addView(divider)
            }
        }
    }

    private fun setStyleBold(textView: TextView) {
        textView.paint.isFakeBoldText = true
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }
}
