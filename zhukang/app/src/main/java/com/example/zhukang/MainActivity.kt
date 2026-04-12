package com.example.zhukang

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.example.zhukang.api.FoodApiService
import com.example.zhukang.model.FoodAnalysisResponse
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

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
    private lateinit var tvError: TextView

    // API Service
    private lateinit var foodApiService: FoodApiService

    // 当前拍照的图片 URI
    private var currentPhotoUri: Uri? = null
    private var currentPhotoPath: String? = null

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

        // 设置按钮点击事件
        btnTakePhoto.setOnClickListener {
            checkCameraPermissionAndOpen()
        }

        btnTestImage.setOnClickListener {
            analyzeTestImage()
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

        lifecycleScope.launch {
            try {
                // 创建 MultipartBody.Part
                val requestFile = imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                val imagePart = MultipartBody.Part.createFormData(
                    "image",
                    fileName,
                    requestFile
                )

                Log.d(TAG, "上传图片: $fileName, 大小: ${imageBytes.size} bytes")

                // 调用 API
                val response = foodApiService.analyzeFood(imagePart)

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
        tvError.visibility = android.view.View.GONE
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
