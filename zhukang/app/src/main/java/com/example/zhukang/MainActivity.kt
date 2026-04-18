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

    // Views
    private lateinit var btnTakePhoto: Button
    private lateinit var btnTestImage: Button
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

        loadUserGoalTargets()
        loadUserDailyIntakeSummary()
    }

    private fun loadUserGoalTargets() {
        val userId = currentUserId
        if (userId.isNullOrBlank()) {
            Toast.makeText(this, "未检测到用户信息，使用默认目标", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                runCatching { authApiService.getUserGoalTargets(userId) }.getOrNull()
            }

            if (response?.isSuccessful != true || response.body() == null) {
                Toast.makeText(this@MainActivity, "加载目标进度失败，使用默认目标", Toast.LENGTH_SHORT).show()
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
        btnTakePhoto = findViewById(R.id.btnTakePhoto)
        btnTestImage = findViewById(R.id.btnTestImage)
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
     * 检查相机权限并打开相机
     */
    private fun checkCameraPermissionAndOpen() {
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

                Log.d(TAG, "上传图片: $fileName, 大小: ${imageBytes.size} bytes")

                // 调用 API
                val response = foodApiService.analyzeFood(imagePart, userIdPart)

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

        cardResult.visibility = android.view.View.VISIBLE
        cardDayProgress.visibility = android.view.View.VISIBLE
        tvError.visibility = android.view.View.GONE
        loadUserDailyIntakeSummary()
    }

    private fun renderProgressValues(animate: Boolean = false) {
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
            tvProgressModeHint.text = "百分比（点按切换）"
            tvDayCaloriesValue.text = "$caloriesPercent%"
            tvDayProteinValue.text = "$proteinPercent%"
            tvDayFatValue.text = "$fatPercent%"
            tvDayCarbsValue.text = "$carbsPercent%"
        } else {
            tvProgressModeHint.text = "绝对值（点按切换）"
            tvDayCaloriesValue.text = "${currentCalories.roundToInt()}kcal / ${dailyGoalCalories.roundToInt()}kcal"
            tvDayProteinValue.text = "${currentProtein.roundToInt()}g / ${dailyGoalProtein.roundToInt()}g"
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
