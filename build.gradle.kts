import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
}

group = "dev.catosaurluna"
val releaseVersion = "0.1.3"
version = releaseVersion
description = "Private Kotlin operator dashboard for the Hetzner Dune server."

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha03")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.apache.sshd:sshd-core:2.19.0")
    implementation("org.slf4j:slf4j-nop:2.0.17")
    implementation("net.java.dev.jna:jna:5.19.1")
    implementation("net.java.dev.jna:jna-platform:5.19.1")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

compose.desktop {
    application {
        // Apache SSHD discovers several client services at runtime. Keep the
        // release distribution unshrunk until explicit ProGuard keep rules
        // have been tested against the packaged Windows application.
        buildTypes.release.proguard {
            isEnabled = false
        }
        mainClass = "com.cato.duneadmin.MainKt"
        nativeDistributions {
            modules("java.net.http")
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "CatosDuneAdmin"
            packageVersion = releaseVersion
            description = "Private operator dashboard for the Hetzner Dune server."
            vendor = "catosaurluna"
            windows {
                menuGroup = "CatosDuneAdmin"
                shortcut = true
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
