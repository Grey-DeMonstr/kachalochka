import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktlint)
}

// A release build names its version; every other build, the web deployed from master among them,
// shows the newest one in the changelog, which a release commit puts on top before its tag.
val appVersion: Provider<String> =
    providers.gradleProperty("versionName").orElse(
        providers
            .fileContents(rootProject.layout.projectDirectory.file("changelog.txt"))
            .asText
            .map { text -> text.lines().first { Regex("""\d+\.\d+\.\d+""").matches(it) } },
    )

val generateAppVersion =
    tasks.register("generateAppVersion") {
        val outputDir = layout.buildDirectory.dir("generated/appVersion")
        val version = appVersion
        inputs.property("version", version)
        outputs.dir(outputDir)

        doLast {
            val packageDir = outputDir.get().asFile.resolve("monster/greyde/kachalochka")
            packageDir.mkdirs()
            packageDir.resolve("AppVersion.kt").writeText(
                """
                package monster.greyde.kachalochka

                object AppVersion {
                    const val NAME: String = "${version.get()}"
                }

                """.trimIndent(),
            )
        }
    }

kotlin {
    jvmToolchain(21)

    android {
        namespace = "monster.greyde.kachalochka.app"
        compileSdk =
            libs.versions.androidCompileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.androidMinSdk
                .get()
                .toInt()
    }
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateAppVersion)
        }
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
            implementation(libs.navigationevent.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.vico.multiplatform)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
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
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services.auth)
            implementation(libs.google.id)
            implementation(libs.androidx.work.runtime.ktx)
            implementation(project.dependencies.platform(libs.supabase.bom))
            implementation(libs.supabase.auth)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
            implementation(project.dependencies.platform(libs.supabase.bom))
            implementation(libs.supabase.auth)
        }
    }
}

// LegalPagesTest reads the web pages, so an edited page must rerun it.
tasks.named<Test>("jvmTest") {
    inputs
        .dir("src/wasmJsMain/resources")
        .withPropertyName("webResources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
