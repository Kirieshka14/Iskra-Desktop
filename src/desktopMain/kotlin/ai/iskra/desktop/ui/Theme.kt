package ai.iskra.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

data class PaletteOption(
    val id: String,
    val label: String,
    val darkAccent: Color,
    val lightAccent: Color
)

val ISKRA_PALETTES = listOf(
    PaletteOption("classic", "Классика", Color(0xFFF43F5E), Color(0xFFE11D48)),
    PaletteOption("lavender", "Лаванда", Color(0xFFC9B6DF), Color(0xFF7A5FA8)),
    PaletteOption("terracotta", "Терракота", Color(0xFFE8A87C), Color(0xFF9D5626)),
    PaletteOption("blue", "Сапфир", Color(0xFF8EC7EA), Color(0xFF2B6EA8)),
    PaletteOption("emerald", "Изумруд", Color(0xFF6EE7B7), Color(0xFF059669))
)

val GENRE_LABELS = mapOf(
    "all" to "Все",
    "fantasy" to "Фэнтези",
    "romance" to "Романтика",
    "sci_fi" to "Sci-Fi",
    "horror" to "Хоррор",
    "drama" to "Драма",
    "comedy" to "Комедия",
    "slice_of_life" to "Повседневность",
    "historical" to "История",
    "other" to "Другое"
)

val AFFINITY_LABELS = mapOf(
    "hatred" to "Ненависть",
    "distrust" to "Недоверие",
    "neutral" to "Нейтрально",
    "trust" to "Доверие",
    "love" to "Любовь"
)

@Composable
fun IskraTheme(
    themeMode: String = "dark",
    paletteId: String = "classic",
    content: @Composable () -> Unit
) {
    val palette = ISKRA_PALETTES.find { it.id == paletteId } ?: ISKRA_PALETTES.first()
    val isDark = themeMode != "light"

    val scheme = if (isDark) {
        darkColorScheme(
            primary = palette.darkAccent,
            onPrimary = Color(0xFF0D0F12),
            background = Color(0xFF0D0F12),
            surface = Color(0xFF161920),
            surfaceVariant = Color(0xFF1F242D),
            onBackground = Color(0xFFF5F5F4),
            onSurface = Color(0xFFF5F5F4),
            onSurfaceVariant = Color(0xFF9CA3AF),
            outline = Color(0xFF2B303C)
        )
    } else {
        lightColorScheme(
            primary = palette.lightAccent,
            onPrimary = Color.White,
            background = Color(0xFFF8F7F4),
            surface = Color.White,
            surfaceVariant = Color(0xFFF1EFEA),
            onBackground = Color(0xFF18181B),
            onSurface = Color(0xFF18181B),
            onSurfaceVariant = Color(0xFF6B7280),
            outline = Color(0xFFE4E2DD)
        )
    }

    MaterialTheme(
        colorScheme = scheme,
        content = content
    )
}
