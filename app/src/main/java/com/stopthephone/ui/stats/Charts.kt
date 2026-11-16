package com.stopthephone.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val HOUR_LABEL_STEP = 6
private const val DIMMED_ALPHA = 0.35f

@Immutable
class ChartColors(val first: Color, val second: Color, val heat: List<Color>)

private val LightColors = ChartColors(
    first = Color(0xFF2A78D6),
    second = Color(0xFFEB6834),
    heat = listOf(0xFFCDE2FB, 0xFF9EC5F4, 0xFF6DA7EC, 0xFF3987E5, 0xFF256ABF, 0xFF184F95).map { Color(it) },
)

private val DarkColors = ChartColors(
    first = Color(0xFF3987E5),
    second = Color(0xFFD95926),
    heat = listOf(0xFF104281, 0xFF184F95, 0xFF256ABF, 0xFF3987E5, 0xFF6DA7EC, 0xFF9EC5F4).map { Color(it) },
)

@Composable
fun chartColors(): ChartColors = if (isSystemInDarkTheme()) DarkColors else LightColors

/**
 * 两段堆叠的柱状图，first 在下、second 在上，点击柱子回调序号。
 */
@Composable
fun StackedBarChart(
    values: List<Pair<Float, Float>>,
    xLabels: List<String?>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = chartColors()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val max = values.maxOfOrNull { it.first + it.second }?.takeIf { it > 0f } ?: 1f
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .semantics { contentDescription = description }
                .pointerInput(values.size) {
                    detectTapGestures { offset ->
                        val index = (offset.x / (size.width.toFloat() / values.size)).toInt().coerceIn(0, values.lastIndex)
                        onSelect(if (index == selected) null else index)
                    }
                },
        ) {
            val slot = size.width / values.size
            val plotHeight = size.height - 20.dp.toPx()
            val barWidth = (slot * 0.6f).coerceAtMost(24.dp.toPx())
            val gap = 2.dp.toPx()
            val radius = (4.dp.toPx()).coerceAtMost(barWidth / 2)
            drawLine(grid, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
            values.forEachIndexed { index, (first, second) ->
                val left = slot * index + (slot - barWidth) / 2
                val firstHeight = plotHeight * first / max
                val secondHeight = plotHeight * second / max
                val alpha = if (selected == null || selected == index) 1f else DIMMED_ALPHA
                val segments = listOf(first to colors.first.copy(alpha = alpha), second to colors.second.copy(alpha = alpha))
                    .zip(listOf(firstHeight, secondHeight))
                    .filter { it.first.first > 0f }
                var bottom = plotHeight
                segments.forEachIndexed { i, (segment, height) ->
                    val top = bottom - height
                    val isTop = i == segments.lastIndex
                    if (isTop) {
                        val corner = CornerRadius(radius.coerceAtMost(height))
                        val shape = RoundRect(Rect(left, top, left + barWidth, top + height), topLeft = corner, topRight = corner)
                        drawPath(Path().apply { addRoundRect(shape) }, segment.second)
                    } else {
                        drawRect(segment.second, Offset(left, top), Size(barWidth, height))
                    }
                    bottom = top - gap
                }
                val label = xLabels.getOrNull(index) ?: return@forEachIndexed
                val text = measurer.measure(label, labelStyle)
                drawText(text, topLeft = Offset(slot * index + (slot - text.size.width) / 2, plotHeight + 4.dp.toPx()))
            }
            drawLine(grid, Offset(0f, plotHeight), Offset(size.width, plotHeight), strokeWidth = 1.dp.toPx())
        }
    }
}

@Composable
fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * 两段占比条，带图例和百分比。
 */
@Composable
fun RatioBar(first: Int, second: Int, firstName: String, secondName: String) {
    val colors = chartColors()
    val total = (first + second).coerceAtLeast(1)
    Column {
        Row(Modifier.fillMaxWidth().height(16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (first > 0) {
                Box(Modifier.weight(first.toFloat()).height(16.dp).background(colors.first, RoundedCornerShape(4.dp)))
            }
            if (second > 0) {
                Box(Modifier.weight(second.toFloat()).height(16.dp).background(colors.second, RoundedCornerShape(4.dp)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val firstPercent = (first * 100f / total).roundToInt()
            LegendItem(colors.first, "$firstName $first · $firstPercent%")
            LegendItem(colors.second, "$secondName $second · ${100 - firstPercent}%")
        }
    }
}

/**
 * 一周 × 24 小时热力图，点击格子回调星期序号和小时。
 */
@Composable
fun Heatmap(
    values: List<List<Int>>,
    dayNames: List<String>,
    selected: Pair<Int, Int>?,
    onSelect: (Pair<Int, Int>?) -> Unit,
    description: String,
) {
    val colors = chartColors()
    val empty = MaterialTheme.colorScheme.surface
    val ring = MaterialTheme.colorScheme.onSurface
    val max = values.maxOf { row -> row.max() }.coerceAtLeast(1)
    val hours = values.first().size
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row {
        Column(Modifier.padding(end = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            dayNames.forEach {
                Box(Modifier.height(16.dp), contentAlignment = Alignment.Center) {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height((18 * values.size + 18).dp)
                    .semantics { contentDescription = description }
                    .pointerInput(values.size) {
                        detectTapGestures { offset ->
                            val day = (offset.y / (18.dp.toPx())).toInt().coerceIn(0, values.lastIndex)
                            val hour = (offset.x / (size.width.toFloat() / hours)).toInt().coerceIn(0, hours - 1)
                            onSelect(if (selected == day to hour) null else day to hour)
                        }
                    },
            ) {
                val gap = 2.dp.toPx()
                val cellWidth = (size.width - gap * (hours - 1)) / hours
                val cellHeight = 16.dp.toPx()
                values.forEachIndexed { day, row ->
                    row.forEachIndexed { hour, count ->
                        val color = if (count == 0) empty else colors.heat[((count - 1) * colors.heat.size / max).coerceAtMost(colors.heat.lastIndex)]
                        val topLeft = Offset(hour * (cellWidth + gap), day * (cellHeight + gap))
                        drawRoundRect(color, topLeft, Size(cellWidth, cellHeight), CornerRadius(2.dp.toPx()))
                        if (selected == day to hour) {
                            drawRoundRect(
                                ring,
                                topLeft,
                                Size(cellWidth, cellHeight),
                                CornerRadius(2.dp.toPx()),
                                style = Stroke(2.dp.toPx()),
                            )
                        }
                    }
                }
                for (hour in 0 until hours step HOUR_LABEL_STEP) {
                    drawText(measurer.measure("$hour", labelStyle), topLeft = Offset(hour * (cellWidth + gap), values.size * (cellHeight + gap)))
                }
            }
        }
    }
}
