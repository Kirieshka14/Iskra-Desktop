import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.0"
}

group = "ai.iskra"
version = "0.8.2"

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
                implementation("io.ktor:ktor-client-okhttp:2.3.12")
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
            includeAllModules = true
            targetFormats(TargetFormat.Deb, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Dmg)
            packageName = "iskra"
            packageVersion = "1.8.3"
            description = "IskraAi Desktop Client"
            vendor = "IskraAi"

            linux {
                iconFile.set(project.file("icons/icon.png"))
                debMaintainer = "support@iskraai.run.place"
                menuGroup = "Network"
            }
            windows {
                iconFile.set(project.file("icons/icon.ico"))
                menuGroup = "IskraAi"
                shortcut = true
            }
            macOS {
                bundleID = "ai.iskra.desktop"
            }
        }
    }
}
