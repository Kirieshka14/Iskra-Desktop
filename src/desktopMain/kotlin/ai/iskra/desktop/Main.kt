package ai.iskra.desktop

import ai.iskra.desktop.ui.IskraDesktopApp
import ai.iskra.desktop.ui.IskraTheme
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.skia.Image

fun main() = application {
    val windowState = rememberWindowState(width = 1280.dp, height = 840.dp)
    val iconPainter = runCatching {
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")
            ?: Thread.currentThread().contextClassLoader.getResourceAsStream("logo-spark.png")
        stream?.use { bytes ->
            BitmapPainter(Image.makeFromEncoded(bytes.readBytes()).toComposeImageBitmap())
        }
    }.getOrNull()

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "IskraAi — истории, которые отвечают",
        icon = iconPainter
    ) {
        IskraDesktopApp()
    }
}
