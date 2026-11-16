package com.stopthephone.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 白底大圆角分组卡片。
 */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(content = content)
    }
}

/**
 * 分组卡片里的一行。
 */
@Composable
fun GroupRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke(this)
    }
}

@Composable
fun GroupDivider(inset: Boolean = false) {
    HorizontalDivider(
        modifier = Modifier.padding(start = if (inset) 74.dp else 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * 分组上方的小标题。
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp).semantics { heading() },
    )
}

/**
 * 滑块式分段选择，selected 为 null 表示都不选中。
 */
@Composable
fun SegmentedControl(options: List<String>, selected: Int?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val thumb = if (dark) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerLowest
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(3.dp),
    ) {
        options.forEachIndexed { index, option ->
            val on = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) thumb else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(index) })
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 圆形开关，用于星期选择。
 */
@Composable
fun CircleToggle(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, description: String) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
            color = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun StatusPill(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/**
 * 灰底圆形图标按钮。
 */
@Composable
fun CircleIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

/**
 * 懒加载列表里的分组卡片片段，首尾两项带圆角。
 */
@Composable
fun GroupItem(index: Int, count: Int, content: @Composable ColumnScope.() -> Unit) {
    val radius = 16.dp
    val shape = RoundedCornerShape(
        topStart = if (index == 0) radius else 0.dp,
        topEnd = if (index == 0) radius else 0.dp,
        bottomStart = if (index == count - 1) radius else 0.dp,
        bottomEnd = if (index == count - 1) radius else 0.dp,
    )
    Surface(Modifier.fillMaxWidth(), shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(content = content)
    }
}

/**
 * 统计数字，数字用给定字号，单位缩小。
 */
@Composable
fun MetricText(value: String, style: TextStyle, color: Color = Color.Unspecified) {
    val unitSize = style.fontSize * 0.55f
    val text = buildAnnotatedString {
        value.forEach { char ->
            if (char.isDigit() || char == '–' || char == '<') {
                append(char)
            } else {
                withStyle(SpanStyle(fontSize = unitSize)) { append(char) }
            }
        }
    }
    Text(text, style = style, color = color)
}
