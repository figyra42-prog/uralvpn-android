package io.nekohasekai.sfa.bg

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.screen.dashboard.fetchSubInfo
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.TypedProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

// UralVPN: a warning about the end of the subscription. The home screen shows a red line, and this
// worker (every 12 hours, also with the app closed) sends a notification when 3 days or less are left
// (once a day) and once when the subscription has ended.
class UralExpiryWork(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        private const val CHANNEL = "ural_expiry"
        private const val DAY_MS = 86_400_000L

        fun schedule() {
            runCatching {
                val request = PeriodicWorkRequestBuilder<UralExpiryWork>(12, TimeUnit.HOURS).build()
                WorkManager.getInstance(Application.application)
                    .enqueueUniquePeriodicWork("UralExpiry", ExistingPeriodicWorkPolicy.KEEP, request)
            }
        }

        // Whole days left, rounded down like the panel status (0 = less than a day); -1 when the
        // subscription has ended; null when there is no end date.
        fun daysLeft(expireSeconds: Long): Long? {
            if (expireSeconds <= 0) return null
            val left = expireSeconds * 1000 - System.currentTimeMillis()
            if (left <= 0) return -1
            return left / DAY_MS
        }

        fun text(days: Long): String = when {
            days < 0 -> "Подписка закончилась"
            days == 0L -> "Подписка заканчивается меньше чем через сутки"
            else -> "Подписка заканчивается через $days дн"
        }
    }

    override suspend fun doWork(): Result {
        runCatching { check() }
        return Result.success()
    }

    private suspend fun check() {
        val context = applicationContext
        val prefs = context.getSharedPreferences("ural_ui", Context.MODE_PRIVATE)
        val today = System.currentTimeMillis() / DAY_MS
        for (profile in ProfileManager.list()) {
            if (profile.typed.type != TypedProfile.Type.Remote) continue
            val info = withContext(Dispatchers.IO) { fetchSubInfo(profile.typed.remoteURL) } ?: continue
            val days = daysLeft(info.expireSeconds) ?: continue
            if (days > 3) continue
            // once a day while there are 3 days or less, once when it has ended
            val key = "expiry_notified_${profile.id}"
            val mark = if (days < 0) "ended-${info.expireSeconds}" else "day-$today"
            if (prefs.getString(key, "") == mark) continue
            if (notify(context, info.title ?: profile.name, text(days))) {
                prefs.edit().putString(key, mark).apply()
            }
        }
    }

    private fun notify(context: Context, title: String, text: String): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Окончание подписки", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pending = launch?.let {
            android.app.PendingIntent.getActivity(context, 0, it, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text + ". Продлите её в боте.")
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(9001, notification)
        return true
    }
}
