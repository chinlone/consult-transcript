package com.xiaodong.consulttranscript.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 咨询场景用低饱和的安静配色，不刺眼；绿色只用于「全程不联网」这类安心提示
private val CalmPrimary = Color(0xFF3F6C63)
private val CalmSecondary = Color(0xFF6B7F77)
private val OfflineGreen = Color(0xFF2E7D5B)

private val LightColors = lightColorScheme(
    primary = CalmPrimary,
    secondary = CalmSecondary,
    tertiary = OfflineGreen,
    background = Color(0xFFF7F8F6),
    surface = Color.White
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC4B7),
    secondary = CalmSecondary,
    tertiary = OfflineGreen
)

@Composable
fun ConsultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}

/** 界面上统一用这个颜色标注「离线／已删除／安全」类信息 */
val offlineGreen: Color get() = OfflineGreen
