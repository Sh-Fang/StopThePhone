package com.stopthephone.ui.common

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stopthephone.StopThePhoneApp

@Composable
fun AppIcon(packageName: String, size: Dp = 40.dp) {
    val installedApps = (LocalContext.current.applicationContext as StopThePhoneApp).installedApps
    val bitmap by produceState<Bitmap?>(null, packageName) { value = installedApps.icon(packageName) }
    val icon = bitmap
    if (icon == null) {
        Box(Modifier.size(size))
    } else {
        Image(icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(size))
    }
}
