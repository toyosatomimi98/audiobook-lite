package com.toyosatomimi98.pagevoice.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ColorError = androidx.compose.ui.graphics.Color(0xFFA94438)

private val PageVoiceColors = lightColorScheme(
    primary = Terracotta,
    onPrimary = PaperLight,
    primaryContainer = TerracottaSoft,
    onPrimaryContainer = Ink,
    secondary = SageDeep,
    onSecondary = PaperLight,
    secondaryContainer = PaperDeep,
    onSecondaryContainer = Ink,
    tertiary = Ochre,
    onTertiary = Ink,
    background = Paper,
    onBackground = Ink,
    surface = PaperLight,
    onSurface = Ink,
    surfaceVariant = PaperDeep,
    onSurfaceVariant = InkSoft,
    outline = InkFaint.copy(alpha = 0.55f),
    outlineVariant = PaperDeep,
    error = ColorError,
)

private val serif = FontFamily.Serif
private val sans = FontFamily.SansSerif

private val PageVoiceTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = serif,
        fontWeight = FontWeight.Bold,
        fontSize = 38.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = serif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 23.sp,
        lineHeight = 29.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
)

private val PageVoiceShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

@Composable
fun PageVoiceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PageVoiceColors,
        typography = PageVoiceTypography,
        shapes = PageVoiceShapes,
        content = content,
    )
}
