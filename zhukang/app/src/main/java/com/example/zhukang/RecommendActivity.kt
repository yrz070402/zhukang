package com.example.zhukang

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
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
import androidx.core.widget.doAfterTextChanged
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

    private enum class BottomTab {
        HOME, RECOMMEND, REPORT, PROFILE
    }

    // Views
    private lateinit var btnBack: ImageView
    private lateinit var tvPageTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var tvFoodSectionTitle: TextView
    private lateinit var tvManualSectionTitle: TextView
    private lateinit var foodGrid: GridLayout
    private lateinit var etManualInput: EditText
    private lateinit var btnClearSelection: android.widget.Button
    private lateinit var btnGetRecommendation: android.widget.Button
    private lateinit var progressBar: ProgressBar
    private lateinit var cardResult: CardView
    private lateinit var layoutMeals: LinearLayout
    private lateinit var tvResultTitle: TextView
    private lateinit var tvMealsTitle: TextView
    private lateinit var tvAnalysis: TextView
    private lateinit var tvTarget: TextView
    private lateinit var tvSelectionSummary: TextView
    private lateinit var navHome: LinearLayout
    private lateinit var navRecommend: LinearLayout
    private lateinit var navReport: LinearLayout
    private lateinit var navProfile: LinearLayout
    private lateinit var navHomeIcon: ImageView
    private lateinit var navRecommendIcon: ImageView
    private lateinit var navReportIcon: ImageView
    private lateinit var navProfileIcon: ImageView
    private lateinit var navHomeLabel: TextView
    private lateinit var navRecommendLabel: TextView
    private lateinit var navReportLabel: TextView
    private lateinit var navProfileLabel: TextView

    // API
    private val authApiService by lazy { AuthApiService.create() }

    // Data
    private var currentUserId: String? = null
    private var selectedMealType: String? = null
    private var isEnglishUi = false
    private val selectedFoods = mutableSetOf<String>()

    // 预设食物列表
    private val presetFoods = listOf(
        "牛肉", "鸡肉", "鱼虾", "鸡蛋",
        "豆腐", "西兰花", "番茄", "胡萝卜",
        "菠菜", "米饭", "面条", "馒头",
        "面包", "牛奶", "豆浆", "水果"
    )
    private val foodEnglishLabels = mapOf(
        "牛肉" to "Beef",
        "鸡肉" to "Chicken",
        "鱼虾" to "Fish/Shrimp",
        "鸡蛋" to "Egg",
        "豆腐" to "Tofu",
        "西兰花" to "Broccoli",
        "番茄" to "Tomato",
        "胡萝卜" to "Carrot",
        "菠菜" to "Spinach",
        "米饭" to "Rice",
        "面条" to "Noodles",
        "馒头" to "Steamed Bun",
        "面包" to "Bread",
        "牛奶" to "Milk",
        "豆浆" to "Soy Milk",
        "水果" to "Fruit"
    )

    companion object {
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
        selectedMealType = intent.getStringExtra("meal_type") ?: SessionPrefs.getLastMealType(this)
        selectedMealType?.let { SessionPrefs.saveLastMealType(this, it) }
        isEnglishUi = SessionPrefs.isEnglishEnabled(this)

        initViews()
        applyLanguageTexts()
        renderFoodGrid()
        updateActionButtonsState()
    }

    override fun onResume() {
        super.onResume()
        val latestEnglishFlag = SessionPrefs.isEnglishEnabled(this)
        if (latestEnglishFlag != isEnglishUi) {
            isEnglishUi = latestEnglishFlag
            applyLanguageTexts()
            renderFoodGrid()
        }
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        tvPageTitle = findViewById(R.id.tvPageTitle)
        tvSubtitle = findViewById(R.id.tvSubtitle)
        tvFoodSectionTitle = findViewById(R.id.tvFoodSectionTitle)
        tvManualSectionTitle = findViewById(R.id.tvManualSectionTitle)
        foodGrid = findViewById(R.id.foodGrid)
        etManualInput = findViewById(R.id.etManualInput)
        btnClearSelection = findViewById(R.id.btnClearSelection)
        btnGetRecommendation = findViewById(R.id.btnGetRecommendation)
        progressBar = findViewById(R.id.progressBar)
        cardResult = findViewById(R.id.cardResult)
        layoutMeals = findViewById(R.id.layoutMeals)
        tvResultTitle = findViewById(R.id.tvResultTitle)
        tvMealsTitle = findViewById(R.id.tvMealsTitle)
        tvAnalysis = findViewById(R.id.tvAnalysis)
        tvTarget = findViewById(R.id.tvTarget)
        tvSelectionSummary = findViewById(R.id.tvSelectionSummary)
        navHome = findViewById(R.id.navHome)
        navRecommend = findViewById(R.id.navRecommend)
        navReport = findViewById(R.id.navReport)
        navProfile = findViewById(R.id.navProfile)
        navHomeIcon = findViewById(R.id.navHomeIcon)
        navRecommendIcon = findViewById(R.id.navRecommendIcon)
        navReportIcon = findViewById(R.id.navReportIcon)
        navProfileIcon = findViewById(R.id.navProfileIcon)
        navHomeLabel = findViewById(R.id.navHomeLabel)
        navRecommendLabel = findViewById(R.id.navRecommendLabel)
        navReportLabel = findViewById(R.id.navReportLabel)
        navProfileLabel = findViewById(R.id.navProfileLabel)

        btnBack.setOnClickListener {
            finish()
        }

        btnGetRecommendation.setOnClickListener {
            getRecommendation()
        }

        btnClearSelection.setOnClickListener {
            clearSelection()
        }

        etManualInput.doAfterTextChanged {
            cardResult.visibility = View.GONE
            updateSelectionSummary()
            updateActionButtonsState()
        }

        setupBottomNav()
        setBottomNavSelection(BottomTab.RECOMMEND)
    }

    private fun setupBottomNav() {
        navHome.setOnClickListener {
            setBottomNavSelection(BottomTab.HOME)
            navigateToMain()
        }
        navRecommend.setOnClickListener {
            setBottomNavSelection(BottomTab.RECOMMEND)
        }
        navReport.setOnClickListener {
            setBottomNavSelection(BottomTab.REPORT)
            navigateToReport()
        }
        navProfile.setOnClickListener {
            setBottomNavSelection(BottomTab.PROFILE)
            navigateToProfile()
        }
    }

    private fun setBottomNavSelection(tab: BottomTab) {
        applyBottomNavState(navHome, navHomeIcon, navHomeLabel, tab == BottomTab.HOME)
        applyBottomNavState(navRecommend, navRecommendIcon, navRecommendLabel, tab == BottomTab.RECOMMEND)
        applyBottomNavState(navReport, navReportIcon, navReportLabel, tab == BottomTab.REPORT)
        applyBottomNavState(navProfile, navProfileIcon, navProfileLabel, tab == BottomTab.PROFILE)
    }

    private fun applyBottomNavState(
        container: LinearLayout,
        icon: ImageView,
        label: TextView,
        selected: Boolean
    ) {
        if (selected) {
            container.setBackgroundResource(R.drawable.bg_bottom_nav_item_selected)
        } else {
            container.background = null
        }
        val color = ContextCompat.getColor(
            this,
            if (selected) R.color.zk_primary else R.color.zk_text_secondary
        )
        icon.imageTintList = ColorStateList.valueOf(color)
        label.setTextColor(color)
    }

    private fun navigateToMain() {
        val userId = currentUserId
        if (userId.isNullOrBlank()) {
            toastMissingUser()
            return
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("user_id", userId)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    private fun navigateToReport() {
        val userId = currentUserId
        if (userId.isNullOrBlank()) {
            toastMissingUser()
            return
        }
        val intent = Intent(this, ReportActivity::class.java).apply {
            putExtra("user_id", userId)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    private fun navigateToProfile() {
        val userId = currentUserId
        if (userId.isNullOrBlank()) {
            toastMissingUser()
            return
        }
        val intent = Intent(this, PersonalProfileActivity::class.java).apply {
            putExtra("user_id", userId)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    private fun renderFoodGrid() {
        foodGrid.removeAllViews()

        presetFoods.forEach { food ->
            val card = createFoodCard(food)
            foodGrid.addView(card)
        }
        updateSelectionSummary()
        updateActionButtonsState()
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
                text = foodDisplayName(foodName)
                textSize = 12f
                gravity = Gravity.CENTER
                maxLines = 2
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
                cardResult.visibility = View.GONE
                renderFoodGrid()
                updateActionButtonsState()
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

    private fun getRecommendation() {
        val userId = currentUserId
        val mealType = selectedMealType

        if (userId.isNullOrBlank()) {
            toastMissingUser()
            return
        }

        if (mealType.isNullOrBlank()) {
            toast(t("缺少餐次信息", "Missing meal type."))
            return
        }

        // 获取手动输入的食物
        val manualInput = etManualInput.text.toString().trim().takeIf { it.isNotBlank() }
        if (selectedFoods.isEmpty() && manualInput.isNullOrBlank()) {
            toast(t("请至少选择或输入一种食物", "Please select or enter at least one food item."))
            return
        }

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
                    authApiService.getRecommendation(request)
                }

                hideLoading()

                if (response.isSuccessful && response.body() != null) {
                    showResult(response.body()!!)
                } else {
                    Toast.makeText(
                        this@RecommendActivity,
                        t("获取推荐失败", "Failed to get recommendation") + ": ${response.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                hideLoading()
                Toast.makeText(
                    this@RecommendActivity,
                    t("网络错误", "Network error") + ": ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
        cardResult.visibility = View.GONE
        btnGetRecommendation.isEnabled = false
        btnClearSelection.isEnabled = false
    }

    private fun hideLoading() {
        progressBar.visibility = View.GONE
        updateActionButtonsState()
        btnClearSelection.isEnabled = true
    }

    private fun showResult(response: RecommendResponse) {
        tvAnalysis.text = response.analysis.ifBlank { t("暂无分析结论", "No analysis available.") }
        val target = response.nextMealTarget
        tvTarget.text = t("热量目标", "Calorie target") + ": " +
            target.targetCalories.ifBlank { t("未提供", "Not provided") } + "\n" +
            t("重点营养", "Focus macros") + ": " +
            target.focusMacros.ifBlank { t("未提供", "Not provided") }

        // 显示推荐菜品
        layoutMeals.removeAllViews()
        if (response.recommendations.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = t(
                    "当前没有可用推荐，请调整食材后重试。",
                    "No recommendation is available. Please adjust your foods and try again."
                )
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@RecommendActivity, R.color.zk_text_secondary))
                setPadding(0, dp(8), 0, dp(4))
            }
            layoutMeals.addView(emptyView)
        } else {
            response.recommendations.forEach { dish ->
                val dishView = createDishView(dish)
                layoutMeals.addView(dishView)
            }
        }

        // 显示卡片
        cardResult.visibility = View.VISIBLE
    }

    private fun updateSelectionSummary() {
        val manualFoods = parseManualFoods()
        val selectedPart = if (selectedFoods.isEmpty()) {
            t("未选择", "None")
        } else {
            selectedFoods.map { foodDisplayName(it) }.joinToString(if (isEnglishUi) ", " else "、")
        }
        val manualPart = if (manualFoods.isEmpty()) {
            t("无", "None")
        } else {
            manualFoods.joinToString(if (isEnglishUi) ", " else "、")
        }
        tvSelectionSummary.text = t("已选食材", "Selected") + ": $selectedPart\n" +
            t("手动补充", "Manual Input") + ": $manualPart"
    }

    private fun parseManualFoods(): List<String> {
        return etManualInput.text.toString()
            .split(",", "，", ";", "；", "\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun clearSelection() {
        selectedFoods.clear()
        etManualInput.text?.clear()
        cardResult.visibility = View.GONE
        renderFoodGrid()
        updateActionButtonsState()
        toast(t("已清空已选食材", "Selected foods cleared."))
    }

    private fun createDishView(dish: RecommendedDish): LinearLayout {
        return LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))

            // 菜品名称
            val nameView = TextView(this@RecommendActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = dish.dishName
                textSize = 16f
                setTextColor(ContextCompat.getColor(this@RecommendActivity, R.color.zk_text_primary))
                setStyleBold(this)
            }
            addView(nameView)

            // 推荐理由
            val reasonView = TextView(this@RecommendActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = dish.reason
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@RecommendActivity, R.color.zk_text_secondary))
                setLineSpacing(dp(2).toFloat(), 1.0f)
            }
            addView(reasonView)

            // 热量
            val caloriesView = TextView(this@RecommendActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = t("预估热量", "Estimated calories") + ": ${dish.estimatedCalories}"
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@RecommendActivity, R.color.zk_nutrient_calories))
            }
            addView(caloriesView)

            // 烹饪步骤
            if (dish.cookingSteps.isNotBlank()) {
                val stepsView = TextView(this@RecommendActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    text = t("做法", "Steps") + ": ${dish.cookingSteps}"
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

    private fun applyLanguageTexts() {
        btnBack.contentDescription = t("返回", "Back")
        tvPageTitle.text = t("智能推荐", "Smart Recommendation")
        tvSubtitle.text = t("按食材生成下一餐建议", "Build next-meal suggestions from your ingredients")
        tvFoodSectionTitle.text = t("常见食物", "Common Foods")
        tvManualSectionTitle.text = t("其他食物（可选）", "Other Foods (Optional)")
        etManualInput.hint = t("输入其他食材，多个用逗号分隔", "Enter other foods, separated by commas")
        btnClearSelection.text = t("清空已选食材", "Clear Selected Foods")
        btnGetRecommendation.text = t("获取推荐", "Get Recommendation")
        tvResultTitle.text = t("推荐分析", "Recommendation Analysis")
        tvMealsTitle.text = t("推荐菜品", "Recommended Dishes")

        navHomeLabel.text = t("首页", "Home")
        navRecommendLabel.text = t("推荐", "Suggest")
        navReportLabel.text = t("报表", "Report")
        navProfileLabel.text = t("我的", "Me")

        updateSelectionSummary()
    }

    private fun hasFoodInput(): Boolean {
        return selectedFoods.isNotEmpty() || parseManualFoods().isNotEmpty()
    }

    private fun updateActionButtonsState() {
        val canRequest = !selectedMealType.isNullOrBlank() && hasFoodInput()
        btnGetRecommendation.isEnabled = canRequest
        btnGetRecommendation.alpha = if (canRequest) 1f else 0.55f
    }

    private fun foodDisplayName(foodName: String): String {
        return if (isEnglishUi) foodEnglishLabels[foodName] ?: foodName else foodName
    }

    private fun toastMissingUser() {
        toast(t("缺少用户信息", "Missing user information."))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun t(zh: String, en: String): String = if (isEnglishUi) en else zh

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

}
