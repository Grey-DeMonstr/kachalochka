import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kotlinSerialization)
}

private fun localOrEnv(name: String): String {
    val properties = Properties()
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(properties::load)
    return properties.getProperty(name) ?: System.getenv(name) ?: ""
}

// Backslash must go first, or it re-escapes the backslashes just inserted for the other chars.
private fun escapeKotlinString(value: String): String =
    value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("$", "\\$")

val generateSupabaseConfig by tasks.registering {
    val url = localOrEnv("SUPABASE_URL")
    val anonKey = localOrEnv("SUPABASE_ANON_KEY")
    val escapedUrl = escapeKotlinString(url)
    val escapedAnonKey = escapeKotlinString(anonKey)
    val outputDir = layout.buildDirectory.dir("generated/supabase")

    inputs.property("url", url)
    inputs.property("anonKey", anonKey)
    outputs.dir(outputDir)

    doLast {
        val packageDir =
            outputDir
                .get()
                .asFile
                .resolve("monster/greyde/kachalochka/core/data/supabase")
        packageDir.mkdirs()
        packageDir.resolve("SupabaseConfig.kt").writeText(
            """
            package monster.greyde.kachalochka.core.data.supabase

            internal object SupabaseConfig {
                const val URL: String = "$escapedUrl"
                const val ANON_KEY: String = "$escapedAnonKey"
            }

            """.trimIndent(),
        )
    }
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

    applyDefaultHierarchyTemplate {
        common {
            group("sql") {
                withAndroidTarget()
                withJvm()
            }
        }
    }

    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
    }
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateSupabaseConfig)
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(project.dependencies.platform(libs.supabase.bom))
                implementation(libs.supabase.auth)
                implementation(libs.supabase.postgrest)
                implementation(libs.supabase.storage)
                implementation(libs.supabase.realtime)
                implementation(libs.koin.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.koin.test)
        }
        val sqlMain by getting {
            dependencies {
                implementation(libs.sqldelight.runtime)
                implementation(libs.sqldelight.coroutines)
            }
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.driver.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.koin.android)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.driver.jvm)
            implementation(libs.ktor.client.okhttp)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
    }
}

android {
    namespace = "monster.greyde.kachalochka.core"
    compileSdk =
        libs.versions.androidCompileSdk
            .get()
            .toInt()
    defaultConfig {
        minSdk =
            libs.versions.androidMinSdk
                .get()
                .toInt()
    }
}

sqldelight {
    databases {
        create("KachalochkaDatabase") {
            packageName.set("monster.greyde.kachalochka.core.data.db")
            srcDirs.setFrom("src/sqlMain/sqldelight")
        }
    }
}

// The SQLDelight plugin attaches its output to commonMain from an afterEvaluate of its own, so
// this block has to be registered after the sqldelight { } block to run second.
afterEvaluate {
    kotlin.sourceSets.named("commonMain") {
        kotlin.exclude { it.file.path.contains("generated${File.separator}sqldelight") }
    }
    kotlin.sourceSets.named("sqlMain") {
        kotlin.srcDir(tasks.named("generateCommonMainKachalochkaDatabaseInterface"))
    }
}
