package ai.iskra.desktop

import ai.iskra.desktop.ui.IskraDesktopApp
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.skia.Image
import java.awt.Dimension
import java.awt.Toolkit

fun main() = application {
    val screenSize = runCatching { Toolkit.getDefaultToolkit().screenSize }.getOrNull()
    val defaultWidth = if (screenSize != null) (screenSize.width * 0.82).toInt().coerceIn(980, 1240) else 1160
    val defaultHeight = if (screenSize != null) (screenSize.height * 0.82).toInt().coerceIn(620, 760) else 720

    val windowState = rememberWindowState(
        size = DpSize(defaultWidth.dp, defaultHeight.dp),
        position = WindowPosition(Alignment.Center)
    )
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
        window.minimumSize = Dimension(880, 560)
        IskraDesktopApp()
    }
}
