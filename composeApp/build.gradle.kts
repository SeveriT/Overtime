import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvm()

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        all {
            languageSettings.optIn("androidx.compose.material3.ExperimentalMaterial3Api")
            languageSettings.optIn("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.material.icons.extended)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.cio)
            implementation(libs.vlcj)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

compose.desktop {
    application {
        mainClass = "fi.severi.overtime.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            // jdk.accessibility isn't found by suggestRuntimeModules, but without it the app exits
            // silently at startup since the material3 Expressive upgrade.
            modules("java.instrument", "java.management", "jdk.unsupported", "java.xml", "jdk.accessibility")
            packageName = "Overtime"
            packageVersion = "0.1.0"
            description = "IPTV player"
            vendor = "Severi"
            windows {
                // Regenerate with: python tools/make_icon.py
                iconFile.set(project.file("icons/overtime.ico"))
                menu = true
                shortcut = true
                upgradeUuid = "6f3a1c2e-0b7d-4a5e-9c41-2d8e7f1b3a90"
            }
        }
    }
}
