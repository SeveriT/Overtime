package fi.severi.overtime.ui

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.SystemFont

@OptIn(ExperimentalTextApi::class)
internal actual fun systemFontFamily(name: String): FontFamily =
    FontFamily(SystemFont(name, FontWeight.Normal), SystemFont(name, FontWeight.Bold))
