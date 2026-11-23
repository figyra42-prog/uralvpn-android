package com.v2ray.ang.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.handler.UralRusApps
import com.v2ray.ang.util.LogUtil

/**
 * UralVPN: adds newly installed Russian apps to the per-app bypass list.
 */
class UralPackageReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != Intent.ACTION_PACKAGE_ADDED) return
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return

        val packageName = intent.data?.schemeSpecificPart ?: return
        if (!UralRusApps.addInstalledPackage(context, packageName)) return

        LogUtil.i(AppConfig.TAG, "UralPackageReceiver: added Russian app to bypass: $packageName")
        LauncherManager.restartService(context)
    }
}
