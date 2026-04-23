package com.example.zhukang

import android.app.AlertDialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.zhukang.api.AuthApiService
import com.example.zhukang.model.UserProfileDetailResponse
import com.example.zhukang.model.UserProfileUpdateRequest
import com.example.zhukang.model.UserTagsUpdateRequest
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class PersonalProfileActivity : AppCompatActivity() {

    private val authApiService by lazy { AuthApiService.create() }

    private lateinit var btnBack: ImageButton
    private lateinit var ivAvatar: ImageView
    private lateinit var tvAccount: TextView
    private lateinit var tvNicknameValue: TextView
    private lateinit var etNicknameEdit: EditText
    private lateinit var btnChangeAvatar: Button

    private lateinit var tvAgeValue: TextView
    private lateinit var etAgeEdit: EditText
    private lateinit var tvHeightValue: TextView
    private lateinit var etHeightEdit: EditText
    private lateinit var tvWeightValue: TextView
    private lateinit var etWeightEdit: EditText
    private lateinit var rgGender: RadioGroup
    private lateinit var sliderActivityLevel: Slider
    private lateinit var tvActivityLevelValue: TextView

    private lateinit var rgGoalType: RadioGroup
    private lateinit var tvTargetCaloriesValue: TextView
    private lateinit var etTargetCaloriesEdit: EditText
    private lateinit var tvTargetProteinValue: TextView
    private lateinit var etTargetProteinEdit: EditText
    private lateinit var tvTargetFatValue: TextView
    private lateinit var etTargetFatEdit: EditText
    private lateinit var tvTargetCarbValue: TextView
    private lateinit var etTargetCarbEdit: EditText

    private lateinit var cgUserTags: ChipGroup
    private lateinit var etTagInput: EditText
    private lateinit var btnAddTag: Button

    private lateinit var btnSaveProfile: Button
    private lateinit var tvState: TextView

    private var userId: String? = null
    private var selectedAvatarIndex: Int = 0
    private var loadedProfile: UserProfileDetailResponse? = null

    private val avatarResourceNames = listOf(
        "avatar_01", "avatar_02", "avatar_03", "avatar_04", "avatar_05", "avatar_06",
        "avatar_07", "avatar_08", "avatar_09", "avatar_10", "avatar_11", "avatar_12",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_personal_profile)

        userId = intent.getStringExtra("user_id")
        bindViews()
        bindEvents()

        if (userId.isNullOrBlank()) {
            showState("缺少用户信息，请重新登录", true)
            return
        }

        loadUserProfile()
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.btnBack)
        ivAvatar = findViewById(R.id.ivAvatar)
        tvAccount = findViewById(R.id.tvAccount)
        tvNicknameValue = findViewById(R.id.tvNicknameValue)
        etNicknameEdit = findViewById(R.id.etNicknameEdit)
        btnChangeAvatar = findViewById(R.id.btnChangeAvatar)

        tvAgeValue = findViewById(R.id.tvAgeValue)
        etAgeEdit = findViewById(R.id.etAgeEdit)
        tvHeightValue = findViewById(R.id.tvHeightValue)
        etHeightEdit = findViewById(R.id.etHeightEdit)
        tvWeightValue = findViewById(R.id.tvWeightValue)
        etWeightEdit = findViewById(R.id.etWeightEdit)
        rgGender = findViewById(R.id.rgGender)
        sliderActivityLevel = findViewById(R.id.sliderActivityLevel)
        tvActivityLevelValue = findViewById(R.id.tvActivityLevelValue)

        rgGoalType = findViewById(R.id.rgGoalType)
        tvTargetCaloriesValue = findViewById(R.id.tvTargetCaloriesValue)
        etTargetCaloriesEdit = findViewById(R.id.etTargetCaloriesEdit)
        tvTargetProteinValue = findViewById(R.id.tvTargetProteinValue)
        etTargetProteinEdit = findViewById(R.id.etTargetProteinEdit)
        tvTargetFatValue = findViewById(R.id.tvTargetFatValue)
        etTargetFatEdit = findViewById(R.id.etTargetFatEdit)
        tvTargetCarbValue = findViewById(R.id.tvTargetCarbValue)
        etTargetCarbEdit = findViewById(R.id.etTargetCarbEdit)

        cgUserTags = findViewById(R.id.cgUserTags)
        etTagInput = findViewById(R.id.etTagInput)
        btnAddTag = findViewById(R.id.btnAddTag)

        btnSaveProfile = findViewById(R.id.btnSaveProfile)
        tvState = findViewById(R.id.tvState)
    }

    private fun bindEvents() {
        btnBack.setOnClickListener { finish() }
        btnChangeAvatar.setOnClickListener { showAvatarPicker() }
        btnAddTag.setOnClickListener { addTagFromInput() }
        btnSaveProfile.setOnClickListener { saveProfile() }

        bindEditableField(tvNicknameValue, etNicknameEdit)
        bindEditableField(tvAgeValue, etAgeEdit)
        bindEditableField(tvHeightValue, etHeightEdit)
        bindEditableField(tvWeightValue, etWeightEdit)
        bindEditableField(tvTargetCaloriesValue, etTargetCaloriesEdit)
        bindEditableField(tvTargetCarbValue, etTargetCarbEdit)
        bindEditableField(tvTargetProteinValue, etTargetProteinEdit)
        bindEditableField(tvTargetFatValue, etTargetFatEdit)

        etTagInput.setOnEditorActionListener { _, actionId, event ->
            val isImeSubmit = actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO
            val isHardwareEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (isImeSubmit || isHardwareEnter) {
                addTagFromInput()
                true
            } else {
                false
            }
        }

        sliderActivityLevel.addOnChangeListener { _, value, _ ->
            tvActivityLevelValue.text = "运动量：${toActivityLevelLabel(value.roundToInt())}"
        }
    }

    private fun loadUserProfile() {
        val currentUserId = userId ?: return
        showState("资料加载中...", false)

        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                runCatching { authApiService.getUserProfile(currentUserId) }.getOrNull()
            }

            if (response?.isSuccessful != true || response.body() == null) {
                showState("加载失败，请稍后重试", true)
                return@launch
            }

            loadedProfile = response.body()
            applyProfileToViews(response.body()!!)
            showState("", false)
        }
    }

    private fun applyProfileToViews(profile: UserProfileDetailResponse) {
        tvAccount.text = "账号：${profile.account}"
        setFieldValue(tvNicknameValue, etNicknameEdit, profile.nickname)

        selectedAvatarIndex = profile.avatarIndex.coerceIn(0, 11)
        ivAvatar.setImageResource(resolveAvatarResId(selectedAvatarIndex))

        setFieldValue(tvAgeValue, etAgeEdit, profile.age.toString())
        setFieldValue(tvHeightValue, etHeightEdit, profile.heightCm.toString())
        setFieldValue(tvWeightValue, etWeightEdit, profile.weightKg.toString())

        when (profile.sex.lowercase()) {
            "male" -> rgGender.check(R.id.rbGenderMale)
            "female" -> rgGender.check(R.id.rbGenderFemale)
            else -> rgGender.clearCheck()
        }

        sliderActivityLevel.value = profile.activityLevel.toFloat().coerceIn(0f, 3f)
        tvActivityLevelValue.text = "运动量：${toActivityLevelLabel(profile.activityLevel)}"

        when (profile.goalType) {
            "muscle_gain" -> rgGoalType.check(R.id.rbGoalMuscle)
            "fat_loss" -> rgGoalType.check(R.id.rbGoalFatLoss)
            "maintain" -> rgGoalType.check(R.id.rbGoalMaintain)
            else -> rgGoalType.check(R.id.rbGoalMaintain)
        }

        setFieldValue(tvTargetCaloriesValue, etTargetCaloriesEdit, profile.targetDailyCaloriesKcal.toString())
        setFieldValue(tvTargetProteinValue, etTargetProteinEdit, profile.targetProteinG.toString())
        setFieldValue(tvTargetFatValue, etTargetFatEdit, profile.targetFatG.toString())
        setFieldValue(tvTargetCarbValue, etTargetCarbEdit, profile.targetCarbG.toString())

        cgUserTags.removeAllViews()
        profile.dietaryTags.forEach { addTagChip(it.displayName) }
    }

    private fun saveProfile() {
        val currentUserId = userId ?: return
        val currentProfile = loadedProfile ?: return

        val nickname = currentFieldText(tvNicknameValue, etNicknameEdit).trim()
        val age = currentFieldText(tvAgeValue, etAgeEdit).trim().toIntOrNull()
        val height = currentFieldText(tvHeightValue, etHeightEdit).trim().toFloatOrNull()
        val weight = currentFieldText(tvWeightValue, etWeightEdit).trim().toFloatOrNull()
        val sex = selectedSex()
        val activityLevel = sliderActivityLevel.value.roundToInt().coerceIn(0, 3)
        val goalType = selectedGoalType()

        if (nickname.isBlank()) {
            openFieldEditor(tvNicknameValue, etNicknameEdit)
            etNicknameEdit.error = "昵称不能为空"
            return
        }
        if (age == null || age !in 1..120) {
            openFieldEditor(tvAgeValue, etAgeEdit)
            etAgeEdit.error = "请输入 1-120"
            return
        }
        if (height == null || height <= 0f) {
            openFieldEditor(tvHeightValue, etHeightEdit)
            etHeightEdit.error = "身高需大于 0"
            return
        }
        if (weight == null || weight <= 0f) {
            openFieldEditor(tvWeightValue, etWeightEdit)
            etWeightEdit.error = "体重需大于 0"
            return
        }
        if (sex == null) {
            Toast.makeText(this, "请选择性别", Toast.LENGTH_SHORT).show()
            return
        }
        if (goalType == null) {
            Toast.makeText(this, "请选择目标类型", Toast.LENGTH_SHORT).show()
            return
        }

        val calories = currentFieldText(tvTargetCaloriesValue, etTargetCaloriesEdit).trim().toFloatOrNull()
        val protein = currentFieldText(tvTargetProteinValue, etTargetProteinEdit).trim().toFloatOrNull()
        val fat = currentFieldText(tvTargetFatValue, etTargetFatEdit).trim().toFloatOrNull()
        val carb = currentFieldText(tvTargetCarbValue, etTargetCarbEdit).trim().toFloatOrNull()

        val shouldRecalculate =
            age != currentProfile.age ||
            height != currentProfile.heightCm ||
            weight != currentProfile.weightKg ||
            sex != currentProfile.sex ||
            activityLevel != currentProfile.activityLevel ||
            goalType != currentProfile.goalType

        val tags = getAllTags()

        btnSaveProfile.isEnabled = false
        showState("保存中...", false)

        lifecycleScope.launch {
            val profileResponse = withContext(Dispatchers.IO) {
                runCatching {
                    authApiService.patchUserProfile(
                        UserProfileUpdateRequest(
                            userId = currentUserId,
                            nickname = nickname,
                            avatarIndex = selectedAvatarIndex,
                            age = age,
                            sex = sex,
                            heightCm = height,
                            weightKg = weight,
                            activityLevel = activityLevel,
                            goalType = goalType,
                            targetDailyCaloriesKcal = if (shouldRecalculate) null else calories,
                            targetProteinG = if (shouldRecalculate) null else protein,
                            targetFatG = if (shouldRecalculate) null else fat,
                            targetCarbG = if (shouldRecalculate) null else carb,
                        )
                    )
                }.getOrNull()
            }

            if (profileResponse?.isSuccessful != true || profileResponse.body() == null) {
                btnSaveProfile.isEnabled = true
                showState("资料保存失败", true)
                return@launch
            }

            val tagsResponse = withContext(Dispatchers.IO) {
                runCatching {
                    authApiService.putUserTags(UserTagsUpdateRequest(userId = currentUserId, dietaryPreferences = tags))
                }.getOrNull()
            }

            btnSaveProfile.isEnabled = true

            if (tagsResponse?.isSuccessful != true) {
                showState("标签保存失败", true)
                return@launch
            }

            Toast.makeText(this@PersonalProfileActivity, "保存成功", Toast.LENGTH_SHORT).show()
            loadUserProfile()
        }
    }

    private fun showAvatarPicker() {
        val grid = GridLayout(this).apply {
            columnCount = 3
            rowCount = 4
            useDefaultMargins = true
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("选择头像")
            .setView(grid)
            .setNegativeButton("取消", null)
            .create()

        avatarResourceNames.forEachIndexed { index, _ ->
            val avatarResId = resolveAvatarResId(index)
            val card = MaterialCardView(this).apply {
                radius = dp(12).toFloat()
                strokeWidth = dp(2)
                strokeColor = getColor(if (index == selectedAvatarIndex) R.color.zk_primary else R.color.zk_text_secondary)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = dp(92)
                    height = dp(112)
                }
                setCardBackgroundColor(getColor(R.color.zk_surface))
            }

            val avatarView = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
                setImageResource(avatarResId)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }

            card.addView(avatarView)
            card.setOnClickListener {
                selectedAvatarIndex = index
                ivAvatar.setImageResource(resolveAvatarResId(selectedAvatarIndex))
                dialog.dismiss()
            }
            grid.addView(card)
        }

        dialog.show()
    }

    private fun resolveAvatarResId(index: Int): Int {
        val safeIndex = index.coerceIn(0, 11)
        val drawableName = avatarResourceNames[safeIndex]
        val resId = resources.getIdentifier(drawableName, "drawable", packageName)
        return if (resId != 0) resId else R.drawable.ic_launcher_foreground
    }

    private fun addTagFromInput() {
        val tag = etTagInput.text.toString().trim()
        if (tag.isBlank()) {
            return
        }
        if (getAllTags().any { it.equals(tag, ignoreCase = true) }) {
            Toast.makeText(this, "标签已存在", Toast.LENGTH_SHORT).show()
            return
        }
        addTagChip(tag)
        etTagInput.text?.clear()
    }

    private fun addTagChip(tag: String) {
        val chip = Chip(this).apply {
            text = tag
            isCheckable = false
            isCloseIconVisible = true
            closeIconContentDescription = "删除标签"
            setOnCloseIconClickListener { cgUserTags.removeView(this) }
        }
        cgUserTags.addView(chip)
    }

    private fun getAllTags(): List<String> {
        val tags = mutableListOf<String>()
        for (i in 0 until cgUserTags.childCount) {
            val view = cgUserTags.getChildAt(i)
            if (view is Chip) {
                tags.add(view.text.toString())
            }
        }
        return tags.distinct()
    }

    private fun selectedSex(): String? {
        return when (rgGender.checkedRadioButtonId) {
            R.id.rbGenderMale -> "male"
            R.id.rbGenderFemale -> "female"
            else -> null
        }
    }

    private fun selectedGoalType(): String? {
        return when (rgGoalType.checkedRadioButtonId) {
            R.id.rbGoalMuscle -> "muscle_gain"
            R.id.rbGoalFatLoss -> "fat_loss"
            R.id.rbGoalMaintain -> "maintain"
            else -> null
        }
    }

    private fun bindEditableField(valueView: TextView, editView: EditText) {
        valueView.setOnClickListener {
            openFieldEditor(valueView, editView)
        }
        editView.setOnEditorActionListener { _, actionId, event ->
            val isImeSubmit = actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO
            val isHardwareEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (isImeSubmit || isHardwareEnter) {
                editView.clearFocus()
                true
            } else {
                false
            }
        }
        editView.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                closeFieldEditor(valueView, editView)
            }
        }
    }

    private fun openFieldEditor(valueView: TextView, editView: EditText) {
        editView.setText(valueView.text.toString())
        valueView.visibility = View.GONE
        editView.visibility = View.VISIBLE
        editView.requestFocus()
        editView.setSelection(editView.text?.length ?: 0)
    }

    private fun closeFieldEditor(valueView: TextView, editView: EditText) {
        val text = editView.text.toString().trim()
        if (text.isNotBlank()) {
            valueView.text = text
        }
        editView.visibility = View.GONE
        valueView.visibility = View.VISIBLE
    }

    private fun setFieldValue(valueView: TextView, editView: EditText, value: String) {
        valueView.text = value
        editView.setText(value)
        editView.visibility = View.GONE
        valueView.visibility = View.VISIBLE
    }

    private fun currentFieldText(valueView: TextView, editView: EditText): String {
        return if (editView.visibility == View.VISIBLE) {
            editView.text.toString()
        } else {
            valueView.text.toString()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

    private fun toActivityLevelLabel(value: Int): String {
        return when (value.coerceIn(0, 3)) {
            0 -> "久坐少动"
            1 -> "轻度活动"
            2 -> "中度活动"
            else -> "高频训练"
        }
    }

    private fun showState(message: String, isError: Boolean) {
        if (message.isBlank()) {
            tvState.visibility = View.GONE
            return
        }
        tvState.visibility = View.VISIBLE
        tvState.text = message
        tvState.setTextColor(if (isError) getColor(R.color.zk_error) else getColor(R.color.zk_text_secondary))
    }
}
