package com.example.zhukang

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

    private enum class BottomTab {
        HOME, RECOMMEND, REPORT, PROFILE
    }

    private val authApiService by lazy { AuthApiService.create() }

    private lateinit var btnBack: ImageButton
    private lateinit var tvPageTitle: TextView
    private lateinit var tvPageSubtitle: TextView
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
    private lateinit var ivAvatar: ImageView
    private lateinit var tvAccount: TextView
    private lateinit var tvNicknameLabel: TextView
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
    private lateinit var rbGenderMale: RadioButton
    private lateinit var rbGenderFemale: RadioButton
    private lateinit var sliderActivityLevel: Slider
    private lateinit var tvActivityLevelValue: TextView

    private lateinit var rgGoalType: RadioGroup
    private lateinit var rbGoalMuscle: RadioButton
    private lateinit var rbGoalFatLoss: RadioButton
    private lateinit var rbGoalMaintain: RadioButton
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
    private lateinit var tvBasicInfoTitle: TextView
    private lateinit var tvAgeLabel: TextView
    private lateinit var tvHeightLabel: TextView
    private lateinit var tvWeightLabel: TextView
    private lateinit var tvGenderTitle: TextView
    private lateinit var tvGoalSectionTitle: TextView
    private lateinit var tvGoalCaloriesLabel: TextView
    private lateinit var tvGoalCarbLabel: TextView
    private lateinit var tvGoalProteinLabel: TextView
    private lateinit var tvGoalFatLabel: TextView
    private lateinit var tvTagsTitle: TextView

    private var userId: String? = null
    private var selectedAvatarIndex: Int = 0
    private var loadedProfile: UserProfileDetailResponse? = null
    private var isEnglishUi = false

    private val avatarResourceNames = listOf(
        "avatar_01", "avatar_02", "avatar_03", "avatar_04", "avatar_05", "avatar_06",
        "avatar_07", "avatar_08", "avatar_09", "avatar_10", "avatar_11", "avatar_12",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_personal_profile)

        userId = intent.getStringExtra("user_id")
        isEnglishUi = SessionPrefs.isEnglishEnabled(this)
        bindViews()
        applyLanguageTexts()
        bindEvents()

        if (userId.isNullOrBlank()) {
            showState(t("缺少用户信息，请重新登录", "Missing user information. Please sign in again."), true)
            return
        }

        loadUserProfile()
    }

    override fun onResume() {
        super.onResume()
        val latestEnglish = SessionPrefs.isEnglishEnabled(this)
        if (latestEnglish != isEnglishUi) {
            isEnglishUi = latestEnglish
            applyLanguageTexts()
            loadedProfile?.let { applyProfileToViews(it) }
        }
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.btnBack)
        tvPageTitle = findViewById(R.id.tvPageTitle)
        tvPageSubtitle = findViewById(R.id.tvPageSubtitle)
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
        ivAvatar = findViewById(R.id.ivAvatar)
        tvAccount = findViewById(R.id.tvAccount)
        tvNicknameLabel = findViewById(R.id.tvNicknameLabel)
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
        rbGenderMale = findViewById(R.id.rbGenderMale)
        rbGenderFemale = findViewById(R.id.rbGenderFemale)
        sliderActivityLevel = findViewById(R.id.sliderActivityLevel)
        tvActivityLevelValue = findViewById(R.id.tvActivityLevelValue)

        rgGoalType = findViewById(R.id.rgGoalType)
        rbGoalMuscle = findViewById(R.id.rbGoalMuscle)
        rbGoalFatLoss = findViewById(R.id.rbGoalFatLoss)
        rbGoalMaintain = findViewById(R.id.rbGoalMaintain)
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
        tvBasicInfoTitle = findViewById(R.id.tvBasicInfoTitle)
        tvAgeLabel = findViewById(R.id.tvAgeLabel)
        tvHeightLabel = findViewById(R.id.tvHeightLabel)
        tvWeightLabel = findViewById(R.id.tvWeightLabel)
        tvGenderTitle = findViewById(R.id.tvGenderTitle)
        tvGoalSectionTitle = findViewById(R.id.tvGoalSectionTitle)
        tvGoalCaloriesLabel = findViewById(R.id.tvGoalCaloriesLabel)
        tvGoalCarbLabel = findViewById(R.id.tvGoalCarbLabel)
        tvGoalProteinLabel = findViewById(R.id.tvGoalProteinLabel)
        tvGoalFatLabel = findViewById(R.id.tvGoalFatLabel)
        tvTagsTitle = findViewById(R.id.tvTagsTitle)
    }

    private fun bindEvents() {
        btnBack.setOnClickListener { finish() }
        setupBottomNav()
        setBottomNavSelection(BottomTab.PROFILE)
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
            tvActivityLevelValue.text = t("运动量", "Activity") + ": ${toActivityLevelLabel(value.roundToInt())}"
        }
    }

    private fun setupBottomNav() {
        navHome.setOnClickListener {
            setBottomNavSelection(BottomTab.HOME)
            navigateToMain()
        }
        navRecommend.setOnClickListener {
            setBottomNavSelection(BottomTab.RECOMMEND)
            navigateToRecommend()
        }
        navReport.setOnClickListener {
            setBottomNavSelection(BottomTab.REPORT)
            navigateToReport()
        }
        navProfile.setOnClickListener {
            setBottomNavSelection(BottomTab.PROFILE)
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
        val currentUserId = userId
        if (currentUserId.isNullOrBlank()) {
            toastMissingUser()
            return
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("user_id", currentUserId)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    private fun navigateToRecommend() {
        val currentUserId = userId
        if (currentUserId.isNullOrBlank()) {
            toastMissingUser()
            return
        }
        RecommendActivity.start(this, currentUserId, SessionPrefs.getLastMealType(this))
        finish()
    }

    private fun navigateToReport() {
        val currentUserId = userId
        if (currentUserId.isNullOrBlank()) {
            toastMissingUser()
            return
        }
        val intent = Intent(this, ReportActivity::class.java).apply {
            putExtra("user_id", currentUserId)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    private fun loadUserProfile() {
        val currentUserId = userId ?: return
        showState(t("资料加载中...", "Loading profile..."), false)

        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                runCatching { authApiService.getUserProfile(currentUserId) }.getOrNull()
            }

            if (response?.isSuccessful != true || response.body() == null) {
                showState(t("加载失败，请稍后重试", "Failed to load. Please try again."), true)
                return@launch
            }

            loadedProfile = response.body()
            applyProfileToViews(response.body()!!)
            showState("", false)
        }
    }

    private fun applyProfileToViews(profile: UserProfileDetailResponse) {
        tvAccount.text = t("账号", "Account") + ": ${profile.account}"
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
        tvActivityLevelValue.text = t("运动量", "Activity") + ": ${toActivityLevelLabel(profile.activityLevel)}"

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
            etNicknameEdit.error = t("昵称不能为空", "Nickname cannot be empty")
            return
        }
        if (age == null || age !in 1..120) {
            openFieldEditor(tvAgeValue, etAgeEdit)
            etAgeEdit.error = t("请输入 1-120", "Enter a value between 1 and 120")
            return
        }
        if (height == null || height <= 0f) {
            openFieldEditor(tvHeightValue, etHeightEdit)
            etHeightEdit.error = t("身高需大于 0", "Height must be greater than 0")
            return
        }
        if (weight == null || weight <= 0f) {
            openFieldEditor(tvWeightValue, etWeightEdit)
            etWeightEdit.error = t("体重需大于 0", "Weight must be greater than 0")
            return
        }
        if (sex == null) {
            toast(t("请选择性别", "Please select sex"))
            return
        }
        if (goalType == null) {
            toast(t("请选择目标类型", "Please select goal type"))
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
        showState(t("保存中...", "Saving..."), false)

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
                showState(t("资料保存失败", "Failed to save profile"), true)
                return@launch
            }

            val tagsResponse = withContext(Dispatchers.IO) {
                runCatching {
                    authApiService.putUserTags(UserTagsUpdateRequest(userId = currentUserId, dietaryPreferences = tags))
                }.getOrNull()
            }

            btnSaveProfile.isEnabled = true

            if (tagsResponse?.isSuccessful != true) {
                showState(t("标签保存失败", "Failed to save tags"), true)
                return@launch
            }

            toast(t("保存成功", "Saved successfully"))
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
            .setTitle(t("选择头像", "Choose Avatar"))
            .setView(grid)
            .setNegativeButton(t("取消", "Cancel"), null)
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
            toast(t("标签已存在", "Tag already exists"))
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
            closeIconContentDescription = t("删除标签", "Remove tag")
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
            0 -> t("久坐少动", "Sedentary")
            1 -> t("轻度活动", "Light activity")
            2 -> t("中度活动", "Moderate activity")
            else -> t("高频训练", "High training")
        }
    }

    private fun applyLanguageTexts() {
        btnBack.contentDescription = t("返回", "Back")
        ivAvatar.contentDescription = t("用户头像", "User avatar")
        tvPageTitle.text = t("个人资料", "Profile")
        tvPageSubtitle.text = t("编辑后将同步更新健康目标", "Changes will sync with your nutrition goals")
        tvNicknameLabel.text = t("昵称:", "Nickname:")
        btnChangeAvatar.text = t("切换头像", "Change Avatar")
        tvBasicInfoTitle.text = t("基础信息", "Basic Info")
        tvAgeLabel.text = t("年龄（岁）", "Age (years)")
        tvHeightLabel.text = t("身高（cm）", "Height (cm)")
        tvWeightLabel.text = t("体重（kg）", "Weight (kg)")
        tvGenderTitle.text = t("性别", "Sex")
        rbGenderMale.text = t("男", "Male")
        rbGenderFemale.text = t("女", "Female")
        tvGoalSectionTitle.text = t("目标与配额", "Goals & Targets")
        rbGoalMuscle.text = t("活力塑型", "Muscle Gain")
        rbGoalFatLoss.text = t("轻松减脂", "Fat Loss")
        rbGoalMaintain.text = t("健康养成", "Maintain")
        tvGoalCaloriesLabel.text = t("目标卡路里（kcal）", "Target Calories (kcal)")
        tvGoalCarbLabel.text = t("目标碳水（g）", "Target Carbs (g)")
        tvGoalProteinLabel.text = t("目标蛋白质（g）", "Target Protein (g)")
        tvGoalFatLabel.text = t("目标脂肪（g）", "Target Fat (g)")
        tvTagsTitle.text = t("用户标签", "Tags")
        etTagInput.hint = t("输入标签并添加", "Type a tag and add")
        btnAddTag.text = t("添加", "Add")
        btnSaveProfile.text = t("保存修改", "Save Changes")

        etNicknameEdit.hint = t("请输入昵称", "Enter nickname")
        etAgeEdit.hint = t("请输入年龄（1-120）", "Enter age (1-120)")
        etHeightEdit.hint = t("请输入身高，例如 170", "Enter height, e.g. 170")
        etWeightEdit.hint = t("请输入体重，例如 65.5", "Enter weight, e.g. 65.5")
        etTargetCaloriesEdit.hint = t("请输入目标卡路里", "Enter target calories")
        etTargetCarbEdit.hint = t("请输入目标碳水", "Enter target carbs")
        etTargetProteinEdit.hint = t("请输入目标蛋白质", "Enter target protein")
        etTargetFatEdit.hint = t("请输入目标脂肪", "Enter target fat")

        if (loadedProfile == null) {
            tvAccount.text = t("账号", "Account") + ": --"
            tvNicknameValue.text = t("点击输入昵称", "Tap to enter nickname")
            tvAgeValue.text = t("点击填写年龄", "Tap to enter age")
            tvHeightValue.text = t("点击填写身高", "Tap to enter height")
            tvWeightValue.text = t("点击填写体重", "Tap to enter weight")
            tvTargetCaloriesValue.text = t("点击填写目标卡路里", "Tap to enter target calories")
            tvTargetCarbValue.text = t("点击填写目标碳水", "Tap to enter target carbs")
            tvTargetProteinValue.text = t("点击填写目标蛋白质", "Tap to enter target protein")
            tvTargetFatValue.text = t("点击填写目标脂肪", "Tap to enter target fat")
        }

        navHomeLabel.text = t("首页", "Home")
        navRecommendLabel.text = t("推荐", "Suggest")
        navReportLabel.text = t("报表", "Report")
        navProfileLabel.text = t("我的", "Me")
    }

    private fun toastMissingUser() {
        toast(t("缺少用户信息", "Missing user information."))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun t(zh: String, en: String): String = if (isEnglishUi) en else zh

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
