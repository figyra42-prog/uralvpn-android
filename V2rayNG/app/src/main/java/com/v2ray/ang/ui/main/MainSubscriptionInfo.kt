package com.v2ray.ang.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.handler.SubscriptionInfoParser
import com.v2ray.ang.util.Utils

private const val MILLIS_PER_SECOND = 1000L
private const val MILLIS_PER_DAY = 86_400_000L
private const val EXPIRY_DATE_PATTERN = "yyyy-MM-dd"
private const val NO_EXPIRY_SYMBOL = "∞"

/** Locale-neutral values the subscription information box renders. */
internal data class SubscriptionInfoUiModel(
    val title: String?,
    val usedBytes: Long,
    /** Traffic quota in bytes; 0 means unlimited. */
    val totalBytes: Long,
    /** Bytes downloaded. */
    val downloadBytes: Long,
    /** Bytes uploaded. */
    val uploadBytes: Long,
    /** Used share of the quota in 0..1; null when the quota is unlimited. */
    val trafficFraction: Float?,
    /** Expiry in epoch milliseconds; null when the subscription never expires. */
    val expireMillis: Long?,
    val expired: Boolean,
    /** Whole days until expiry; null when the subscription never expires. */
    val remainingDays: Long? = null,
    val supportUrl: String?,
    val webPageUrl: String?,
    /** Panel status message (Remnawave `announce` header), may be multi-line. */
    val announce: String?,
) {
    val trafficLow: Boolean get() = trafficFraction != null && trafficFraction >= TRAFFIC_WARNING_FRACTION

    companion object {
        const val TRAFFIC_WARNING_FRACTION = 0.9f
    }
}

internal fun buildSubscriptionInfoUiModel(
    info: SubscriptionUsageInfo,
    nowMillis: Long,
): SubscriptionInfoUiModel {
    val unlimited = info.total <= 0L
    val expireMillis = info.expire.takeIf { it > 0L }?.let { it * MILLIS_PER_SECOND }
    return SubscriptionInfoUiModel(
        title = info.title,
        usedBytes = info.used,
        totalBytes = if (unlimited) 0L else info.total,
        downloadBytes = info.download,
        uploadBytes = info.upload,
        trafficFraction = if (unlimited) null else (info.used.toDouble() / info.total).toFloat().coerceIn(0f, 1f),
        expireMillis = expireMillis,
        expired = expireMillis != null && expireMillis <= nowMillis,
        remainingDays = expireMillis?.let {
            ((it - nowMillis).coerceAtLeast(0L) + MILLIS_PER_DAY - 1) / MILLIS_PER_DAY
        },
        supportUrl = info.supportUrl,
        webPageUrl = info.webPageUrl,
        announce = info.announce,
    )
}

/**
 * Fixed box on top of a subscription group showing the account information published by the
 * subscription panel: traffic, expiry date and shortcuts (support link, panel page, update).
 */
@Composable
internal fun SubscriptionInfoCard(
    info: SubscriptionUsageInfo,
    fallbackTitle: String,
    onOpenLink: (String) -> Unit,
    onRefresh: () -> Unit,
    onPing: () -> Unit,
    sortByPing: Boolean,
    onToggleSortByPing: () -> Unit,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val model = remember(info) { buildSubscriptionInfoUiModel(info, System.currentTimeMillis()) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, raisedRim()),
        shadowElevation = 18.dp,
    ) {
        Column(
            Modifier
                .background(raisedBrush(MaterialTheme.colorScheme.surfaceContainerHighest))
                .padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onToggleCollapse,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_expand_more_24dp),
                        stringResource(if (collapsed) R.string.sub_info_expand else R.string.sub_info_collapse),
                        Modifier
                            .size(20.dp)
                            .rotate(if (collapsed) 0f else 180f),
                    )
                }
                Text(
                    text = model.title ?: fallbackTitle,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                model.supportUrl?.let { url ->
                    CardIconButton(
                        iconRes = if (SubscriptionInfoParser.isTelegramLink(url)) {
                            R.drawable.ic_telegram_24dp
                        } else {
                            R.drawable.ic_feedback_24dp
                        },
                        contentDescription = stringResource(R.string.sub_info_open_support),
                        onClick = { onOpenLink(url) },
                    )
                }
                model.webPageUrl?.let { url ->
                    CardIconButton(
                        iconRes = R.drawable.ic_about_24dp,
                        contentDescription = stringResource(R.string.sub_info_open_page),
                        onClick = { onOpenLink(url) },
                    )
                }
                CardIconButton(
                    iconRes = R.drawable.ic_refresh_24dp,
                    contentDescription = stringResource(R.string.title_sub_update),
                    onClick = onRefresh,
                )
                CardIconButton(
                    iconRes = R.drawable.ic_flash_on_24dp,
                    contentDescription = stringResource(R.string.title_real_ping_all_server),
                    onClick = onPing,
                )
                IconButton(
                    onClick = onToggleSortByPing,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_sort_24dp),
                        stringResource(R.string.sub_info_sort_by_ping),
                        Modifier.size(20.dp),
                        tint = if (sortByPing) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // One semantics node for the read-only figures so TalkBack reads them in one go.
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp)
                    .semantics(mergeDescendants = true) {}
            ) {
                StatsRow(model)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(
                            if (model.expired) R.string.sub_info_status_expired
                            else R.string.sub_info_status_active
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (model.expired) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    if (!model.expired) {
                        model.remainingDays?.let { days ->
                            Text(
                                text = stringResource(R.string.sub_info_remaining, days),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
                model.trafficFraction?.let { fraction ->
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .clearAndSetSemantics {},
                        color = if (model.trafficLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
                model.announce?.let { announce ->
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = announce,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// Volume of the subscription card: light top edge, darker bottom.
@Composable
private fun raisedRim(): Brush =
    Brush.verticalGradient(listOf(Color(0x59FFFFFF), Color(0x66000000)))

private fun raisedBrush(base: Color): Brush =
    Brush.verticalGradient(
        listOf(
            Color(
                red = base.red + (1f - base.red) * 0.12f,
                green = base.green + (1f - base.green) * 0.12f,
                blue = base.blue + (1f - base.blue) * 0.12f,
                alpha = base.alpha,
            ),
            Color(red = base.red * 0.88f, green = base.green * 0.88f, blue = base.blue * 0.88f, alpha = base.alpha),
        ),
    )

@Composable
private fun CardIconButton(iconRes: Int, contentDescription: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(painterResource(iconRes), contentDescription, Modifier.size(20.dp))
    }
}

@Composable
private fun StatsRow(model: SubscriptionInfoUiModel) {
    val totalText = if (model.totalBytes > 0L) {
        model.totalBytes.toTrafficString()
    } else {
        NO_EXPIRY_SYMBOL
    }
    // Figures and the slash between them always read left to right, also in right-to-left languages;
    // otherwise the bidi algorithm swaps "used / total" around the slash.
    val figuresStyle = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = stringResource(R.string.sub_info_traffic, model.usedBytes.toTrafficString(), totalText),
            modifier = Modifier.weight(1f, fill = false),
            style = figuresStyle,
            color = if (model.trafficLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            // No expiry date means the subscription never expires.
            text = model.expireMillis?.let {
                stringResource(R.string.sub_info_expiry, Utils.formatTimestamp(it, EXPIRY_DATE_PATTERN))
            } ?: stringResource(R.string.sub_info_expiry, NO_EXPIRY_SYMBOL),
            style = figuresStyle,
            color = if (model.expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
