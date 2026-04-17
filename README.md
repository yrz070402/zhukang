# 筑康 App - 团队开发指南

## 项目结构

```
大作业/
├── backend/          # Python FastAPI 后端
├── zhukang/          # Android Kotlin 客户端
└── 筑康App_PRD_v1.0.md  # 产品需求文档
```

---

## 一、后端启动（Python）

### 1. 环境要求
- Python 3.10+
- pip

### 2. 安装依赖
```bash
cd backend
pip install -r requirements.txt
```

### 3. 配置 API Key
复制 `.env.example` 为 `.env`，填入你的豆包 API Key：
```bash
cp .env.example .env
```

编辑 `.env` 文件：
```env
DOUBAO_API_KEY=你的API密钥
DOUBAO_BASE_URL=https://ark.cn-beijing.volces.com/api/v3
DOUBAO_MODEL_ID=你的模型ID
```

### 4. 启动服务
```bash
python -m uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

### 5. 测试接口
浏览器访问：http://localhost:8000/docs

---

## 二、Android 客户端（Kotlin）

### 1. 环境要求
- Android Studio Hedgehog 或更新版本
- JDK 11+
- Android SDK (minSdk 24)

### 2. 打开项目
1. 打开 Android Studio
2. File → Open → 选择 `zhukang` 文件夹
3. 等待 Gradle Sync 完成

### 3. 配置服务器地址
编辑 `app/src/main/java/com/example/zhukang/api/FoodApiService.kt`：

```kotlin
// 模拟器使用
private const val BASE_URL = "http://10.0.2.2:8000/"

// 真机使用（改为你的电脑 IP）
private const val BASE_URL = "http://192.168.x.x:8000/"
```

### 4. 运行
- 模拟器：直接点击 Run
- 真机：确保手机和电脑在同一 WiFi，关闭防火墙

---

## 三、API 接口说明

### 食物分析接口
```
POST /api/v1/food/analyze
Content-Type: multipart/form-data

参数：
- image: 图片文件

返回：
{
    "food_name": "食物名称",
    "calories": 热量(千卡),
    "protein": 蛋白质(克),
    "fat": 脂肪(克),
    "carbs": 碳水化合物(克)
}
```

---

## 四、常见问题

### Q: 网络连接超时？
1. 确认后端服务正在运行
2. 检查 IP 地址配置是否正确
3. 关闭 Windows 防火墙或添加端口规则：
   ```powershell
   netsh advfirewall firewall add rule name="FastAPI" dir=in action=allow protocol=tcp localport=8000
   ```

### Q: 模拟器无法访问后端？
- 模拟器必须使用 `http://10.0.2.2:8000/`

### Q: 真机无法访问后端？
- 真机使用电脑的局域网 IP（如 `192.168.0.226`）
- 确保手机和电脑在同一 WiFi

### Q: 豆包 API 报错？
- 检查 `.env` 文件中的 API Key 和 Model ID 是否正确
- 确认豆包 API 配额是否充足

---

## 五、开发进度

### 已完成 ✅
- [x] 后端基础架构
- [x] 豆包 AI 图片识别
- [x] Android 相机拍照
- [x] 图片上传与分析
- [x] 营养结果展示
- [x] 用户登录注册
- [x] 每日营养进度条

### 待开发 📋
- [ ] 历史记录存储
- [ ] 个性化建议
- [ ] 数据报告生成

---

## 六、引用
当前目标卡路里计算公式来源于B站up主-好人松松

## 七、联系方式

项目负责人：[你的名字]
更新日期：2026-04-10
