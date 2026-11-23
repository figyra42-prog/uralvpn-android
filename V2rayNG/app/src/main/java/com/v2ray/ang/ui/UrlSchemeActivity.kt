package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.handler.UralDeepLink
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UrlSchemeActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val target = when (intent?.action) {
            Intent.ACTION_VIEW -> UralDeepLink.fromScheme(
                intent.data?.host,
                intent.data?.getQueryParameter("url"),
                intent.data?.fragment
            )

            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    UralDeepLink.fromShared(intent.getStringExtra(Intent.EXTRA_TEXT))
                } else {
                    null
                }
            }

            else -> null
        }

        if (target == null) {
            LogUtil.w(AppConfig.TAG, "UrlSchemeActivity: unsupported link")
            toastError(R.string.import_subscription_failure)
            openMain()
            return
        }

        importTarget(target)
    }

    private fun importTarget(target: UralDeepLink.Target) {
        val url = when (target) {
            is UralDeepLink.Target.Subscription -> target.url
            is UralDeepLink.Target.Config -> appendFragment(target.url, target.fragment)
        }
        val subscriptionName = if (target is UralDeepLink.Target.Subscription) "UralVPN" else null

        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                val (count, countSub) = AngConfigManager.importBatchConfig(
                    url,
                    "",
                    false,
                    subscriptionName
                )
                val imported = count + countSub > 0
                val alreadyPresent = target is UralDeepLink.Target.Subscription &&
                        countSub == 0 &&
                        MmkvManager.decodeSubscriptions().any { it.subscription.url == url }
                if (target is UralDeepLink.Target.Subscription && (imported || alreadyPresent)) {
                    SubscriptionUpdater.sync(applicationContext)
                }
                imported || alreadyPresent
            }

            if (success) {
                toast(R.string.import_subscription_success)
            } else {
                toastError(R.string.import_subscription_failure)
            }
            openMain()
        }
    }

    private fun appendFragment(url: String, fragment: String?): String =
        if (!fragment.isNullOrEmpty() && !url.contains('#')) "$url#$fragment" else url

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    @Composable
    override fun ScreenContent() {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}
