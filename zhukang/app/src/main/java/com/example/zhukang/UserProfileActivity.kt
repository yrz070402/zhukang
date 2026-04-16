package com.example.zhukang

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class UserProfileActivity : AppCompatActivity() {

    private lateinit var etAge: EditText
    private lateinit var etHeight: EditText
    private lateinit var etWeight: EditText
    private lateinit var rgGender: RadioGroup
    private lateinit var rgUserTag: RadioGroup
    private lateinit var cgDietOptions: ChipGroup
    private lateinit var cgCustomDietTags: ChipGroup
    private lateinit var etCustomDiet: EditText
    private lateinit var btnSubmitProfile: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        etAge = findViewById(R.id.etAge)
        etHeight = findViewById(R.id.etHeight)
        etWeight = findViewById(R.id.etWeight)
        rgGender = findViewById(R.id.rgGender)
        rgUserTag = findViewById(R.id.rgUserTag)
        cgDietOptions = findViewById(R.id.cgDietOptions)
        cgCustomDietTags = findViewById(R.id.cgCustomDietTags)
        etCustomDiet = findViewById(R.id.etCustomDiet)
        btnSubmitProfile = findViewById(R.id.btnSubmitProfile)

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

        // TODO: 后续可将年龄、身高、体重、性别、用户标签、饮食偏好提交到用户资料接口。
        // 预留接口建议：POST /api/v1/user/profile
        // Request JSON 建议字段：age, height_cm, weight_kg, gender, user_tag, diet_preferences(list)

        val summary = if (mergedDietOptions.isEmpty()) "未选择饮食偏好与限制" else mergedDietOptions.joinToString("、")
        Toast.makeText(this, "信息提交成功：$summary", Toast.LENGTH_SHORT).show()

        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
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
}
