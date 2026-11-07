package ai.iskra.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class IskraColorTokens(
    val canvas: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val text: Color,
    val muted: Color,
    val subtle: Color,
    val accent: Color,
    val accentHover: Color,
    val accentInk: Color,
    val accentSoft: Color,
    val border: Color,
    val borderStrong: Color,
    val danger: Color,
    val success: Color,
    val isDark: Boolean,
    val palette: String
)

fun iskraTokensFor(palette: String, isDark: Boolean): IskraColorTokens {
    return if (isDark) {
        when (palette) {
            "lavender" -> IskraColorTokens(
                canvas = Color(0xFF12101B),
                surface = Color(0xFF1A1726),
                surface2 = Color(0xFF231F33),
                surface3 = Color(0xFF2E2942),
                text = Color(0xFFF3F0FF),
                muted = Color(0xFFA59EC4),
                subtle = Color(0xFF6E6790),
                accent = Color(0xFFA78BFA),
                accentHover = Color(0xFFC4B5FD),
                accentInk = Color(0xFF12101B),
                accentSoft = Color(0x2EA78BFA),
                border = Color(0x1AF3F0FF),
                borderStrong = Color(0x2EF3F0FF),
                danger = Color(0xFFF87171),
                success = Color(0xFF4ADE80),
                isDark = true,
                palette = palette
            )
            "terracotta" -> IskraColorTokens(
                canvas = Color(0xFF181210),
                surface = Color(0xFF221A17),
                surface2 = Color(0xFF2D221E),
                surface3 = Color(0xFF3A2C27),
                text = Color(0xFFFAF1EC),
                muted = Color(0xFFB8A196),
                subtle = Color(0xFF826C62),
                accent = Color(0xFFEA7A57),
                accentHover = Color(0xFFF29A7E),
                accentInk = Color(0xFF181210),
                accentSoft = Color(0x2EEA7A57),
                border = Color(0x1AFAF1EC),
                borderStrong = Color(0x2EFAF1EC),
                danger = Color(0xFFF87171),
                success = Color(0xFF4ADE80),
                isDark = true,
                palette = palette
            )
            "blue" -> IskraColorTokens(
                canvas = Color(0xFF0E131B),
                surface = Color(0xFF151C28),
                surface2 = Color(0xFF1D2636),
                surface3 = Color(0xFF273247),
                text = Color(0xFFEEF4FF),
                muted = Color(0xFF94A6C4),
                subtle = Color(0xFF607291),
                accent = Color(0xFF60A5FA),
                accentHover = Color(0xFF93C5FD),
                accentInk = Color(0xFF0E131B),
                accentSoft = Color(0x2E60A5FA),
                border = Color(0x1AEEF4FF),
                borderStrong = Color(0x2EEEF4FF),
                danger = Color(0xFFF87171),
                success = Color(0xFF4ADE80),
                isDark = true,
                palette = palette
            )
            "emerald" -> IskraColorTokens(
                canvas = Color(0xFF0E1613),
                surface = Color(0xFF14201C),
                surface2 = Color(0xFF1B2B25),
                surface3 = Color(0xFF243831),
                text = Color(0xFFECFDF5),
                muted = Color(0xFF92B5A6),
                subtle = Color(0xFF5D8273),
                accent = Color(0xFF34D399),
                accentHover = Color(0xFF6EE7B7),
                accentInk = Color(0xFF0E1613),
                accentSoft = Color(0x2E34D399),
                border = Color(0x1AECFDF5),
                borderStrong = Color(0x2EECFDF5),
                danger = Color(0xFFF87171),
                success = Color(0xFF4ADE80),
                isDark = true,
                palette = palette
            )
            else -> IskraColorTokens(
                canvas = Color(0xFF131313),
                surface = Color(0xFF1A1A1A),
                surface2 = Color(0xFF232323),
                surface3 = Color(0xFF2E2E2E),
                text = Color(0xFFF5F5F4),
                muted = Color(0xFFA3A3A3),
                subtle = Color(0xFF6E6E6E),
                accent = Color(0xFFF5F5F4),
                accentHover = Color(0xFFFFFFFF),
                accentInk = Color(0xFF141414),
                accentSoft = Color(0x1FF5F5F4),
                border = Color(0x17FFFFFF),
                borderStrong = Color(0x29FFFFFF),
                danger = Color(0xFFF87171),
                success = Color(0xFF4ADE80),
                isDark = true,
                palette = "classic"
            )
        }
    } else {
        when (palette) {
            "lavender" -> IskraColorTokens(
                canvas = Color(0xFFF8F6FF),
                surface = Color(0xFFFFFFFF),
                surface2 = Color(0xFFF1EDFF),
                surface3 = Color(0xFFE5DEFF),
                text = Color(0xFF1E1930),
                muted = Color(0xFF655D82),
                subtle = Color(0xFF968EB5),
                accent = Color(0xFF6D4AFF),
                accentHover = Color(0xFF5835EB),
                accentInk = Color(0xFFFFFFFF),
                accentSoft = Color(0x1F6D4AFF),
                border = Color(0x1A1E1930),
                borderStrong = Color(0x2E1E1930),
                danger = Color(0xFFDC2626),
                success = Color(0xFF16A34A),
                isDark = false,
                palette = palette
            )
            "terracotta" -> IskraColorTokens(
                canvas = Color(0xFFFBF6F3),
                surface = Color(0xFFFFFFFF),
                surface2 = Color(0xFFF4EAE4),
                surface3 = Color(0xFFEAD9CF),
                text = Color(0xFF261914),
                muted = Color(0xFF755C52),
                subtle = Color(0xFFA68B80),
                accent = Color(0xFFC85A38),
                accentHover = Color(0xFFB04828),
                accentInk = Color(0xFFFFFFFF),
                accentSoft = Color(0x1FC85A38),
                border = Color(0x1A261914),
                borderStrong = Color(0x2E261914),
                danger = Color(0xFFDC2626),
                success = Color(0xFF16A34A),
                isDark = false,
                palette = palette
            )
            "blue" -> IskraColorTokens(
                canvas = Color(0xFFF4F8FF),
                surface = Color(0xFFFFFFFF),
                surface2 = Color(0xFFE8F0FE),
                surface3 = Color(0xFFD6E4FF),
                text = Color(0xFF121C2D),
                muted = Color(0xFF526480),
                subtle = Color(0xFF8596B2),
                accent = Color(0xFF2563EB),
                accentHover = Color(0xFF1D4ED8),
                accentInk = Color(0xFFFFFFFF),
                accentSoft = Color(0x1F2563EB),
                border = Color(0x1A121C2D),
                borderStrong = Color(0x2E121C2D),
                danger = Color(0xFFDC2626),
                success = Color(0xFF16A34A),
                isDark = false,
                palette = palette
            )
            "emerald" -> IskraColorTokens(
                canvas = Color(0xFFF2FBF7),
                surface = Color(0xFFFFFFFF),
                surface2 = Color(0xFFE3F5ED),
                surface3 = Color(0xFFD0EBE0),
                text = Color(0xFF10241C),
                muted = Color(0xFF4A6E60),
                subtle = Color(0xFF7CA192),
                accent = Color(0xFF059669),
                accentHover = Color(0xFF047857),
                accentInk = Color(0xFFFFFFFF),
                accentSoft = Color(0x1F059669),
                border = Color(0x1A10241C),
                borderStrong = Color(0x2E10241C),
                danger = Color(0xFFDC2626),
                success = Color(0xFF16A34A),
                isDark = false,
                palette = palette
            )
            else -> IskraColorTokens(
                canvas = Color(0xFFF7F6F3),
                surface = Color(0xFFFFFFFF),
                surface2 = Color(0xFFF0EEEA),
                surface3 = Color(0xFFE5E2DC),
                text = Color(0xFF181816),
                muted = Color(0xFF6B6A65),
                subtle = Color(0xFF9C9A93),
                accent = Color(0xFF181816),
                accentHover = Color(0xFF000000),
                accentInk = Color(0xFFFAF9F7),
                accentSoft = Color(0x14181816),
                border = Color(0x1A181816),
                borderStrong = Color(0x2E181816),
                danger = Color(0xFFDC2626),
                success = Color(0xFF16A34A),
                isDark = false,
                palette = "classic"
            )
        }
    }
}

val LocalIskraColors = staticCompositionLocalOf { iskraTokensFor("classic", true) }

val IskraColors: IskraColorTokens
    @Composable
    get() = LocalIskraColors.current

private val IskraTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

@Composable
fun IskraTheme(
    palette: String = "classic",
    isDark: Boolean = true,
    content: @Composable () -> Unit
) {
    val tokens = iskraTokensFor(palette, isDark)
    val scheme = if (isDark) {
        darkColorScheme(
            primary = tokens.accent,
            onPrimary = tokens.accentInk,
            secondary = tokens.accentHover,
            background = tokens.canvas,
            surface = tokens.surface,
            surfaceVariant = tokens.surface2,
            onBackground = tokens.text,
            onSurface = tokens.text,
            onSurfaceVariant = tokens.muted,
            outline = tokens.border,
            error = tokens.danger
        )
    } else {
        lightColorScheme(
            primary = tokens.accent,
            onPrimary = tokens.accentInk,
            secondary = tokens.accentHover,
            background = tokens.canvas,
            surface = tokens.surface,
            surfaceVariant = tokens.surface2,
            onBackground = tokens.text,
            onSurface = tokens.text,
            onSurfaceVariant = tokens.muted,
            outline = tokens.border,
            error = tokens.danger
        )
    }

    CompositionLocalProvider(LocalIskraColors provides tokens) {
        MaterialTheme(
            colorScheme = scheme,
            typography = IskraTypography,
            content = content
        )
    }
}
