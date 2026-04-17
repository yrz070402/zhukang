package com.example.zhukang

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.zhukang.api.AuthApiService
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class ReportActivity : AppCompatActivity() {

    private enum class Period { DAILY, WEEKLY, MONTHLY }
    private enum class Metric { CALORIES, PROTEIN, FAT, CARB }

    private val authApiService by lazy { AuthApiService.create() }

    private lateinit var btnBack: ImageButton
    private lateinit var togglePeriod: MaterialButtonToggleGroup
    private lateinit var chipGroupMetric: ChipGroup
    private lateinit var lineChart: LineChart
    private lateinit var tvRange: TextView
    private lateinit var tvGoalHint: TextView
    private lateinit var tvLatestSummary: TextView
    private lateinit var tvReportState: TextView

    private var currentUserId: String? = null
    private var currentPeriod = Period.DAILY
    private var currentMetric = Metric.CALORIES
    private var currentReport: UserReportResponse? = null

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
        chipGroupMetric = findViewById(R.id.chipGroupMetric)
        lineChart = findViewById(R.id.lineChart)
        tvRange = findViewById(R.id.tvRange)
        tvGoalHint = findViewById(R.id.tvGoalHint)
        tvLatestSummary = findViewById(R.id.tvLatestSummary)
        tvReportState = findViewById(R.id.tvReportState)
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
                else -> Period.MONTHLY
            }
            loadReport(currentPeriod)
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
    }

    private fun loadReport(period: Period) {
        val userId = currentUserId ?: return
        showState("报表加载中...", false)

        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                runCatching {
                    when (period) {
                        Period.DAILY -> authApiService.getDailyReport(userId)
                        Period.WEEKLY -> authApiService.getWeeklyReport(userId)
                        Period.MONTHLY -> authApiService.getMonthlyReport(userId)
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
