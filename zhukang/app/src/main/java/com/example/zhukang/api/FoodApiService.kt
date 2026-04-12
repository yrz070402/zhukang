package com.example.zhukang.api

import com.example.zhukang.model.FoodAnalysisResponse
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.util.concurrent.TimeUnit

/**
 * 食物分析 API 服务接口
 */
interface FoodApiService {

    @Multipart
    @POST("api/v1/food/analyze")
    suspend fun analyzeFood(
        @Part image: MultipartBody.Part
    ): Response<FoodAnalysisResponse>

    companion object {
        // 模拟器访问本地主机使用 10.0.2.2
        // 真机访问改为电脑实际 IP，如 http://192.168.0.226:8000/
        private const val BASE_URL = "http://10.0.2.2:8000/"

        fun create(): FoodApiService {
            // 配置 OkHttp 客户端，增加超时时间
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)    // 连接超时 60秒
                .readTimeout(60, TimeUnit.SECONDS)       // 读取超时 60秒
                .writeTimeout(60, TimeUnit.SECONDS)      // 写入超时 60秒
                .callTimeout(120, TimeUnit.SECONDS)      // 整体调用超时 120秒
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)  // 使用自定义的 OkHttp 客户端
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            return retrofit.create(FoodApiService::class.java)
        }
    }
}
