import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.ktlint) apply false
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        // check must pass on a machine with no browser. Disabling the browser suite is not
        // enough: its place in the graph still schedules Node, npm and both Wasm test
        // compilations. The gate therefore takes the host suites directly rather than the
        // report that aggregates them.
        afterEvaluate {
            tasks.named("check") {
                setDependsOn(
                    dependsOn.filterNot {
                        it is TaskProvider<*> && it.name in setOf("allTests", "wasmJsBrowserTest")
                    },
                )
            }
        }
    }

    plugins.withId("org.jlleitschuh.gradle.ktlint") {
        extensions.configure<KtlintExtension>("ktlint") {
            filter {
                val generatedDir =
                    layout.buildDirectory
                        .dir("generated")
                        .get()
                        .asFile
                exclude { entry -> entry.file.startsWith(generatedDir) }
            }
        }
    }
}
