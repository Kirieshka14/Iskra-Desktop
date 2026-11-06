import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.0"
}

group = "ai.iskra"
version = "1.0.0"

kotlin {
    jvmToolchain(17)
    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("io.ktor:ktor-client-core:2.3.12")
                implementation("io.ktor:ktor-client-cio:2.3.12")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "ai.iskra.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.AppImage, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Dmg)
            packageName = "iskra"
            packageVersion = "1.0.0"
            description = "IskraAi Native Desktop Client"
            vendor = "IskraAi"

            linux {
                iconFile.set(project.file("build/icon.png"))
                debMaintainer = "support@iskraai.run.place"
                menuGroup = "Network"
            }
            windows {
                iconFile.set(project.file("build/icon.ico"))
                menuGroup = "IskraAi"
                shortcut = true
            }
            macOS {
                bundleID = "ai.iskra.desktop"
            }
        }
    }
}
