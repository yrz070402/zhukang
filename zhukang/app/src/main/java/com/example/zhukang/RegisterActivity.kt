package com.example.zhukang

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import android.text.InputType
import androidx.appcompat.app.AppCompatActivity

class RegisterActivity : AppCompatActivity() {

    private lateinit var etRegisterAccount: EditText
    private lateinit var etRegisterPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var cbShowRegisterPassword: CheckBox
    private lateinit var btnSubmitRegister: Button
    private lateinit var btnBackToLogin: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        etRegisterAccount = findViewById(R.id.etRegisterAccount)
        etRegisterPassword = findViewById(R.id.etRegisterPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        cbShowRegisterPassword = findViewById(R.id.cbShowRegisterPassword)
        btnSubmitRegister = findViewById(R.id.btnSubmitRegister)
        btnBackToLogin = findViewById(R.id.btnBackToLogin)

        cbShowRegisterPassword.setOnCheckedChangeListener { _, isChecked ->
            val passwordSelection = etRegisterPassword.selectionStart
            val confirmSelection = etConfirmPassword.selectionStart
            val inputType = if (isChecked) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            etRegisterPassword.inputType = inputType
            etConfirmPassword.inputType = inputType
            etRegisterPassword.setSelection(passwordSelection.coerceAtLeast(0))
            etConfirmPassword.setSelection(confirmSelection.coerceAtLeast(0))
        }

        btnSubmitRegister.setOnClickListener {
            handleRegisterClick()
        }

        btnBackToLogin.setOnClickListener {
            finish()
        }
    }

    private fun handleRegisterClick() {
        val account = etRegisterAccount.text.toString().trim()
        val password = etRegisterPassword.text.toString()
        val confirmPassword = etConfirmPassword.text.toString()

        if (!validateRegisterInput(account, password, confirmPassword)) {
            return
        }

        // 预留接口说明：
        // POST /api/v1/auth/register
        // Request JSON: {"account": "<账号>", "password": "<密码明文>"}
        // Response JSON(建议): {"user_id": "...", "account": "..."}
        // 当前后端尚未实现 auth 路由，本阶段先进入用户信息填写界面。
        startActivity(Intent(this, UserProfileActivity::class.java))
    }

    private fun validateRegisterInput(account: String, password: String, confirmPassword: String): Boolean {
        if (account.isEmpty()) {
            etRegisterAccount.error = "账号不能为空"
            etRegisterAccount.requestFocus()
            return false
        }
        if (!account.matches(Regex("^[a-zA-Z0-9_]{3,32}$"))) {
            etRegisterAccount.error = "账号仅支持字母、数字、下划线，长度 3-32"
            etRegisterAccount.requestFocus()
            return false
        }
        if (password.length !in 6..64) {
            etRegisterPassword.error = "密码长度需为 6-64 位"
            etRegisterPassword.requestFocus()
            return false
        }
        val hasLetter = password.any { it.isLetter() }
        val hasDigit = password.any { it.isDigit() }
        if (!hasLetter || !hasDigit) {
            etRegisterPassword.error = "密码需同时包含字母和数字"
            etRegisterPassword.requestFocus()
            return false
        }
        if (confirmPassword != password) {
            etConfirmPassword.error = "两次输入密码不一致"
            etConfirmPassword.requestFocus()
            return false
        }
        return true
    }
}
