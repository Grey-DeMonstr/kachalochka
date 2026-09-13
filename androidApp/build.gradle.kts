plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ktlint)
}

kotlin { jvmToolchain(21) }

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
        versionCode = 1
        versionName = "0.1.0"
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

dependencies {
    implementation(project(":app"))
    // KachalochkaApplication builds the Koin graph, so core's module is on this compile classpath
    // in its own right.
    implementation(project(":core"))
    implementation(compose.runtime)
    implementation(compose.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
}
