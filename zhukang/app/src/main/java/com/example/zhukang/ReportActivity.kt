package com.example.zhukang

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.zhukang.api.AuthApiService
import com.example.zhukang.api.BackendUrls
import com.example.zhukang.model.DietMapDay
import com.example.zhukang.model.DietMapIntakeItem
import com.example.zhukang.model.DietMapResponse
import com.example.zhukang.model.ReportSeriesPoint
import com.example.zhukang.model.UserReportResponse
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class ReportActivity : AppCompatActivity() {

    private enum class Period { DAILY, WEEKLY, MONTHLY, BITELOG }
    private enum class Metric { CALORIES, PROTEIN, FAT, CARB }
    private enum class BitelogPeriod { WEEKLY, MONTHLY }
    private enum class BottomTab { HOME, RECOMMEND, REPORT, PROFILE }

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
    private lateinit var togglePeriod: MaterialButtonToggleGroup
    private lateinit var metricScrollContainer: HorizontalScrollView
    private lateinit var chipGroupMetric: ChipGroup
    private lateinit var cardChart: CardView
    private lateinit var cardLatestSummary: CardView
    private lateinit var tvLatestSummaryTitle: TextView
    private lateinit var lineChart: LineChart
    private lateinit var tvRange: TextView
    private lateinit var tvGoalHint: TextView
    private lateinit var tvLatestSummary: TextView
    private lateinit var tvReportState: TextView

    private lateinit var cardBitelog: CardView
    private lateinit var toggleBitelogPeriod: MaterialButtonToggleGroup
    private lateinit var tvBitelogRange: TextView
    private lateinit var btnBitelogPrev: ImageButton
    private lateinit var btnBitelogNext: ImageButton
    private lateinit var bitelogGrid: GridLayout
    private lateinit var bitelogWeekdayHeader: LinearLayout
    private lateinit var tvWeekdayMon: TextView
    private lateinit var tvWeekdayTue: TextView
    private lateinit var tvWeekdayWed: TextView
    private lateinit var tvWeekdayThu: TextView
    private lateinit var tvWeekdayFri: TextView
    private lateinit var tvWeekdaySat: TextView
    private lateinit var tvWeekdaySun: TextView
    private lateinit var tvBitelogEmpty: TextView

    private var currentUserId: String? = null
    private var currentPeriod = Period.DAILY
    private var currentMetric = Metric.CALORIES
    private var currentReport: UserReportResponse? = null
    private var bitelogSubPeriod = BitelogPeriod.WEEKLY
    private var bitelogOffset = 0
    private var isEnglishUi = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_report)

        currentUserId = intent.getStringExtra("user_id")
        isEnglishUi = SessionPrefs.isEnglishEnabled(this)
        initViews()
        applyLanguageTexts()
        initChart()
        bindEvents()

        if (currentUserId.isNullOrBlank()) {
            showState(t("缺少用户信息，请返回首页重试", "Missing user info. Please return to Home and try again."), true)
            return
        }

        togglePeriod.check(R.id.btnPeriodDaily)
        chipGroupMetric.check(R.id.chipCalories)
        loadReport(currentPeriod)
    }

    override fun onResume() {
        super.onResume()
        val latestEnglish = SessionPrefs.isEnglishEnabled(this)
        if (latestEnglish != isEnglishUi) {
            isEnglishUi = latestEnglish
            applyLanguageTexts()
            if (currentPeriod == Period.BITELOG) {
                loadBitelog()
            } else {
                renderChart(currentReport)
            }
        }
    }

    private fun initViews() {
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
        togglePeriod = findViewById(R.id.togglePeriod)
        metricScrollContainer = findViewById(R.id.metricScrollContainer)
        chipGroupMetric = findViewById(R.id.chipGroupMetric)
        cardChart = findViewById(R.id.cardChart)
        cardLatestSummary = findViewById(R.id.cardLatestSummary)
        tvLatestSummaryTitle = findViewById(R.id.tvLatestSummaryTitle)
        lineChart = findViewById(R.id.lineChart)
        tvRange = findViewById(R.id.tvRange)
        tvGoalHint = findViewById(R.id.tvGoalHint)
        tvLatestSummary = findViewById(R.id.tvLatestSummary)
        tvReportState = findViewById(R.id.tvReportState)

        cardBitelog = findViewById(R.id.cardBitelog)
        toggleBitelogPeriod = findViewById(R.id.toggleBitelogPeriod)
        tvBitelogRange = findViewById(R.id.tvBitelogRange)
        btnBitelogPrev = findViewById(R.id.btnBitelogPrev)
        btnBitelogNext = findViewById(R.id.btnBitelogNext)
        bitelogGrid = findViewById(R.id.bitelogGrid)
        bitelogWeekdayHeader = findViewById(R.id.bitelogWeekdayHeader)
        tvWeekdayMon = findViewById(R.id.tvWeekdayMon)
        tvWeekdayTue = findViewById(R.id.tvWeekdayTue)
        tvWeekdayWed = findViewById(R.id.tvWeekdayWed)
        tvWeekdayThu = findViewById(R.id.tvWeekdayThu)
        tvWeekdayFri = findViewById(R.id.tvWeekdayFri)
        tvWeekdaySat = findViewById(R.id.tvWeekdaySat)
        tvWeekdaySun = findViewById(R.id.tvWeekdaySun)
        tvBitelogEmpty = findViewById(R.id.tvBitelogEmpty)
    }

    private fun initChart() {
        lineChart.setNoDataText(t("暂无可展示数据", "No data to display"))
        lineChart.description.isEnabled = false
        lineChart.setTouchEnabled(true)
        lineChart.setPinchZoom(false)
        lineChart.axisRight.isEnabled = false
        lineChart.legend.isEnabled = true

        lineChart.axisLeft.apply {
            axisMinimum = 0f
            textColor = ContextCompat.getColor(this@ReportActivity, R.color.zk_text_secondary)
            gridColor = ContextCompat.getColor(this@ReportActivity, R.color.zk_divider)
        }

        lineChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            setDrawGridLines(false)
            textColor = ContextCompat.getColor(this@ReportActivity, R.color.zk_text_secondary)
        }
    }

    private fun bindEvents() {
        btnBack.setOnClickListener { finish() }
        setupBottomNav()
        setBottomNavSelection(BottomTab.REPORT)

        togglePeriod.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            currentPeriod = when (checkedId) {
                R.id.btnPeriodDaily -> Period.DAILY
                R.id.btnPeriodWeekly -> Period.WEEKLY
                R.id.btnPeriodMonthly -> Period.MONTHLY
                else -> Period.BITELOG
            }
            applyPeriodMode()
        }

        chipGroupMetric.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            currentMetric = when (checkedIds.first()) {
                R.id.chipCalories -> Metric.CALORIES
                R.id.chipProtein -> Metric.PROTEIN
                R.id.chipFat -> Metric.FAT
                else -> Metric.CARB
            }
            renderChart(currentReport)
        }

        toggleBitelogPeriod.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            bitelogSubPeriod = if (checkedId == R.id.btnBitelogWeekly) BitelogPeriod.WEEKLY else BitelogPeriod.MONTHLY
            bitelogOffset = 0
            loadBitelog()
        }

        btnBitelogPrev.setOnClickListener {
            bitelogOffset -= 1
            loadBitelog()
        }
        btnBitelogNext.setOnClickListener {
            if (bitelogOffset >= 0) {
                toast(t("已到最新周期", "Already at latest period"))
                return@setOnClickListener
            }
            bitelogOffset += 1
            loadBitelog()
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
        }
        navProfile.setOnClickListener {
            setBottomNavSelection(BottomTab.PROFILE)
            navigateToProfile()
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
        val userId = currentUserId
        if (userId.isNullOrBlank()) {
            toast(t("缺少用户信息", "Missing user information."))
            return
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("user_id", userId)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    private fun navigateToRecommend() {
        val userId = currentUserId
        if (userId.isNullOrBlank()) {
            toast(t("缺少用户信息", "Missing user information."))
            return
        }
        RecommendActivity.start(this, userId, SessionPrefs.getLastMealType(this))
        finish()
    }

    private fun navigateToProfile() {
        val userId = currentUserId
        if (userId.isNullOrBlank()) {
            toast(t("缺少用户信息", "Missing user information."))
            return
        }
        val intent = Intent(this, PersonalProfileActivity::class.java).apply {
            putExtra("user_id", userId)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    private fun applyPeriodMode() {
        val isBitelog = currentPeriod == Period.BITELOG
        cardChart.visibility = if (isBitelog) View.GONE else View.VISIBLE
        cardLatestSummary.visibility = if (isBitelog) View.GONE else View.VISIBLE
        metricScrollContainer.visibility = if (isBitelog) View.GONE else View.VISIBLE
        cardBitelog.visibility = if (isBitelog) View.VISIBLE else View.GONE

        if (isBitelog) {
            if (toggleBitelogPeriod.checkedButtonId == View.NO_ID) {
                toggleBitelogPeriod.check(R.id.btnBitelogWeekly)
            }
            loadBitelog()
        } else {
            loadReport(currentPeriod)
        }
    }

    private fun loadReport(period: Period) {
        val userId = currentUserId ?: return
        if (period == Period.BITELOG) return
        showState(t("报表加载中...", "Loading reports..."), false)

        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                runCatching {
                    when (period) {
                        Period.DAILY -> authApiService.getDailyReport(userId)
                        Period.WEEKLY -> authApiService.getWeeklyReport(userId)
                        Period.MONTHLY -> authApiService.getMonthlyReport(userId)
                        Period.BITELOG -> error("bitelog handled separately")
                    }
                }.getOrNull()
            }

            if (response?.isSuccessful != true || response.body() == null) {
                currentReport = null
                lineChart.clear()
                showState(t("加载失败，请稍后重试", "Failed to load report. Please try again."), true)
                toast(t("报表接口请求失败", "Report API request failed"))
                return@launch
            }

            currentReport = response.body()
            renderChart(currentReport)
            showState("", false)
        }
    }

    private fun renderChart(report: UserReportResponse?) {
        if (report == null) {
            lineChart.clear()
            return
        }

        tvRange.text = t("数据范围", "Range") + ": ${report.startAt} - ${report.endAt}"

        val chartPoints = if (currentPeriod == Period.DAILY) {
            report.points
        } else {
            val chronological = report.points.reversed()
            val withData = chronological.filter { metricValue(it) != null }
            val noData = chronological.filter { metricValue(it) == null }
            withData + noData
        }

        val labels = if (currentPeriod == Period.DAILY) {
            chartPoints.map { it.date }
        } else {
            chartPoints.map { point ->
                if (metricValue(point) == null) {
                    ""
                } else {
                    point.date.substringAfter("-").replace("-", "/")
                }
            }
        }

        val entries = mutableListOf<Entry>()
        chartPoints.forEachIndexed { index, point ->
            metricValue(point)?.let { value -> entries.add(Entry(index.toFloat(), value)) }
        }

        val dataSet = LineDataSet(entries, metricLabel()).apply {
            color = metricColor()
            lineWidth = 2.5f
            setDrawValues(false)
            setDrawCircles(true)
            circleRadius = 3.5f
            circleHoleRadius = 1.8f
            setCircleColor(metricColor())
            highLightColor = ContextCompat.getColor(this@ReportActivity, R.color.zk_text_secondary)
            mode = LineDataSet.Mode.LINEAR
        }

        lineChart.xAxis.apply {
            valueFormatter = IndexAxisValueFormatter(labels)
            axisMinimum = -0.2f
            axisMaximum = (chartPoints.size - 1).toFloat() + 0.2f
            labelCount = labels.size.coerceAtMost(6)
        }

        lineChart.axisLeft.removeAllLimitLines()
        lineChart.axisLeft.addLimitLine(
            LimitLine(metricGoal(report), t("目标", "Goal")).apply {
                lineColor = ContextCompat.getColor(this@ReportActivity, R.color.zk_progress_level_green)
                lineWidth = 1.5f
                textColor = ContextCompat.getColor(this@ReportActivity, R.color.zk_progress_level_green)
                textSize = 11f
                enableDashedLine(10f, 8f, 0f)
            }
        )

        tvGoalHint.text = t("目标横线", "Goal line") + ": ${metricGoal(report).roundToInt()} ${metricUnit()}"

        lineChart.data = LineData(dataSet)
        lineChart.invalidate()
        lineChart.animateX(300)

        val latest = report.points.firstOrNull { metricValue(it) != null }
        if (latest == null) {
            tvLatestSummary.text = t("当前区间暂无摄入记录", "No intake records in this period.")
        } else {
            tvLatestSummary.text =
                t("热量", "Calories") + " ${latest.caloriesKcal?.roundToInt() ?: 0} kcal  " +
                t("蛋白质", "Protein") + " ${latest.proteinG?.roundToInt() ?: 0} g  " +
                t("脂肪", "Fat") + " ${latest.fatG?.roundToInt() ?: 0} g  " +
                t("碳水", "Carbs") + " ${latest.carbG?.roundToInt() ?: 0} g"
        }
    }

    private fun metricValue(point: ReportSeriesPoint): Float? {
        return when (currentMetric) {
            Metric.CALORIES -> point.caloriesKcal
            Metric.PROTEIN -> point.proteinG
            Metric.FAT -> point.fatG
            Metric.CARB -> point.carbG
        }
    }

    private fun metricGoal(report: UserReportResponse): Float {
        return when (currentMetric) {
            Metric.CALORIES -> report.goalLine.caloriesKcal
            Metric.PROTEIN -> report.goalLine.proteinG
            Metric.FAT -> report.goalLine.fatG
            Metric.CARB -> report.goalLine.carbG
        }
    }

    private fun metricUnit(): String {
        return when (currentMetric) {
            Metric.CALORIES -> "kcal"
            Metric.PROTEIN, Metric.FAT, Metric.CARB -> "g"
        }
    }

    private fun metricLabel(): String {
        return when (currentMetric) {
            Metric.CALORIES -> t("热量", "Calories")
            Metric.PROTEIN -> t("蛋白质", "Protein")
            Metric.FAT -> t("脂肪", "Fat")
            Metric.CARB -> t("碳水", "Carbs")
        }
    }

    private fun metricColor(): Int {
        return when (currentMetric) {
            Metric.CALORIES -> ContextCompat.getColor(this, R.color.zk_nutrient_calories)
            Metric.PROTEIN -> ContextCompat.getColor(this, R.color.zk_nutrient_protein)
            Metric.FAT -> ContextCompat.getColor(this, R.color.zk_nutrient_fat)
            Metric.CARB -> ContextCompat.getColor(this, R.color.zk_nutrient_carbs)
        }
    }

    private fun loadBitelog() {
        val userId = currentUserId ?: return
        val periodParam = if (bitelogSubPeriod == BitelogPeriod.WEEKLY) "weekly" else "monthly"
        tvBitelogRange.text = t("加载中...", "Loading...")
        tvBitelogEmpty.visibility = View.GONE
        bitelogGrid.removeAllViews()

        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                runCatching {
                    authApiService.getDietMap(userId, periodParam, bitelogOffset)
                }.getOrNull()
            }

            if (response?.isSuccessful != true || response.body() == null) {
                tvBitelogRange.text = t("加载失败", "Failed to load")
                toast(t("饮食足迹加载失败", "Bitelog loading failed"))
                return@launch
            }

            renderBitelog(response.body()!!)
        }
    }

    private fun renderBitelog(data: DietMapResponse) {
        bitelogGrid.removeAllViews()
        if (data.days.isEmpty()) {
            tvBitelogRange.text = t("本周期暂无数据", "No data in this period")
            tvBitelogEmpty.visibility = View.VISIBLE
            return
        }

        val formatter = if (isEnglishUi) {
            DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
        } else {
            DateTimeFormatter.ofPattern("M月d日")
        }
        val firstDate = LocalDate.parse(data.days.first().businessDay)
        val lastDate = LocalDate.parse(data.days.last().businessDay)
        val rangeLabel = when (data.period) {
            "weekly" -> if (isEnglishUi) {
                "This week ${firstDate.format(formatter)} - ${lastDate.format(formatter)}"
            } else {
                "本周 ${firstDate.format(formatter)} 至 ${lastDate.format(formatter)}"
            }
            else -> if (isEnglishUi) {
                firstDate.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
            } else {
                "${firstDate.year}年${firstDate.monthValue}月"
            }
        }
        tvBitelogRange.text = when {
            data.offset == 0 -> rangeLabel
            data.offset < 0 -> if (isEnglishUi) "$rangeLabel (${(-data.offset)} earlier)" else "$rangeLabel（往前 ${-data.offset}）"
            else -> if (isEnglishUi) "$rangeLabel (${data.offset} later)" else "$rangeLabel（往后 ${data.offset}）"
        }

        val showMonthNumbers = data.period == "monthly"
        bitelogWeekdayHeader.visibility = if (showMonthNumbers) View.GONE else View.VISIBLE

        val daysWithLeading = if (showMonthNumbers) {
            // 月视图按 ISO 周一对齐，先补前置空白。
            val leading = (data.days.first().weekday - 1).coerceAtLeast(0)
            List(leading) { null } + data.days
        } else {
            data.days.toList()
        }

        val columnWidth = 0
        val weight = 1f
        val density = resources.displayMetrics.density

        daysWithLeading.forEach { day ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (8 * density).toInt())
                layoutParams = GridLayout.LayoutParams().apply {
                    width = columnWidth
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, weight)
                    setGravity(Gravity.TOP)
                }
            }

            if (showMonthNumbers) {
                val dayLabel = TextView(this).apply {
                    text = day?.let { LocalDate.parse(it.businessDay).dayOfMonth.toString() } ?: ""
                    textSize = 11f
                    setTextColor(ContextCompat.getColor(this@ReportActivity, R.color.zk_text_secondary))
                    gravity = Gravity.CENTER
                }
                cell.addView(dayLabel)
            }

            val intakes = day?.intakes.orEmpty()
            if (intakes.isEmpty()) {
                cell.addView(createEmptyCircle(density))
            } else {
                intakes.forEach { intake ->
                    cell.addView(createIntakeThumbnail(intake, density))
                }
            }

            bitelogGrid.addView(cell)
        }

        val totalIntakes = data.days.sumOf { it.intakes.size }
        tvBitelogEmpty.visibility = if (totalIntakes == 0) View.VISIBLE else View.GONE
    }

    private fun createEmptyCircle(density: Float): View {
        val size = (44 * density).toInt()
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                topMargin = (4 * density).toInt()
            }
            background = ContextCompat.getDrawable(
                this@ReportActivity,
                R.drawable.bg_bitelog_empty_circle
            )
        }
    }

    private fun createIntakeThumbnail(intake: DietMapIntakeItem, density: Float): ImageView {
        val size = (44 * density).toInt()
        return ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                topMargin = (4 * density).toInt()
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = ContextCompat.getDrawable(
                this@ReportActivity,
                R.drawable.bg_bitelog_empty_circle
            )
            val absoluteUrl = intake.imageUrl?.let { BackendUrls.absolutize(it) }
            if (absoluteUrl != null) {
                load(absoluteUrl)
            } else {
                setColorFilter(Color.TRANSPARENT)
            }
            setOnClickListener { showIntakeDetail(intake, absoluteUrl) }
        }
    }

    private fun showIntakeDetail(intake: DietMapIntakeItem, absoluteImageUrl: String?) {
        val density = resources.displayMetrics.density
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(
                (20 * density).toInt(),
                (8 * density).toInt(),
                (20 * density).toInt(),
                (8 * density).toInt()
            )
        }

        val imageSize = (160 * density).toInt()
        val imageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(imageSize, imageSize)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = ContextCompat.getDrawable(
                this@ReportActivity,
                R.drawable.bg_bitelog_empty_circle
            )
            if (absoluteImageUrl != null) {
                load(absoluteImageUrl)
            }
        }
        container.addView(imageView)

        val nutritionText = TextView(this).apply {
            val caloriesText = "${intake.caloriesKcal.roundToInt()} kcal"
            val proteinText = "${intake.proteinG.roundToInt()} g"
            val fatText = "${intake.fatG.roundToInt()} g"
            val carbText = "${intake.carbG.roundToInt()} g"
            text =
                t("热量", "Calories") + " $caloriesText\n" +
                t("蛋白质", "Protein") + " $proteinText  " +
                t("脂肪", "Fat") + " $fatText  " +
                t("碳水", "Carbs") + " $carbText"
            setTextColor(ContextCompat.getColor(this@ReportActivity, R.color.zk_text_secondary))
            gravity = Gravity.CENTER
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (12 * density).toInt()
            }
        }
        container.addView(nutritionText)

        val timeText = TextView(this).apply {
            text = intake.intakeTime.replace("T", " ").substringBefore("+").substringBefore(".")
            setTextColor(ContextCompat.getColor(this@ReportActivity, R.color.zk_text_secondary))
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (6 * density).toInt()
            }
        }
        container.addView(timeText)

        MaterialAlertDialogBuilder(this)
            .setTitle(intake.foodName)
            .setView(container)
            .setPositiveButton(t("关闭", "Close"), null)
            .show()
    }

    private fun applyLanguageTexts() {
        btnBack.contentDescription = t("返回", "Back")
        tvPageTitle.text = t("饮食报表", "Nutrition Report")
        tvPageSubtitle.text = t("查看日报、周报、月报营养趋势", "View daily, weekly, and monthly nutrition trends")
        togglePeriod.findViewById<TextView>(R.id.btnPeriodDaily).text = t("日报", "Day")
        togglePeriod.findViewById<TextView>(R.id.btnPeriodWeekly).text = t("周报", "Week")
        togglePeriod.findViewById<TextView>(R.id.btnPeriodMonthly).text = t("月报", "Month")
        togglePeriod.findViewById<TextView>(R.id.btnPeriodBitelog).text = t("饮食足迹", "Bitelog")
        chipGroupMetric.findViewById<TextView>(R.id.chipCalories).text = t("热量", "Calories")
        chipGroupMetric.findViewById<TextView>(R.id.chipProtein).text = t("蛋白质", "Protein")
        chipGroupMetric.findViewById<TextView>(R.id.chipFat).text = t("脂肪", "Fat")
        chipGroupMetric.findViewById<TextView>(R.id.chipCarb).text = t("碳水", "Carbs")
        tvLatestSummaryTitle.text = t("最新一天结构", "Latest Day Summary")
        tvLatestSummary.text = t(
            "热量 0 kcal  蛋白质 0 g  脂肪 0 g  碳水 0 g",
            "Calories 0 kcal  Protein 0 g  Fat 0 g  Carbs 0 g"
        )
        toggleBitelogPeriod.findViewById<TextView>(R.id.btnBitelogWeekly).text = t("周", "Week")
        toggleBitelogPeriod.findViewById<TextView>(R.id.btnBitelogMonthly).text = t("月", "Month")
        btnBitelogPrev.contentDescription = t("上一期", "Previous")
        btnBitelogNext.contentDescription = t("下一期", "Next")
        tvWeekdayMon.text = if (isEnglishUi) "Mon" else "一"
        tvWeekdayTue.text = if (isEnglishUi) "Tue" else "二"
        tvWeekdayWed.text = if (isEnglishUi) "Wed" else "三"
        tvWeekdayThu.text = if (isEnglishUi) "Thu" else "四"
        tvWeekdayFri.text = if (isEnglishUi) "Fri" else "五"
        tvWeekdaySat.text = if (isEnglishUi) "Sat" else "六"
        tvWeekdaySun.text = if (isEnglishUi) "Sun" else "日"
        tvBitelogEmpty.text = t("本周期暂无进食记录", "No intake records in this period")
        lineChart.setNoDataText(t("暂无可展示数据", "No data to display"))

        navHomeLabel.text = t("首页", "Home")
        navRecommendLabel.text = t("推荐", "Suggest")
        navReportLabel.text = t("报表", "Report")
        navProfileLabel.text = t("我的", "Me")
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun t(zh: String, en: String): String = if (isEnglishUi) en else zh

    private fun showState(message: String, forceVisible: Boolean) {
        if (!forceVisible && message.isBlank()) {
            tvReportState.visibility = View.GONE
            return
        }
        tvReportState.visibility = View.VISIBLE
        tvReportState.text = message
        tvReportState.setTextColor(
            if (forceVisible) ContextCompat.getColor(this, R.color.zk_error)
            else ContextCompat.getColor(this, R.color.zk_text_secondary)
        )
    }
}
