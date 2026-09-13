import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktlint)
}

ktlint {
    filter {
        val generatedDir =
            layout.buildDirectory
                .dir("generated")
                .get()
                .asFile
        exclude { entry -> entry.file.startsWith(generatedDir) }
    }
}

kotlin {
    jvmToolchain(21)

    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
    }
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.navigation.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
            implementation(libs.androidx.datastore.preferences.core)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
        }
    }
}

// KEYSTORE_PATH being set means a release build is expected to be signed, so a missing
// sibling variable must name itself rather than surface as an opaque AGP signing failure.
fun requireSigningEnv(name: String): String =
    System.getenv(name) ?: error("KEYSTORE_PATH is set but $name is missing from the environment.")

android {
    namespace = "monster.greyde.kachalochka"
    compileSdk =
        libs.versions.androidCompileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "monster.greyde.kachalochka"
        minSdk =
            libs.versions.androidMinSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.androidCompileSdk
                .get()
                .toInt()
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = (project.findProperty("versionName") as String?) ?: "0.1.0"
    }

    signingConfigs {
        create("release") {
            val keystore = System.getenv("KEYSTORE_PATH")
            if (keystore != null) {
                storeFile = file(keystore)
                storePassword = requireSigningEnv("KEYSTORE_PASSWORD")
                keyAlias = requireSigningEnv("KEY_ALIAS")
                keyPassword = requireSigningEnv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (System.getenv("KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "monster.greyde.kachalochka.MainKt"
        nativeDistributions { targetFormats(TargetFormat.Exe) }
    }
}
