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

/**
 * libVLC is bundled so Overtime works without VLC installed: copied from the VLC 3 (64-bit) on the
 * build machine (`-PvlcDir=...` to use another), minus its own Qt interface. VLC's plugin cache records
 * file times, which zipping and copying change, so none is shipped: VLC writes it on the first start.
 */
val vlcSource = providers.gradleProperty("vlcDir").orElse("C:/Program Files/VideoLAN/VLC")
val bundledVlcRoot = layout.buildDirectory.dir("vlc-resources")
val bundleVlc by tasks.registering(Sync::class) {
    val source = vlcSource.get()
    from(source) {
        include("libvlc.dll", "libvlccore.dll", "COPYING.txt", "README.txt", "plugins/**")
        exclude("plugins/gui/**", "plugins/plugins.dat")
    }
    into(bundledVlcRoot.map { it.dir("windows/vlc") })
    doFirst { require(File(source, "libvlc.dll").isFile) { "VLC 3 (64-bit) not found in $source; pass -PvlcDir=..." } }
}

compose.desktop {
    application {
        mainClass = "fi.severi.overtime.MainKt"

        nativeDistributions {
            appResourcesRootDir.set(bundledVlcRoot)
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            // jdk.accessibility isn't found by suggestRuntimeModules, but without it the app exits
            // silently at startup since the material3 Expressive upgrade.
            modules("java.instrument", "java.management", "jdk.unsupported", "java.xml", "jdk.accessibility")
            packageName = "Overtime"
            packageVersion = "0.10.0"
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

tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(bundleVlc) }
