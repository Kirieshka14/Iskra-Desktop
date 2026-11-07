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
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    // Ensure _JAVA_AWT_WM_NONREPARENTING=1 is active BEFORE AWT initializes on Linux tiling/Wayland WMs (Hyprland, Sway, i3, etc.)
    val osName = System.getProperty("os.name").orEmpty()
    if (osName.contains("Linux", ignoreCase = true) &&
        System.getenv("_JAVA_AWT_WM_NONREPARENTING") != "1" &&
        System.getenv("ISKRA_AWT_REEXEC") != "1"
    ) {
        val cmd = runCatching { ProcessHandle.current().info().command().orElse(null) }.getOrNull()
        if (cmd != null && !cmd.endsWith("/java")) {
            val relaunched = runCatching {
                val pb = ProcessBuilder(listOf(cmd) + args)
                pb.environment()["_JAVA_AWT_WM_NONREPARENTING"] = "1"
                pb.environment()["AWT_TOOLKIT"] = "XToolkit"
                pb.environment()["ISKRA_AWT_REEXEC"] = "1"
                pb.inheritIO()
                val proc = pb.start()
                exitProcess(proc.waitFor())
            }
            if (relaunched.isSuccess) return
        }
    }

    System.setProperty("awt.useSystemAAFontSettings", "on")
    System.setProperty("swing.aatext", "true")

    application {
        val usableDp = runCatching {
            val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
            val gd = ge.defaultScreenDevice
            val gc = gd.defaultConfiguration
            val tx = gc.defaultTransform
            val scaleX = tx.scaleX.takeIf { it > 0.5 } ?: 1.0
            val scaleY = tx.scaleY.takeIf { it > 0.5 } ?: 1.0
            val maxBounds = ge.maximumWindowBounds
            val screen = Toolkit.getDefaultToolkit().screenSize
            val rawW = if (maxBounds.width > 400) maxBounds.width else screen.width
            val rawH = if (maxBounds.height > 300) maxBounds.height else screen.height
            val dpW = if (rawW > 1600 && scaleX > 1.05) (rawW / scaleX).toInt() else rawW
            val dpH = if (rawH > 950 && scaleY > 1.05) (rawH / scaleY).toInt() else rawH
            dpW to dpH
        }.getOrDefault(1366 to 768)

        val desktopEnv = (
            System.getenv("XDG_CURRENT_DESKTOP").orEmpty() + " " +
                System.getenv("XDG_SESSION_DESKTOP").orEmpty() + " " +
                System.getenv("DESKTOP_SESSION").orEmpty()
            ).lowercase()
        val isTilingWm = listOf("hyprland", "sway", "i3", "bspwm", "dwm", "awesome", "qtile", "niri", "river")
            .any { desktopEnv.contains(it) }

        val (defaultWidth, defaultHeight) = when {
            isTilingWm -> usableDp.first to usableDp.second
            usableDp.first <= 1440 || usableDp.second <= 820 ->
                (usableDp.first - 24).coerceAtLeast(960) to (usableDp.second - 36).coerceAtLeast(620)
            else ->
                (usableDp.first * 0.86).toInt().coerceIn(1120, 1440) to
                    (usableDp.second * 0.86).toInt().coerceIn(700, 900)
        }

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
            resizable = true,
            icon = iconPainter
        ) {
            window.minimumSize = Dimension(640, 420)
            androidx.compose.runtime.DisposableEffect(window) {
                val listener = object : ComponentAdapter() {
                    override fun componentResized(e: ComponentEvent?) {
                        window.contentPane?.setSize(window.width, window.height)
                        window.contentPane?.revalidate()
                        window.revalidate()
                        window.repaint()
                    }

                    override fun componentShown(e: ComponentEvent?) {
                        window.contentPane?.setSize(window.width, window.height)
                        window.contentPane?.revalidate()
                        window.revalidate()
                        window.repaint()
                    }
                }
                window.addComponentListener(listener)
                onDispose { window.removeComponentListener(listener) }
            }
            IskraDesktopApp()
        }
    }
}
