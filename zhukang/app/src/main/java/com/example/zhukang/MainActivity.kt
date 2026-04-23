package com.example.zhukang

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
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
        BREAKFAST, LUNCH, DINNER
    }

    // Views
    private lateinit var btnBreakfast: Button
    private lateinit var btnLunch: Button
    private lateinit var btnDinner: Button
    private lateinit var btnTakePhoto: Button
    private lateinit var btnTestImage: Button
    private lateinit var btnRecommend: Button
    private lateinit var ivFoodImage: ImageView
    private lateinit var cardImage: CardView
    private lateinit var cardResult: CardView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvFoodName: TextView
    private lateinit var tvCalories: TextView
    private lateinit var tvProtein: TextView
    private lateinit var tvFat: TextView
    private lateinit var tvCarbs: TextView
    private lateinit var cardDayProgress: CardView
    private lateinit var fabProfileEntry: FloatingActionButton
    private lateinit var fabReportEntry: FloatingActionButton
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

    // 当前选中的餐次（初始为 null，用户必须手动选择）
    private var selectedMealType: MealType? = null

    // 当前餐次的目标值（每日目标 × 餐次比例）
    private var mealGoalCalories = 0f
    private var mealGoalProtein = 0f
    private var mealGoalFat = 0f
    private var mealGoalCarbs = 0f

    // 餐次目标比例
    private val mealRatios = mapOf(
        MealType.BREAKFAST to 0.30f,
        MealType.LUNCH to 0.40f,
        MealType.DINNER to 0.30f
    )

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
            showError("需要相机权限才能拍照")
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
            showError("拍照取消或失败")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 初始化 Views
        initViews()

        // 初始化 API 服务
        foodApiService = FoodApiService.create()
        currentUserId = intent.getStringExtra("user_id")

        // 设置按钮点击事件
        btnTakePhoto.setOnClickListener {
            checkCameraPermissionAndOpen()
        }

        btnTestImage.setOnClickListener {
            analyzeTestImage()
        }

        cardDayProgress.setOnClickListener {
            progressDisplayMode = if (progressDisplayMode == ProgressDisplayMode.PERCENTAGE) {
                ProgressDisplayMode.ABSOLUTE
            } else {
                ProgressDisplayMode.PERCENTAGE
            }
            renderProgressValues(animate = true)
        }

        fabReportEntry.setOnClickListener {
            openReportScreen()
        }

        fabProfileEntry.setOnClickListener {
            openPersonalProfileScreen()
        }

        // 餐次选择按钮点击事件
        btnBreakfast.setOnClickListener { selectMealType(MealType.BREAKFAST) }
        btnLunch.setOnClickListener { selectMealType(MealType.LUNCH) }
        btnDinner.setOnClickListener { selectMealType(MealType.DINNER) }

        // 智能推荐按钮
        btnRecommend.setOnClickListener { openRecommendScreen() }

        loadUserGoalTargets()
        // 移除：不在页面加载时显示进度条，只在分析食物后显示
        // loadUserDailyIntakeSummary()
    }

    private fun loadUserGoalTargets() {
        val userId = currentUserId
        if (userId.isNullOrBlank()) {
            Toast.makeText(this, "未检测到用户信息，使用默认目标", Toast.LENGTH_SHORT).show()
            updateMealGoals()
            return
        }

        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                runCatching { authApiService.getUserGoalTargets(userId) }.getOrNull()
            }

            if (response?.isSuccessful != true || response.body() == null) {
                Toast.makeText(this@MainActivity, "加载目标进度失败，使用默认目标", Toast.LENGTH_SHORT).show()
                updateMealGoals()
                return@launch
            }

            val body = response.body()!!
            dailyGoalCalories = body.targetDailyCaloriesKcal.coerceAtLeast(0f)
            dailyGoalProtein = body.targetProteinG.coerceAtLeast(0f)
            dailyGoalFat = body.targetFatG.coerceAtLeast(0f)
            dailyGoalCarbs = body.targetCarbG.coerceAtLeast(0f)

            updateMealGoals()
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

        btnTakePhoto = findViewById(R.id.btnTakePhoto)
        btnTestImage = findViewById(R.id.btnTestImage)
        btnRecommend = findViewById(R.id.btnRecommend)
        ivFoodImage = findViewById(R.id.ivFoodImage)
        cardImage = findViewById(R.id.cardImage)
        cardResult = findViewById(R.id.cardResult)
        progressBar = findViewById(R.id.progressBar)
        tvFoodName = findViewById(R.id.tvFoodName)
        tvCalories = findViewById(R.id.tvCalories)
        tvProtein = findViewById(R.id.tvProtein)
        tvFat = findViewById(R.id.tvFat)
        tvCarbs = findViewById(R.id.tvCarbs)
        cardDayProgress = findViewById(R.id.cardDayProgress)
        fabProfileEntry = findViewById(R.id.fabProfileEntry)
        fabReportEntry = findViewById(R.id.fabReportEntry)
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
    }

    /**
     * 选择餐次并更新按钮状态
     */
    private fun selectMealType(type: MealType) {
        selectedMealType = type
        updateMealTypeButtonStyles()
        updateMealGoals()
    }

    /**
     * 更新当餐目标（每日目标 × 餐次比例）
     */
    private fun updateMealGoals() {
        val ratio = mealRatios[selectedMealType] ?: 0.40f
        mealGoalCalories = dailyGoalCalories * ratio
        mealGoalProtein = dailyGoalProtein * ratio
        mealGoalFat = dailyGoalFat * ratio
        mealGoalCarbs = dailyGoalCarbs * ratio
    }

    /**
     * 更新餐次按钮样式（选中态高亮）
     */
    private fun updateMealTypeButtonStyles() {
        val selectedColor = ContextCompat.getColor(this, R.color.zk_primary)
        val unselectedColor = ContextCompat.getColor(this, R.color.zk_text_secondary)
        val selectedBg = ContextCompat.getColor(this, R.color.zk_primary)

        btnBreakfast.apply {
            if (selectedMealType == MealType.BREAKFAST) {
                setBackgroundColor(selectedBg)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.zk_on_primary))
            } else {
                setBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.transparent))
                setTextColor(unselectedColor)
            }
        }

        btnLunch.apply {
            if (selectedMealType == MealType.LUNCH) {
                setBackgroundColor(selectedBg)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.zk_on_primary))
            } else {
                setBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.transparent))
                setTextColor(unselectedColor)
            }
        }

        btnDinner.apply {
            if (selectedMealType == MealType.DINNER) {
                setBackgroundColor(selectedBg)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.zk_on_primary))
            } else {
                setBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.transparent))
                setTextColor(unselectedColor)
            }
        }
    }

    /**
     * 检查相机权限并打开相机
     */
    private fun checkCameraPermissionAndOpen() {
        if (selectedMealType == null) {
            Toast.makeText(this, "请先选择餐次", Toast.LENGTH_SHORT).show()
            return
        }
        if (ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION)
            == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            cameraPermissionLauncher.launch(CAMERA_PERMISSION)
        }
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
            showError("打开相机失败: ${e.message}")
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
                showError("无法读取图片")
            }

        } catch (e: Exception) {
            Log.e(TAG, "处理图片失败", e)
            showError("处理图片失败: ${e.message}")
        }
    }

    /**
     * 分析测试图片
     */
    private fun analyzeTestImage() {
        if (selectedMealType == null) {
            Toast.makeText(this, "请先选择餐次", Toast.LENGTH_SHORT).show()
            return
        }
        showLoading()

        lifecycleScope.launch {
            try {
                // 从 res/raw 读取测试图片
                val imageBytes = resources.openRawResource(R.raw.test_food).readBytes()

                // 显示测试图片
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                ivFoodImage.setImageBitmap(bitmap)
                cardImage.visibility = android.view.View.VISIBLE

                uploadAndAnalyze(imageBytes, "test_food.jpg")

            } catch (e: Exception) {
                Log.e(TAG, "读取测试图片失败", e)
                showError("读取测试图片失败: ${e.message}")
            }
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
            showError("缺少用户信息，请重新登录")
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
                    showResult(result)
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    Log.e(TAG, "API 错误: ${response.code()} - $errorBody")
                    showError("服务器错误: ${response.code()}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "网络请求失败", e)
                hideLoading()
                showError("网络错误: ${e.message}\n\n请确保后端服务正在运行")
            }
        }
    }

    /**
     * 显示加载中
     */
    private fun showLoading() {
        progressBar.visibility = android.view.View.VISIBLE
        cardResult.visibility = android.view.View.GONE
        tvError.visibility = android.view.View.GONE
    }

    /**
     * 隐藏加载中
     */
    private fun hideLoading() {
        progressBar.visibility = android.view.View.GONE
    }

    /**
     * 显示分析结果
     */
    private fun showResult(response: FoodAnalysisResponse) {
        tvFoodName.text = response.foodName
        tvCalories.text = response.calories.toInt().toString()
        tvProtein.text = response.protein.toInt().toString()
        tvFat.text = response.fat.toInt().toString()
        tvCarbs.text = response.carbs.toInt().toString()

        // 更新当前摄入量为这一餐的数据（用于进度条显示）
        currentCalories = response.calories
        currentProtein = response.protein
        currentFat = response.fat ?: 0f
        currentCarbs = response.carbs ?: 0f

        // 若后端返回了抠图后的缩略图 URL，则替换当前预览图，呈现 Bitelog 同款效果。
        response.imageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->
            ivFoodImage.load(BackendUrls.absolutize(imageUrl))
        }

        cardResult.visibility = android.view.View.VISIBLE
        cardDayProgress.visibility = android.view.View.VISIBLE
        tvError.visibility = android.view.View.GONE

        // 更新进度条显示（使用当餐目标）
        renderProgressValues()
    }

    private fun renderProgressValues(animate: Boolean = false) {
        // 使用当餐目标计算百分比
        val caloriesPercent = toPercent(currentCalories, mealGoalCalories)
        val proteinPercent = toPercent(currentProtein, mealGoalProtein)
        val fatPercent = toPercent(currentFat, mealGoalFat)
        val carbsPercent = toPercent(currentCarbs, mealGoalCarbs)

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
            tvProgressModeHint.text = "当餐百分比（点按切换）"
            tvDayCaloriesValue.text = "$caloriesPercent%"
            tvDayProteinValue.text = "$proteinPercent%"
            tvDayFatValue.text = "$fatPercent%"
            tvDayCarbsValue.text = "$carbsPercent%"
        } else {
            tvProgressModeHint.text = "当餐绝对值（点按切换）"
            tvDayCaloriesValue.text = "${currentCalories.roundToInt()}kcal / ${mealGoalCalories.roundToInt()}kcal"
            tvDayProteinValue.text = "${currentProtein.roundToInt()}g / ${mealGoalProtein.roundToInt()}g"
            tvDayFatValue.text = "${currentFat.roundToInt()}g / ${mealGoalFat.roundToInt()}g"
            tvDayCarbsValue.text = "${currentCarbs.roundToInt()}g / ${mealGoalCarbs.roundToInt()}g"
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

    // 独立封装临时报表入口，后续替换主导航时可直接移除该方法及 FAB。
    private fun openReportScreen() {
        val userId = currentUserId
        if (userId.isNullOrBlank()) {
            Toast.makeText(this, "缺少用户信息，请重新登录", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, ReportActivity::class.java)
        intent.putExtra("user_id", userId)
        startActivity(intent)
    }

    // 独立封装临时个人资料入口，后续替换主导航时可直接移除该方法及 FAB。
    private fun openPersonalProfileScreen() {
        val userId = currentUserId
        if (userId.isNullOrBlank()) {
            Toast.makeText(this, "缺少用户信息，请重新登录", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, PersonalProfileActivity::class.java)
        intent.putExtra("user_id", userId)
        startActivity(intent)
    }

    // 智能推荐入口
    private fun openRecommendScreen() {
        val userId = currentUserId
        if (userId.isNullOrBlank()) {
            Toast.makeText(this, "缺少用户信息，请重新登录", Toast.LENGTH_SHORT).show()
            return
        }

        val mealType = selectedMealType?.name
        if (mealType.isNullOrBlank()) {
            Toast.makeText(this, "请先选择餐次", Toast.LENGTH_SHORT).show()
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
        cardDayProgress.visibility = android.view.View.GONE
        hideLoading()
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
