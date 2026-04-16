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

class LoginActivity : AppCompatActivity() {

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

        if (!validateLoginInput(account, password)) {
            return
        }

        // 预留接口说明：
        // POST /api/v1/auth/login
        // Request JSON: {"account": "<账号>", "password": "<密码明文>"}
        // Response JSON(建议): {"access_token": "...", "token_type": "bearer", "user_id": "..."}
        // 当前后端尚未实现 auth 路由，所以这里先直接跳转主界面；后续替换为真实接口调用与结果处理。
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
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

        Toast.makeText(this, "登录参数校验通过", Toast.LENGTH_SHORT).show()
        return true
    }
}
