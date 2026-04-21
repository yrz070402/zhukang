package com.example.zhukang

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
import kotlin.math.roundToInt

class ReportActivity : AppCompatActivity() {

    private enum class Period { DAILY, WEEKLY, MONTHLY, BITELOG }
    private enum class Metric { CALORIES, PROTEIN, FAT, CARB }
    private enum class BitelogPeriod { WEEKLY, MONTHLY }

    private val authApiService by lazy { AuthApiService.create() }

    private lateinit var btnBack: ImageButton
    private lateinit var togglePeriod: MaterialButtonToggleGroup
    private lateinit var metricScrollContainer: HorizontalScrollView
    private lateinit var chipGroupMetric: ChipGroup
    private lateinit var cardChart: CardView
    private lateinit var cardLatestSummary: CardView
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
    private lateinit var tvBitelogEmpty: TextView

    private var currentUserId: String? = null
    private var currentPeriod = Period.DAILY
    private var currentMetric = Metric.CALORIES
    private var currentReport: UserReportResponse? = null
    private var bitelogSubPeriod = BitelogPeriod.WEEKLY
    private var bitelogOffset = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_report)

        currentUserId = intent.getStringExtra("user_id")
        initViews()
        initChart()
        bindEvents()

        if (currentUserId.isNullOrBlank()) {
            showState("缺少用户信息，请返回首页重试", true)
            return
        }

        togglePeriod.check(R.id.btnPeriodDaily)
        chipGroupMetric.check(R.id.chipCalories)
        loadReport(currentPeriod)
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        togglePeriod = findViewById(R.id.togglePeriod)
        metricScrollContainer = findViewById(R.id.metricScrollContainer)
        chipGroupMetric = findViewById(R.id.chipGroupMetric)
        cardChart = findViewById(R.id.cardChart)
        cardLatestSummary = findViewById(R.id.cardLatestSummary)
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
        tvBitelogEmpty = findViewById(R.id.tvBitelogEmpty)
    }

    private fun initChart() {
        lineChart.setNoDataText("暂无可展示数据")
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
                Toast.makeText(this, "已到最新周期", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            bitelogOffset += 1
            loadBitelog()
        }
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
        showState("报表加载中...", false)

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
                showState("加载失败，请稍后重试", true)
                Toast.makeText(this@ReportActivity, "报表接口请求失败", Toast.LENGTH_SHORT).show()
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

        tvRange.text = "数据范围：${report.startAt} - ${report.endAt}"

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
            LimitLine(metricGoal(report), "目标").apply {
                lineColor = ContextCompat.getColor(this@ReportActivity, R.color.zk_progress_level_green)
                lineWidth = 1.5f
                textColor = ContextCompat.getColor(this@ReportActivity, R.color.zk_progress_level_green)
                textSize = 11f
                enableDashedLine(10f, 8f, 0f)
            }
        )

        tvGoalHint.text = "目标横线：${metricGoal(report).roundToInt()} ${metricUnit()}"

        lineChart.data = LineData(dataSet)
        lineChart.invalidate()
        lineChart.animateX(300)

        val latest = report.points.firstOrNull { metricValue(it) != null }
        if (latest == null) {
            tvLatestSummary.text = "当前区间暂无摄入记录"
        } else {
            tvLatestSummary.text = "热量 ${latest.caloriesKcal?.roundToInt() ?: 0} kcal  蛋白质 ${latest.proteinG?.roundToInt() ?: 0} g  脂肪 ${latest.fatG?.roundToInt() ?: 0} g  碳水 ${latest.carbG?.roundToInt() ?: 0} g"
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
            Metric.CALORIES -> "热量"
            Metric.PROTEIN -> "蛋白质"
            Metric.FAT -> "脂肪"
            Metric.CARB -> "碳水"
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
        tvBitelogRange.text = "加载中..."
        tvBitelogEmpty.visibility = View.GONE
        bitelogGrid.removeAllViews()

        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                runCatching {
                    authApiService.getDietMap(userId, periodParam, bitelogOffset)
                }.getOrNull()
            }

            if (response?.isSuccessful != true || response.body() == null) {
                tvBitelogRange.text = "加载失败"
                Toast.makeText(this@ReportActivity, "Bitelog 加载失败", Toast.LENGTH_SHORT).show()
                return@launch
            }

            renderBitelog(response.body()!!)
        }
    }

    private fun renderBitelog(data: DietMapResponse) {
        bitelogGrid.removeAllViews()

        val formatter = DateTimeFormatter.ofPattern("M月d日")
        val firstDate = LocalDate.parse(data.days.first().businessDay)
        val lastDate = LocalDate.parse(data.days.last().businessDay)
        val rangeLabel = when (data.period) {
            "weekly" -> "本周 ${firstDate.format(formatter)} 至 ${lastDate.format(formatter)}"
            else -> "${firstDate.year}年${firstDate.monthValue}月"
        }
        tvBitelogRange.text = when {
            data.offset == 0 -> rangeLabel
            data.offset < 0 -> "$rangeLabel（往前 ${-data.offset}）"
            else -> "$rangeLabel（往后 ${data.offset}）"
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
            text = "热量 $caloriesText\n蛋白质 $proteinText  脂肪 $fatText  碳水 $carbText"
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
            .setPositiveButton("关闭", null)
            .show()
    }

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
