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
import java.awt.GraphicsEnvironment
import java.awt.Toolkit

fun main() = application {
    val usableDp = runCatching {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val gd = ge.defaultScreenDevice
        val gc = gd.defaultConfiguration
        val tx = gc.defaultTransform
        val scaleX = tx.scaleX.takeIf { it > 0.5 } ?: 1.0
        val scaleY = tx.scaleY.takeIf { it > 0.5 } ?: 1.0
        val maxBounds = ge.maximumWindowBounds
        val rawW = if (maxBounds.width > 400) maxBounds.width else Toolkit.getDefaultToolkit().screenSize.width
        val rawH = if (maxBounds.height > 300) maxBounds.height else Toolkit.getDefaultToolkit().screenSize.height
        val dpW = if (rawW > 1600 && scaleX > 1.05) (rawW / scaleX).toInt() else rawW
        val dpH = if (rawH > 950 && scaleY > 1.05) (rawH / scaleY).toInt() else rawH
        dpW to dpH
    }.getOrDefault(1280 to 720)

    val defaultWidth = (usableDp.first * 0.84).toInt().coerceIn(880, minOf(1100, (usableDp.first - 48).coerceAtLeast(880)))
    val defaultHeight = (usableDp.second * 0.84).toInt().coerceIn(540, minOf(660, (usableDp.second - 56).coerceAtLeast(540)))

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
        window.minimumSize = Dimension(800, 480)
        IskraDesktopApp()
    }
}
