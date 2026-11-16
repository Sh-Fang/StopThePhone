package com.stopthephone.ui.common

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch

private const val VISIBLE_ITEMS = 5
private const val LOOPS = 1000
val WHEEL_ITEM_HEIGHT: Dp = 40.dp

/**
 * 循环滚轮，取值范围 0 until count，选中带由调用方绘制。
 */
@Composable
fun WheelPicker(
    count: Int,
    value: Int,
    onValueChange: (Int) -> Unit,
    label: (Int) -> String,
    name: String,
    modifier: Modifier = Modifier,
    width: Dp = 64.dp,
) {
    val half = VISIBLE_ITEMS / 2
    val state = rememberLazyListState(initialFirstVisibleItemIndex = count * LOOPS / 2 + value - half)
    val scope = rememberCoroutineScope()
    val center by remember {
        derivedStateOf {
            // 滚过半格算下一格
            val offset = if (state.firstVisibleItemScrollOffset > state.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.div(2) ?: 0) 1 else 0
            state.firstVisibleItemIndex + offset + half
        }
    }
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(state) {
        snapshotFlow { center % count }.distinctUntilChanged().collect { currentOnValueChange(it) }
    }
    val step = { delta: Int ->
        scope.launch { state.animateScrollToItem(center - half + delta) }
        true
    }
    Box(
        modifier
            .width(width)
            .height(WHEEL_ITEM_HEIGHT * VISIBLE_ITEMS)
            .clearAndSetSemantics {
                contentDescription = "$name ${label(value)}"
                customActions = listOf(
                    CustomAccessibilityAction("加一") { step(1) },
                    CustomAccessibilityAction("减一") { step(-1) },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        LazyColumn(state = state, flingBehavior = rememberSnapFlingBehavior(state), modifier = Modifier.fillMaxWidth()) {
            items(count * LOOPS) { index ->
                Box(Modifier.fillMaxWidth().height(WHEEL_ITEM_HEIGHT), contentAlignment = Alignment.Center) {
                    Text(
                        label(index % count),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.alpha(if (index == center) 1f else 0.4f),
                    )
                }
            }
        }
    }
}
