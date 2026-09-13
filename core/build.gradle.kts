import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.ktlint)
}

fun localOrEnv(name: String): String {
    val properties = Properties()
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(properties::load)
    return properties.getProperty(name) ?: System.getenv(name) ?: ""
}

val generateSupabaseConfig by tasks.registering {
    val url = localOrEnv("SUPABASE_URL")
    val anonKey = localOrEnv("SUPABASE_ANON_KEY")
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
                const val URL: String = "$url"
                const val ANON_KEY: String = "$anonKey"
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
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
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
