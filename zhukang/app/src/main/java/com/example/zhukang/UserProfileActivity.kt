package com.example.zhukang

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.zhukang.api.AuthApiService
import com.example.zhukang.model.ProfileSetupRequest
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class UserProfileActivity : AppCompatActivity() {

    private val authApiService by lazy { AuthApiService.create() }
    private val fallbackDietOptions = listOf("素食主义", "乳糖不耐受", "外卖狂热选手", "麦质敏感", "坚果过敏", "清真")

    private lateinit var etAge: EditText
    private lateinit var etHeight: EditText
    private lateinit var etWeight: EditText
    private lateinit var rgGender: RadioGroup
    private lateinit var rgUserTag: RadioGroup
    private lateinit var sliderActivityLevel: Slider
    private lateinit var tvActivityLevelValue: TextView
    private lateinit var cgDietOptions: ChipGroup
    private lateinit var cgCustomDietTags: ChipGroup
    private lateinit var etCustomDiet: EditText
    private lateinit var btnSubmitProfile: Button
    private var userId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        etAge = findViewById(R.id.etAge)
        etHeight = findViewById(R.id.etHeight)
        etWeight = findViewById(R.id.etWeight)
        rgGender = findViewById(R.id.rgGender)
        rgUserTag = findViewById(R.id.rgUserTag)
        sliderActivityLevel = findViewById(R.id.sliderActivityLevel)
        tvActivityLevelValue = findViewById(R.id.tvActivityLevelValue)
        cgDietOptions = findViewById(R.id.cgDietOptions)
        cgCustomDietTags = findViewById(R.id.cgCustomDietTags)
        etCustomDiet = findViewById(R.id.etCustomDiet)
        btnSubmitProfile = findViewById(R.id.btnSubmitProfile)
        userId = intent.getStringExtra("user_id")

        tvActivityLevelValue.text = "当前：${toActivityLevelLabel(sliderActivityLevel.value.toInt())}"
        sliderActivityLevel.addOnChangeListener { _, value, _ ->
            tvActivityLevelValue.text = "当前：${toActivityLevelLabel(value.toInt())}"
        }

        etCustomDiet.setOnEditorActionListener { _, actionId, event ->
            val isImeSubmit = actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO
            val isHardwareEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (isImeSubmit || isHardwareEnter) {
                addCustomDietTagsFromInput(showDuplicateToast = true)
                true
            } else {
                false
            }
        }

        btnSubmitProfile.setOnClickListener {
            submitProfile()
        }

        loadPopularDietTags()
    }

    private fun loadPopularDietTags() {
        lifecycleScope.launch {
            val options = withContext(Dispatchers.IO) {
                runCatching {
                    authApiService.getPopularTags(limit = 6)
                }.getOrNull()
            }

            val tags = if (options?.isSuccessful == true) {
                options.body()?.items
                    ?.map { it.displayName.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.distinct()
                    ?.take(6)
                    .orEmpty()
            } else {
                emptyList()
            }

            val finalTags = if (tags.isNotEmpty()) tags else fallbackDietOptions
            renderDietOptionChips(finalTags)
        }
    }

    private fun renderDietOptionChips(tags: List<String>) {
        cgDietOptions.removeAllViews()
        tags.forEach { label ->
            val chip = Chip(this).apply {
                text = label
                isCheckable = true
            }
            cgDietOptions.addView(chip)
        }
    }

    private fun submitProfile() {
        val age = etAge.text.toString().trim()
        val height = etHeight.text.toString().trim()
        val weight = etWeight.text.toString().trim()

        if (!validateBasicInfo(age, height, weight)) {
            return
        }

        if (rgGender.checkedRadioButtonId == -1) {
            Toast.makeText(this, "请选择性别", Toast.LENGTH_SHORT).show()
            return
        }

        if (rgUserTag.checkedRadioButtonId == -1) {
            Toast.makeText(this, "请在三个用户标签中选择一个", Toast.LENGTH_SHORT).show()
            return
        }

        // 提交前把输入框中尚未回车提交的文本也转换为标签。
        addCustomDietTagsFromInput(showDuplicateToast = false)

        val selectedDietOptions = getSelectedDietOptions()
        val customDietOptions = getCustomDietOptions()
        val mergedDietOptions = (selectedDietOptions + customDietOptions)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        val userIdValue = userId
        if (userIdValue.isNullOrBlank()) {
            Toast.makeText(this, "缺少用户标识，请重新注册", Toast.LENGTH_SHORT).show()
            return
        }

        val ageValue = age.toInt()
        val heightValue = height.toFloat()
        val weightValue = weight.toFloat()
        val sexValue = toSexValue(rgGender.checkedRadioButtonId)
        val activityLevelValue = toActivityLevelCode(sliderActivityLevel.value)
        val goalIndex = rgUserTag.indexOfChild(findViewById(rgUserTag.checkedRadioButtonId)).coerceIn(0, 2)

        btnSubmitProfile.isEnabled = false
        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                runCatching {
                    authApiService.setupProfile(
                        ProfileSetupRequest(
                            userId = userIdValue,
                            age = ageValue,
                            sex = sexValue,
                            heightCm = heightValue,
                            weightKg = weightValue,
                            activityLevel = activityLevelValue,
                            goalIndex = goalIndex,
                            dietaryPreferences = mergedDietOptions,
                        )
                    )
                }.getOrNull()
            }

            btnSubmitProfile.isEnabled = true

            if (response == null) {
                Toast.makeText(this@UserProfileActivity, "网络请求失败，请稍后重试", Toast.LENGTH_SHORT).show()
                return@launch
            }

            if (!response.isSuccessful || response.body() == null) {
                Toast.makeText(this@UserProfileActivity, "资料提交失败：${response.code()}", Toast.LENGTH_SHORT).show()
                return@launch
            }

            Toast.makeText(this@UserProfileActivity, "资料提交成功", Toast.LENGTH_SHORT).show()
            val intent = Intent(this@UserProfileActivity, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
        }
    }

    private fun toSexValue(checkedId: Int): String? {
        val index = rgGender.indexOfChild(findViewById(checkedId))
        return when (index) {
            0 -> "male"
            1 -> "female"
            else -> null
        }
    }

    private fun validateBasicInfo(age: String, height: String, weight: String): Boolean {
        val ageValue = age.toIntOrNull()
        if (ageValue == null || ageValue !in 1..120) {
            etAge.error = "请输入 1-120 的年龄"
            etAge.requestFocus()
            return false
        }

        val heightValue = height.toFloatOrNull()
        if (heightValue == null || heightValue !in 50f..250f) {
            etHeight.error = "请输入 50-250 cm 的身高"
            etHeight.requestFocus()
            return false
        }

        val weightValue = weight.toFloatOrNull()
        if (weightValue == null || weightValue !in 20f..300f) {
            etWeight.error = "请输入 20-300 kg 的体重"
            etWeight.requestFocus()
            return false
        }

        return true
    }

    private fun getSelectedDietOptions(): List<String> {
        return cgDietOptions.checkedChipIds.mapNotNull { id ->
            findViewById<Chip>(id)?.text?.toString()
        }
    }

    private fun getCustomDietOptions(): List<String> {
        val tags = mutableListOf<String>()
        for (index in 0 until cgCustomDietTags.childCount) {
            val child = cgCustomDietTags.getChildAt(index)
            if (child is Chip) {
                tags.add(child.text.toString())
            }
        }
        return tags
    }

    private fun addCustomDietTagsFromInput(showDuplicateToast: Boolean) {
        val rawInput = etCustomDiet.text.toString()
        if (rawInput.isBlank()) {
            return
        }

        val labels = rawInput
            .split(Regex("[,，;；\\n]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        labels.forEach { label ->
            addSingleCustomDietTag(label, showDuplicateToast)
        }

        etCustomDiet.text?.clear()
    }

    private fun addSingleCustomDietTag(label: String, showDuplicateToast: Boolean) {
        if (isDuplicatedDietTag(label)) {
            if (showDuplicateToast) {
                Toast.makeText(this, "标签“$label”已存在", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val chip = Chip(this).apply {
            text = label
            isCheckable = false
            isCloseIconVisible = true
            closeIconContentDescription = "删除标签"
            setOnCloseIconClickListener {
                cgCustomDietTags.removeView(this)
            }
        }
        cgCustomDietTags.addView(chip)
    }

    private fun isDuplicatedDietTag(label: String): Boolean {
        return (getSelectedDietOptions() + getCustomDietOptions()).any {
            it.equals(label, ignoreCase = true)
        }
    }

    private fun toActivityLevelLabel(value: Int): String {
        return when (value.coerceIn(0, 3)) {
            0 -> "久坐少动"
            1 -> "轻度活动"
            2 -> "中度活动"
            else -> "高频训练"
        }
    }

    private fun toActivityLevelCode(value: Float): Int {
        return value.roundToInt().coerceIn(0, 3)
    }
}
