package com.example.zhukang

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.zhukang.api.AuthApiService
import com.example.zhukang.api.FoodApiService
import coil.load
import com.example.zhukang.api.BackendUrls
import com.example.zhukang.model.FoodAnalysisResponse
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private enum class ProgressDisplayMode {
        PERCENTAGE,
        ABSOLUTE
    }

    private enum class IntakeLevel {
        LOW_OR_EXCESSIVE,
        CAUTION,
        IDEAL
    }

    private enum class MealType {
        BREAKFAST, LUNCH, DINNER, SNACK
    }

    private enum class BottomTab {
        HOME, RECOMMEND, REPORT, PROFILE
    }

    // Views
    private lateinit var btnBreakfast: Button
    private lateinit var btnLunch: Button
    private lateinit var btnDinner: Button
    private lateinit var btnSnack: Button
    private lateinit var btnLanguageToggle: Button
    private lateinit var btnTakePhoto: Button
    private lateinit var btnPickImage: Button
    private lateinit var btnRecommend: Button
    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var tvQuickActionsTitle: TextView
    private lateinit var ivFoodImage: ImageView
    private lateinit var cardImage: CardView
    private lateinit var cardResult: CardView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvFoodName: TextView
    private lateinit var tvCaloriesLabel: TextView
    private lateinit var tvCaloriesUnit: TextView
    private lateinit var tvCalories: TextView
    private lateinit var tvProteinLabel: TextView
    private lateinit var tvProteinUnit: TextView
    private lateinit var tvProtein: TextView
    private lateinit var tvFatLabel: TextView
    private lateinit var tvFatUnit: TextView
    private lateinit var tvFat: TextView
    private lateinit var tvCarbsLabel: TextView
    private lateinit var tvCarbsUnit: TextView
    private lateinit var tvCarbs: TextView
    private lateinit var cardDayProgress: CardView
    private lateinit var tvDayProgressTitle: TextView
    private lateinit var tvDayCaloriesLabel: TextView
    private lateinit var tvDayProteinLabel: TextView
    private lateinit var tvDayFatLabel: TextView
    private lateinit var tvDayCarbsLabel: TextView
    private lateinit var tvProgressModeHint: TextView
    private lateinit var tvDayCaloriesValue: TextView
    private lateinit var tvDayProteinValue: TextView
    private lateinit var tvDayFatValue: TextView
    private lateinit var tvDayCarbsValue: TextView
    private lateinit var pbDayCalories: ProgressBar
    private lateinit var pbDayProtein: ProgressBar
    private lateinit var pbDayFat: ProgressBar
    private lateinit var pbDayCarbs: ProgressBar
    private lateinit var ivDayCaloriesWarning: ImageView
    private lateinit var ivDayProteinWarning: ImageView
    private lateinit var ivDayFatWarning: ImageView
    private lateinit var ivDayCarbsWarning: ImageView
    private lateinit var tvError: TextView
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

    // API Service
    private lateinit var foodApiService: FoodApiService
    private val authApiService by lazy { AuthApiService.create() }

    // 当前拍照的图片 URI
    private var currentPhotoUri: Uri? = null
    private var currentPhotoPath: String? = null
    private var progressDisplayMode = ProgressDisplayMode.PERCENTAGE

    // 默认值仅在首次加载失败时兜底，成功后会替换为后端用户目标。
    private var dailyGoalCalories = 1800f
    private var dailyGoalProtein = 60f
    private var dailyGoalFat = 50f
    private var dailyGoalCarbs = 250f

    // 当前进度数值用于点击切换显示模式，后续可直接替换为后端返回的真实数据。
    private var currentCalories = 0f
    private var currentProtein = 0f
    private var currentFat = 0f
    private var currentCarbs = 0f
    private var currentUserId: String? = null
    private var isAnalyzing = false
    private var isEnglishUi = false
    private var lastDetectedFoodNameRaw: String? = null

    // 当前选中的餐次（默认午餐）
    private var selectedMealType: MealType? = null

    companion object {
        private const val TAG = "MainActivity"
        private const val CAMERA_PERMISSION = Manifest.permission.CAMERA
    }

    // 相机权限请求
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            showError(t("需要相机权限才能拍照", "Camera permission is required."))
        }
    }

    // 拍照结果处理
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = currentPhotoUri
        if (success && uri != null) {
            Log.d(TAG, "拍照成功: $uri")
            displayAndAnalyzeImage(uri)
        } else {
            showError(t("拍照取消或失败", "Camera capture was canceled or failed."))
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            displayAndAnalyzeImage(uri)
        } else {
            Toast.makeText(this, t("未选择图片", "No image selected."), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val contentRoot = findViewById<View>(R.id.main)
        val initialPaddingLeft = contentRoot.paddingLeft
        val initialPaddingTop = contentRoot.paddingTop
        val initialPaddingRight = contentRoot.paddingRight
        val initialPaddingBottom = contentRoot.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(contentRoot) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                initialPaddingLeft + systemBars.left,
                initialPaddingTop + systemBars.top,
                initialPaddingRight + systemBars.right,
                initialPaddingBottom + systemBars.bottom
            )
            insets
        }

        // 初始化 Views
        initViews()
        isEnglishUi = SessionPrefs.isEnglishEnabled(this)
        applyLanguageTexts()

        // 初始化 API 服务
        foodApiService = FoodApiService.create()
        currentUserId = intent.getStringExtra("user_id")
        if (currentUserId.isNullOrBlank()) {
            Toast.makeText(
                this,
                t("登录状态已失效，请重新登录", "Login expired. Please sign in again."),
                Toast.LENGTH_SHORT
            ).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // 设置按钮点击事件
        btnTakePhoto.setOnClickListener {
            checkCameraPermissionAndOpen()
        }

        btnPickImage.setOnClickListener {
            openGalleryPicker()
        }
        btnLanguageToggle.setOnClickListener {
            isEnglishUi = !isEnglishUi
            SessionPrefs.saveEnglishEnabled(this, isEnglishUi)
            applyLanguageTexts()
        }

        cardDayProgress.setOnClickListener {
            progressDisplayMode = if (progressDisplayMode == ProgressDisplayMode.PERCENTAGE) {
                ProgressDisplayMode.ABSOLUTE
            } else {
                ProgressDisplayMode.PERCENTAGE
            }
            renderProgressValues(animate = true)
        }

        // 餐次选择按钮点击事件
        btnBreakfast.setOnClickListener { selectMealType(MealType.BREAKFAST) }
        btnLunch.setOnClickListener { selectMealType(MealType.LUNCH) }
        btnDinner.setOnClickListener { selectMealType(MealType.DINNER) }
        btnSnack.setOnClickListener { selectMealType(MealType.SNACK) }

        // 智能推荐按钮
        btnRecommend.setOnClickListener { openRecommendScreen() }

        // 记住用户上次餐次，降低重复操作。
        selectMealType(loadLastMealType())
        setupBottomNav()
        setBottomNavSelection(BottomTab.HOME)
        loadUserGoalTargets()
        loadUserDailyIntakeSummary()
    }

    override fun onResume() {
        super.onResume()
        loadUserDailyIntakeSummary()
    }

    private fun loadUserGoalTargets() {
        val userId = currentUserId
        if (userId.isNullOrBlank()) {
            Toast.makeText(
                this,
                t("未检测到用户信息，使用默认目标", "User not found. Using default goals."),
                Toast.LENGTH_SHORT
            ).show()
            renderProgressValues()
            return
        }

        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                runCatching { authApiService.getUserGoalTargets(userId) }.getOrNull()
            }

            if (response?.isSuccessful != true || response.body() == null) {
                Toast.makeText(
                    this@MainActivity,
                    t("加载目标进度失败，使用默认目标", "Failed to load goals. Using defaults."),
                    Toast.LENGTH_SHORT
                ).show()
                renderProgressValues()
                return@launch
            }

            val body = response.body()!!
            dailyGoalCalories = body.targetDailyCaloriesKcal.coerceAtLeast(0f)
            dailyGoalProtein = body.targetProteinG.coerceAtLeast(0f)
            dailyGoalFat = body.targetFatG.coerceAtLeast(0f)
            dailyGoalCarbs = body.targetCarbG.coerceAtLeast(0f)

            renderProgressValues()
        }
    }

    private fun loadUserDailyIntakeSummary() {
        val userId = currentUserId
        if (userId.isNullOrBlank()) {
            return
        }

        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                runCatching { authApiService.getUserDailyIntakeSummary(userId) }.getOrNull()
            }

            if (response?.isSuccessful != true || response.body() == null) {
                return@launch
            }

            val body = response.body()!!
            currentCalories = body.totalCaloriesKcal.coerceAtLeast(0f)
            currentProtein = body.totalProteinG.coerceAtLeast(0f)
            currentFat = body.totalFatG.coerceAtLeast(0f)
            currentCarbs = body.totalCarbG.coerceAtLeast(0f)

            renderProgressValues()
            cardDayProgress.visibility = android.view.View.VISIBLE
        }
    }

    private fun initViews() {
        // 餐次选择按钮
        btnBreakfast = findViewById(R.id.btnBreakfast)
        btnLunch = findViewById(R.id.btnLunch)
        btnDinner = findViewById(R.id.btnDinner)
        btnSnack = findViewById(R.id.btnSnack)
        btnLanguageToggle = findViewById(R.id.btnLanguageToggle)

        btnTakePhoto = findViewById(R.id.btnTakePhoto)
        btnPickImage = findViewById(R.id.btnPickImage)
        btnRecommend = findViewById(R.id.btnRecommend)
        tvTitle = findViewById(R.id.tvTitle)
        tvSubtitle = findViewById(R.id.tvSubtitle)
        tvQuickActionsTitle = findViewById(R.id.tvQuickActionsTitle)
        ivFoodImage = findViewById(R.id.ivFoodImage)
        cardImage = findViewById(R.id.cardImage)
        cardResult = findViewById(R.id.cardResult)
        progressBar = findViewById(R.id.progressBar)
        tvFoodName = findViewById(R.id.tvFoodName)
        tvCaloriesLabel = findViewById(R.id.tvCaloriesLabel)
        tvCaloriesUnit = findViewById(R.id.tvCaloriesUnit)
        tvCalories = findViewById(R.id.tvCalories)
        tvProteinLabel = findViewById(R.id.tvProteinLabel)
        tvProteinUnit = findViewById(R.id.tvProteinUnit)
        tvProtein = findViewById(R.id.tvProtein)
        tvFatLabel = findViewById(R.id.tvFatLabel)
        tvFatUnit = findViewById(R.id.tvFatUnit)
        tvFat = findViewById(R.id.tvFat)
        tvCarbsLabel = findViewById(R.id.tvCarbsLabel)
        tvCarbsUnit = findViewById(R.id.tvCarbsUnit)
        tvCarbs = findViewById(R.id.tvCarbs)
        cardDayProgress = findViewById(R.id.cardDayProgress)
        tvDayProgressTitle = findViewById(R.id.tvDayProgressTitle)
        tvDayCaloriesLabel = findViewById(R.id.tvDayCaloriesLabel)
        tvDayProteinLabel = findViewById(R.id.tvDayProteinLabel)
        tvDayFatLabel = findViewById(R.id.tvDayFatLabel)
        tvDayCarbsLabel = findViewById(R.id.tvDayCarbsLabel)
        tvProgressModeHint = findViewById(R.id.tvProgressModeHint)
        tvDayCaloriesValue = findViewById(R.id.tvDayCaloriesValue)
        tvDayProteinValue = findViewById(R.id.tvDayProteinValue)
        tvDayFatValue = findViewById(R.id.tvDayFatValue)
        tvDayCarbsValue = findViewById(R.id.tvDayCarbsValue)
        pbDayCalories = findViewById(R.id.pbDayCalories)
        pbDayProtein = findViewById(R.id.pbDayProtein)
        pbDayFat = findViewById(R.id.pbDayFat)
        pbDayCarbs = findViewById(R.id.pbDayCarbs)
        ivDayCaloriesWarning = findViewById(R.id.ivDayCaloriesWarning)
        ivDayProteinWarning = findViewById(R.id.ivDayProteinWarning)
        ivDayFatWarning = findViewById(R.id.ivDayFatWarning)
        ivDayCarbsWarning = findViewById(R.id.ivDayCarbsWarning)
        tvError = findViewById(R.id.tvError)
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
    }

    private fun setupBottomNav() {
        navHome.setOnClickListener {
            setBottomNavSelection(BottomTab.HOME)
        }
        navRecommend.setOnClickListener {
            setBottomNavSelection(BottomTab.RECOMMEND)
            openRecommendScreen()
        }
        navReport.setOnClickListener {
            setBottomNavSelection(BottomTab.REPORT)
            openReportScreen()
        }
        navProfile.setOnClickListener {
            setBottomNavSelection(BottomTab.PROFILE)
            openPersonalProfileScreen()
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

    private fun t(zh: String, en: String): String = if (isEnglishUi) en else zh

    private fun applyLanguageTexts() {
        tvTitle.text = "筑康NutriNest"
        btnLanguageToggle.text = if (isEnglishUi) "中文" else "EN"

        tvSubtitle.text = t(
            "选择餐次后拍照，自动更新今日累计营养",
            "Choose meal type, then scan food to update today's nutrition."
        )
        btnBreakfast.text = t("早餐", "Breakfast")
        btnLunch.text = t("午餐", "Lunch")
        btnDinner.text = t("晚餐", "Dinner")
        btnSnack.text = t("加餐", "Snack")

        tvQuickActionsTitle.text = t("快捷操作", "Quick Actions")
        btnTakePhoto.text = t("拍照识别", "Scan with Camera")
        btnPickImage.text = t("相册导入", "Import Gallery")
        btnRecommend.text = t("下一餐智能推荐", "Smart Next-Meal Plan")
        ivFoodImage.contentDescription = t("食物图片", "Food image")

        if (lastDetectedFoodNameRaw.isNullOrBlank()) {
            if (tvFoodName.text.isBlank() || tvFoodName.text == "食物名称" || tvFoodName.text == "Food name") {
                tvFoodName.text = t("食物名称", "Food name")
            }
        } else {
            tvFoodName.text = cleanFoodName(lastDetectedFoodNameRaw!!)
        }
        tvCaloriesLabel.text = t("热量", "Calories")
        tvCaloriesUnit.text = if (isEnglishUi) "kcal" else "千卡"
        tvProteinLabel.text = t("蛋白质", "Protein")
        tvProteinUnit.text = if (isEnglishUi) "g" else "克"
        tvFatLabel.text = t("脂肪", "Fat")
        tvFatUnit.text = if (isEnglishUi) "g" else "克"
        tvCarbsLabel.text = t("碳水", "Carbs")
        tvCarbsUnit.text = if (isEnglishUi) "g" else "克"

        tvDayProgressTitle.text = t("今日累计进度", "Today's Progress")
        tvDayCaloriesLabel.text = t("热量", "Calories")
        tvDayProteinLabel.text = t("蛋白质", "Protein")
        tvDayFatLabel.text = t("脂肪", "Fat")
        tvDayCarbsLabel.text = t("碳水", "Carbs")
        val warningDesc = t("超量警示", "Over-limit warning")
        ivDayCaloriesWarning.contentDescription = warningDesc
        ivDayProteinWarning.contentDescription = warningDesc
        ivDayFatWarning.contentDescription = warningDesc
        ivDayCarbsWarning.contentDescription = warningDesc

        navHomeLabel.text = t("首页", "Home")
        navRecommendLabel.text = t("推荐", "Suggest")
        navReportLabel.text = t("报表", "Report")
        navProfileLabel.text = t("我的", "Me")

        renderProgressValues()
    }

    /**
     * 选择餐次并更新按钮状态
     */
    private fun selectMealType(type: MealType) {
        selectedMealType = type
        SessionPrefs.saveLastMealType(this, type.name)
        updateMealTypeButtonStyles()
    }

    private fun loadLastMealType(): MealType {
        return runCatching {
            MealType.valueOf(SessionPrefs.getLastMealType(this))
        }.getOrDefault(MealType.LUNCH)
    }

    /**
     * 更新餐次按钮样式（选中态高亮）
     */
    private fun updateMealTypeButtonStyles() {
        val unselectedColor = ContextCompat.getColor(this, R.color.zk_text_secondary)

        btnBreakfast.apply {
            if (selectedMealType == MealType.BREAKFAST) {
                setBackgroundResource(R.drawable.bg_meal_chip_selected)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.zk_on_primary))
            } else {
                setBackgroundResource(R.drawable.bg_meal_chip_unselected)
                setTextColor(unselectedColor)
            }
        }

        btnLunch.apply {
            if (selectedMealType == MealType.LUNCH) {
                setBackgroundResource(R.drawable.bg_meal_chip_selected)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.zk_on_primary))
            } else {
                setBackgroundResource(R.drawable.bg_meal_chip_unselected)
                setTextColor(unselectedColor)
            }
        }

        btnDinner.apply {
            if (selectedMealType == MealType.DINNER) {
                setBackgroundResource(R.drawable.bg_meal_chip_selected)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.zk_on_primary))
            } else {
                setBackgroundResource(R.drawable.bg_meal_chip_unselected)
                setTextColor(unselectedColor)
            }
        }

        btnSnack.apply {
            if (selectedMealType == MealType.SNACK) {
                setBackgroundResource(R.drawable.bg_meal_chip_selected)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.zk_on_primary))
            } else {
                setBackgroundResource(R.drawable.bg_meal_chip_unselected)
                setTextColor(unselectedColor)
            }
        }
    }

    /**
     * 检查相机权限并打开相机
     */
    private fun checkCameraPermissionAndOpen() {
        if (selectedMealType == null) {
            Toast.makeText(this, t("请先选择餐次", "Please select a meal type first."), Toast.LENGTH_SHORT).show()
            return
        }
        if (ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION)
            == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            cameraPermissionLauncher.launch(CAMERA_PERMISSION)
        }
    }

    private fun openGalleryPicker() {
        if (selectedMealType == null) {
            Toast.makeText(this, t("请先选择餐次", "Please select a meal type first."), Toast.LENGTH_SHORT).show()
            return
        }
        pickImageLauncher.launch("image/*")
    }

    /**
     * 打开相机拍照
     */
    private fun openCamera() {
        try {
            // 创建图片文件
            val photoFile = createImageFile()
            currentPhotoPath = photoFile.absolutePath

            // 获取 FileProvider URI
            val photoUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                photoFile
            )
            currentPhotoUri = photoUri

            // 启动相机
            takePictureLauncher.launch(photoUri)

        } catch (e: Exception) {
            Log.e(TAG, "打开相机失败", e)
            showError("${t("打开相机失败", "Failed to open camera")}: ${e.message}")
        }
    }

    /**
     * 创建图片文件
     */
    private fun createImageFile(): File {
        // 使用应用私有目录
        val imagesDir = File(filesDir, "images")
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }

        val fileName = "food_${System.currentTimeMillis()}.jpg"
        return File(imagesDir, fileName)
    }

    /**
     * 显示图片并开始分析
     */
    private fun displayAndAnalyzeImage(uri: Uri) {
        try {
            // 显示图片
            ivFoodImage.setImageURI(uri)
            cardImage.visibility = android.view.View.VISIBLE
            cardResult.visibility = android.view.View.GONE
            tvError.visibility = android.view.View.GONE

            // 读取图片数据并上传分析
            val inputStream = contentResolver.openInputStream(uri)
            val imageBytes = inputStream?.readBytes()
            inputStream?.close()

            if (imageBytes != null) {
                uploadAndAnalyze(imageBytes, "photo.jpg")
            } else {
                showError(t("无法读取图片", "Unable to read image file."))
            }

        } catch (e: Exception) {
            Log.e(TAG, "处理图片失败", e)
            showError("${t("处理图片失败", "Image processing failed")}: ${e.message}")
        }
    }

    /**
     * 上传图片并分析
     */
    private fun uploadAndAnalyze(imageBytes: ByteArray, fileName: String) {
        showLoading()

        val userId = currentUserId
        if (userId.isNullOrBlank()) {
            hideLoading()
            showError(t("缺少用户信息，请重新登录", "Missing user info. Please sign in again."))
            return
        }

        lifecycleScope.launch {
            try {
                // 创建 MultipartBody.Part
                val requestFile = imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                val imagePart = MultipartBody.Part.createFormData(
                    "image",
                    fileName,
                    requestFile
                )
                val userIdPart = userId.toRequestBody("text/plain".toMediaTypeOrNull())
                val mealTypePart = selectedMealType?.name?.toRequestBody("text/plain".toMediaTypeOrNull())

                Log.d(TAG, "上传图片: $fileName, 大小: ${imageBytes.size} bytes, 餐次: ${selectedMealType?.name}")

                // 调用 API
                val response = foodApiService.analyzeFood(imagePart, userIdPart, mealTypePart)

                hideLoading()

                if (response.isSuccessful && response.body() != null) {
                    val result = response.body()!!
                    Log.d(TAG, "分析成功: $result")
                    showResult(result, refreshProgress = true)
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    Log.e(TAG, "API 错误: ${response.code()} - $errorBody")
                    showError("${t("服务器错误", "Server error")}: ${response.code()}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "网络请求失败", e)
                hideLoading()
                showError(
                    "${t("网络错误", "Network error")}: ${e.message}\n\n${
                        t("请确保后端服务正在运行", "Please make sure backend service is running.")
                    }"
                )
            }
        }
    }

    /**
     * 显示加载中
     */
    private fun showLoading() {
        isAnalyzing = true
        setPrimaryActionsEnabled(false)
        progressBar.visibility = android.view.View.VISIBLE
        cardResult.visibility = android.view.View.GONE
        tvError.visibility = android.view.View.GONE
    }

    /**
     * 隐藏加载中
     */
    private fun hideLoading() {
        isAnalyzing = false
        setPrimaryActionsEnabled(true)
        progressBar.visibility = android.view.View.GONE
    }

    private fun setPrimaryActionsEnabled(enabled: Boolean) {
        btnTakePhoto.isEnabled = enabled
        btnPickImage.isEnabled = enabled
        btnRecommend.isEnabled = enabled
    }

    /**
     * 显示分析结果
     */
    private fun showResult(response: FoodAnalysisResponse) {
        showResult(response, refreshProgress = true)
    }

    private fun showResult(response: FoodAnalysisResponse, refreshProgress: Boolean) {
        lastDetectedFoodNameRaw = response.foodName
        tvFoodName.text = cleanFoodName(response.foodName)
        tvCalories.text = response.calories.toInt().toString()
        tvProtein.text = response.protein.toInt().toString()
        tvFat.text = response.fat.toInt().toString()
        tvCarbs.text = response.carbs.toInt().toString()

        response.imageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->
            ivFoodImage.load(BackendUrls.absolutize(imageUrl))
        }

        cardResult.visibility = android.view.View.VISIBLE
        cardDayProgress.visibility = android.view.View.VISIBLE
        tvError.visibility = android.view.View.GONE

        if (refreshProgress) {
            loadUserDailyIntakeSummary()
        }
    }

    private fun cleanFoodName(raw: String): String {
        val name = raw.trim()
        val lower = name.lowercase()
        val looksGeneric =
            name.contains("豆包识别") ||
                name.contains("综合餐食") ||
                lower.contains("doubao") ||
                lower.contains("composite meal")
        if (looksGeneric || name.isBlank()) {
            return t("营养餐盘", "NutriNest Meal")
        }
        return localizeFoodName(name)
    }

    private fun localizeFoodName(name: String): String {
        if (!isEnglishUi) return name
        return when (name) {
            "海鲜拼盘" -> "Seafood Platter"
            "清炒西兰花" -> "Stir-fried Broccoli"
            "番茄炒蛋" -> "Tomato & Egg Stir-fry"
            "鸡胸肉沙拉" -> "Chicken Breast Salad"
            "牛肉饭" -> "Beef Rice Bowl"
            "三明治" -> "Sandwich"
            "水果拼盘" -> "Fruit Platter"
            else -> name
        }
    }

    private fun renderProgressValues(animate: Boolean = false) {
        // 使用每日目标计算百分比，展示今日累计。
        val caloriesPercent = toPercent(currentCalories, dailyGoalCalories)
        val proteinPercent = toPercent(currentProtein, dailyGoalProtein)
        val fatPercent = toPercent(currentFat, dailyGoalFat)
        val carbsPercent = toPercent(currentCarbs, dailyGoalCarbs)

        applyProgressVisual(pbDayCalories, caloriesPercent)
        applyProgressVisual(pbDayProtein, proteinPercent)
        applyProgressVisual(pbDayFat, fatPercent)
        applyProgressVisual(pbDayCarbs, carbsPercent)
        updateWarningIcons(caloriesPercent, proteinPercent, fatPercent, carbsPercent)

        if (animate) {
            val modeViews = listOf(
                tvProgressModeHint,
                tvDayCaloriesValue,
                tvDayProteinValue,
                tvDayFatValue,
                tvDayCarbsValue
            )
            var remainingFadeOut = modeViews.size
            modeViews.forEach { view ->
                view.animate().cancel()
                view.animate().alpha(0f).setDuration(100).withEndAction {
                    remainingFadeOut -= 1
                    if (remainingFadeOut == 0) {
                        updateProgressDisplayText(caloriesPercent, proteinPercent, fatPercent, carbsPercent)
                        modeViews.forEach { fadeInView ->
                            fadeInView.animate().alpha(1f).setDuration(100).start()
                        }
                    }
                }.start()
            }
            return
        }

        updateProgressDisplayText(caloriesPercent, proteinPercent, fatPercent, carbsPercent)
    }

    private fun updateProgressDisplayText(
        caloriesPercent: Int,
        proteinPercent: Int,
        fatPercent: Int,
        carbsPercent: Int
    ) {

        if (progressDisplayMode == ProgressDisplayMode.PERCENTAGE) {
            tvProgressModeHint.text = t("今日累计百分比（点按切换）", "Today's % progress (tap to switch)")
            tvDayCaloriesValue.text = formatPercentForUi(caloriesPercent)
            tvDayProteinValue.text = formatPercentForUi(proteinPercent)
            tvDayFatValue.text = formatPercentForUi(fatPercent)
            tvDayCarbsValue.text = formatPercentForUi(carbsPercent)
        } else {
            tvProgressModeHint.text = t("今日累计绝对值（点按切换）", "Today's absolute values (tap to switch)")
            tvDayCaloriesValue.text =
                "${currentCalories.roundToInt()}kcal / ${dailyGoalCalories.roundToInt()}kcal"
            tvDayProteinValue.text =
                "${currentProtein.roundToInt()}g / ${dailyGoalProtein.roundToInt()}g"
            tvDayFatValue.text = "${currentFat.roundToInt()}g / ${dailyGoalFat.roundToInt()}g"
            tvDayCarbsValue.text = "${currentCarbs.roundToInt()}g / ${dailyGoalCarbs.roundToInt()}g"
        }
    }

    private fun applyProgressVisual(progressBar: ProgressBar, percent: Int) {
        val intakeLevel = getIntakeLevel(percent)
        val tintColor = ContextCompat.getColor(this, intakeLevel.toColorRes())
        progressBar.max = 100
        progressBar.progressTintList = ColorStateList.valueOf(tintColor)
        progressBar.progress = percent.coerceIn(0, 100)
    }

    private fun updateWarningIcons(
        caloriesPercent: Int,
        proteinPercent: Int,
        fatPercent: Int,
        carbsPercent: Int
    ) {
        ivDayCaloriesWarning.visibility = if (caloriesPercent > 100) android.view.View.VISIBLE else android.view.View.GONE
        ivDayProteinWarning.visibility = if (proteinPercent > 100) android.view.View.VISIBLE else android.view.View.GONE
        ivDayFatWarning.visibility = if (fatPercent > 100) android.view.View.VISIBLE else android.view.View.GONE
        ivDayCarbsWarning.visibility = if (carbsPercent > 100) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun getIntakeLevel(percent: Int): IntakeLevel {
        return when {
            percent < 50 || percent > 120 -> IntakeLevel.LOW_OR_EXCESSIVE
            percent < 80 || percent > 100 -> IntakeLevel.CAUTION
            else -> IntakeLevel.IDEAL
        }
    }

    private fun IntakeLevel.toColorRes(): Int {
        return when (this) {
            IntakeLevel.LOW_OR_EXCESSIVE -> R.color.zk_progress_level_red
            IntakeLevel.CAUTION -> R.color.zk_progress_level_yellow
            IntakeLevel.IDEAL -> R.color.zk_progress_level_green
        }
    }

    private fun toPercent(value: Float, total: Float): Int {
        if (total <= 0f) return 0
        return ((value / total) * 100f).roundToInt().coerceAtLeast(0)
    }

    private fun formatPercentForUi(percent: Int): String {
        return if (percent > 100) "100%+" else "$percent%"
    }

    // 独立封装临时报表入口，后续替换主导航时可直接移除该方法及 FAB。
    private fun openReportScreen() {
        val userId = currentUserId
        if (userId.isNullOrBlank()) {
            Toast.makeText(
                this,
                t("缺少用户信息，请重新登录", "Missing user info. Please sign in again."),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val intent = Intent(this, ReportActivity::class.java)
        intent.putExtra("user_id", userId)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
    }

    // 独立封装临时个人资料入口，后续替换主导航时可直接移除该方法及 FAB。
    private fun openPersonalProfileScreen() {
        val userId = currentUserId
        if (userId.isNullOrBlank()) {
            Toast.makeText(
                this,
                t("缺少用户信息，请重新登录", "Missing user info. Please sign in again."),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val intent = Intent(this, PersonalProfileActivity::class.java)
        intent.putExtra("user_id", userId)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
    }

    // 智能推荐入口
    private fun openRecommendScreen() {
        if (isAnalyzing) {
            Toast.makeText(this, t("分析进行中，请稍候", "Analysis in progress, please wait."), Toast.LENGTH_SHORT).show()
            return
        }

        val userId = currentUserId
        if (userId.isNullOrBlank()) {
            Toast.makeText(
                this,
                t("缺少用户信息，请重新登录", "Missing user info. Please sign in again."),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val mealType = selectedMealType?.name
        if (mealType.isNullOrBlank()) {
            Toast.makeText(this, t("请先选择餐次", "Please select a meal type first."), Toast.LENGTH_SHORT).show()
            return
        }

        RecommendActivity.start(this, userId, mealType)
    }

    /**
     * 显示错误信息
     */
    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = android.view.View.VISIBLE
        cardResult.visibility = android.view.View.GONE
        hideLoading()
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
