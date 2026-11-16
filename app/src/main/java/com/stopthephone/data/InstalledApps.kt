package com.stopthephone.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

data class InstalledApp(val packageName: String, val label: String)

/**
 * 查询可管控的已安装应用：有桌面图标、非系统应用、不含本应用。
 */
class InstalledApps(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val iconCache = LruCache<String, Bitmap>(ICON_CACHE_SIZE)

    suspend fun list(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = Collator.getInstance(Locale.CHINA)
        packageManager.queryIntentActivities(launcherIntent, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.packageName != context.packageName }
            .map { InstalledApp(it.packageName, it.loadLabel(packageManager).toString()) }
            .sortedWith(compareBy(collator) { it.label })
    }

    suspend fun icon(packageName: String): Bitmap? = withContext(Dispatchers.IO) {
        iconCache.get(packageName) ?: runCatching {
            packageManager.getApplicationIcon(packageName).toBitmap(ICON_SIZE_PX, ICON_SIZE_PX)
        }.getOrNull()?.also { iconCache.put(packageName, it) }
    }

    companion object {
        private const val ICON_CACHE_SIZE = 64
        private const val ICON_SIZE_PX = 144
    }
}
