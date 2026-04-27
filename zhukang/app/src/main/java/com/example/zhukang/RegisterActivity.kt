package com.example.zhukang

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import android.text.InputType
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.zhukang.api.AuthApiService
import com.example.zhukang.model.RegisterRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class RegisterActivity : AppCompatActivity() {

    private val authApiService by lazy { AuthApiService.create() }

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

        btnSubmitRegister.isEnabled = false
        val defaultText = btnSubmitRegister.text
        btnSubmitRegister.text = "注册中..."
        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                runCatching {
                    authApiService.register(RegisterRequest(account = account, password = password))
                }.getOrNull()
            }

            btnSubmitRegister.isEnabled = true
            btnSubmitRegister.text = defaultText

            if (response == null) {
                Toast.makeText(this@RegisterActivity, "网络请求失败，请稍后重试", Toast.LENGTH_SHORT).show()
                return@launch
            }

            if (!response.isSuccessful || response.body() == null) {
                val detail = runCatching {
                    val raw = response.errorBody()?.string().orEmpty()
                    JSONObject(raw).optString("detail")
                }.getOrNull().orEmpty()

                when (response.code()) {
                    409 -> {
                        etRegisterAccount.error = if (detail.isNotBlank()) detail else "账号已存在"
                        etRegisterAccount.requestFocus()
                    }

                    else -> {
                        if (detail.isNotBlank()) {
                            Toast.makeText(this@RegisterActivity, detail, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@RegisterActivity, "注册失败：${response.code()}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                return@launch
            }

            val body = response.body()!!
            val intent = Intent(this@RegisterActivity, UserProfileActivity::class.java).apply {
                putExtra("user_id", body.userId)
            }
            startActivity(intent)
        }
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
