package com.local.artifactboost.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.artifactboost.ui.theme.border
import com.local.artifactboost.ui.theme.muted
import com.local.artifactboost.ui.theme.orange
import com.local.artifactboost.ui.theme.strongText
import com.local.artifactboost.ui.theme.surface

/**
 * GitHub 移动端风格的顶部下划线 Tab 栏。
 * 对齐 iOS 版 GitHubTabBar：下划线用动画平滑移动，支持横向滚动。
 */
@Composable
fun <T> GitHubTabBar(
    tabs: List<T>,
    selected: T,
    title: (T) -> String,
    badge: (T) -> String? = { null },
    onSelect: (T) -> Unit,
) {
    Column(modifier = Modifier.background(surface())) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            tabs.forEach { tab ->
                val isSelected = tab == selected
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSelect(tab) }
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            text = title(tab),
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) strongText() else muted(),
                        )
                        badge(tab)?.let { count ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(border().copy(alpha = 0.6f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                            ) {
                                Text(count, fontSize = 10.sp, color = muted())
                            }
                        }
                    }

                    Spacer(Modifier.height(7.dp))

                    // 下划线：选中时显示，用动画过渡宽度
                    val underlineWidth = animateDpAsState(
                        targetValue = if (isSelected) 28.dp else 0.dp,
                        label = "underline",
                    )
                    Box(
                        modifier = Modifier
                            .width(underlineWidth.value)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(orange()),
                    )
                }
            }
        }

        Hairline()
    }
}
