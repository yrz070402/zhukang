package com.example.zhukang

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.zhukang.api.AuthApiService
import com.example.zhukang.model.LoginRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    private val authApiService by lazy { AuthApiService.create() }

    private lateinit var etAccount: EditText
    private lateinit var etPassword: EditText
    private lateinit var cbShowPassword: CheckBox
    private lateinit var btnLogin: Button
    private lateinit var tvGoRegister: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etAccount = findViewById(R.id.etAccount)
        etPassword = findViewById(R.id.etPassword)
        cbShowPassword = findViewById(R.id.cbShowPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tvGoRegister = findViewById(R.id.tvGoRegister)

        cbShowPassword.setOnCheckedChangeListener { _, isChecked ->
            val selection = etPassword.selectionStart
            etPassword.inputType = if (isChecked) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            etPassword.setSelection(selection.coerceAtLeast(0))
        }

        btnLogin.setOnClickListener {
            handleLoginClick()
        }

        tvGoRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun handleLoginClick() {
        val account = etAccount.text.toString().trim()
        val password = etPassword.text.toString()

        etAccount.error = null
        etPassword.error = null

        if (!validateLoginInput(account, password)) {
            return
        }

        val defaultLoginText = btnLogin.text
        btnLogin.isEnabled = false
        btnLogin.text = "登录中..."
        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                runCatching {
                    authApiService.login(LoginRequest(account = account, password = password))
                }.getOrNull()
            }

            btnLogin.isEnabled = true
            btnLogin.text = defaultLoginText

            if (response == null) {
                Toast.makeText(this@LoginActivity, "网络请求失败，请稍后重试", Toast.LENGTH_SHORT).show()
                return@launch
            }

            if (!response.isSuccessful || response.body() == null) {
                val detail = runCatching {
                    val raw = response.errorBody()?.string().orEmpty()
                    JSONObject(raw).optString("detail")
                }.getOrNull().orEmpty()

                when (response.code()) {
                    404 -> {
                        etAccount.error = if (detail.isNotBlank()) detail else "用户尚未注册"
                        etAccount.requestFocus()
                    }

                    401 -> {
                        etPassword.error = if (detail.isNotBlank()) detail else "密码错误"
                        etPassword.requestFocus()
                    }

                    else -> {
                        if (detail.isNotBlank()) {
                            Toast.makeText(this@LoginActivity, detail, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@LoginActivity, "登录失败：${response.code()}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                return@launch
            }

            val intent = Intent(this@LoginActivity, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("user_id", response.body()!!.userId)
            }
            startActivity(intent)
        }
    }

    private fun validateLoginInput(account: String, password: String): Boolean {
        if (account.isEmpty()) {
            etAccount.error = "账号不能为空"
            etAccount.requestFocus()
            return false
        }
        if (account.length !in 3..32) {
            etAccount.error = "账号长度需为 3-32 位"
            etAccount.requestFocus()
            return false
        }
        if (password.isBlank()) {
            etPassword.error = "密码不能为空"
            etPassword.requestFocus()
            return false
        }
        if (password.length !in 6..64) {
            etPassword.error = "密码长度需为 6-64 位"
            etPassword.requestFocus()
            return false
        }

        return true
    }
}
