package com.example.zhukang.api

/**
 * 后端地址辅助工具：
 * - 暴露统一的 BASE_URL（Android 模拟器默认访问宿主机）。
 * - 把后端返回的相对路径（如 /static/...）拼接成可访问的绝对地址。
 */
object BackendUrls {
    const val BASE_URL: String = "http://10.0.2.2:8000/"

    fun absolutize(relativeOrAbsolute: String): String {
        if (relativeOrAbsolute.startsWith("http://", ignoreCase = true) ||
            relativeOrAbsolute.startsWith("https://", ignoreCase = true)
        ) {
            return relativeOrAbsolute
        }
        val trimmedBase = BASE_URL.trimEnd('/')
        val trimmedPath = if (relativeOrAbsolute.startsWith("/")) {
            relativeOrAbsolute
        } else {
            "/$relativeOrAbsolute"
        }
        return "$trimmedBase$trimmedPath"
    }
}
