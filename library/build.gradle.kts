import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.maven.publish)
    id("ghostty.native")
}

val ghosttyNative = the<GhosttyNative>()
// Only the sysroot is used: zig links the JNI library against bionic.
ghosttyNative.ndkHome.set(androidComponents.sdkComponents.sdkDirectory.map { it.dir("ndk/${libs.versions.ndk.get()}") })

val consumerRules = file("consumer-rules.pro")

// The AAR carries consumerKeepRules; a jar carries its rules under META-INF/proguard/,
// where R8 picks them up on its own.
val desktopConsumerRules = tasks.register<Sync>("desktopConsumerRules") {
    from(consumerRules) {
        into("META-INF/proguard")
        rename { "libghostty.pro" }
    }
    into(layout.buildDirectory.dir("generated/consumerRules/desktop"))
}

kotlin {
    android {
        namespace = "io.github.xchacha20_poly1305.libghostty"
        @Suppress("UnstableApiUsage")
        optimization {
            consumerKeepRules.apply {
                publish = true
                file(consumerRules)
            }
        }
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }
    jvm("desktop")
    js {
        browser()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("web") {
                withJs()
                withWasmJs()
            }
            group("jvmShared") {
                withJvm()
                // AGP's KMP Android library target is not the legacy KotlinAndroidTarget.
                withCompilations { it.target.platformType == org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.androidJvm }
            }
        }
    }
    sourceSets {
        named("webMain").dependencies {
            implementation(libs.kotlinx.browser)
        }
        commonMain {
            kotlin.srcDir(ghosttyNative.kotlin.flatMap { it.outputDir })
            dependencies {
                api(libs.kotlinx.coroutines.core)
            }
        }
        named("webMain") {
            resources.srcDir(ghosttyNative.wasm.flatMap {
                it.outputDir
            })
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
        }
        named("desktopMain") {
            resources.srcDir(ghosttyNative.desktopJni.flatMap { it.outputDir })
            resources.srcDir(desktopConsumerRules)
            dependencies {
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        named("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.ext.junit)
        }
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(ghosttyNative.androidJni, GhosttyOutputTask::outputDir)
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    pom {
        properties.set(mapOf("ghostty.commit" to ghosttyCommit(project)))
    }
}

// Explicit binary artifact: browser applications can package it without reaching into another project's tasks.
val ghosttyWasmBinary = configurations.create("ghosttyWasmBinary") {
    isCanBeConsumed = true
    isCanBeResolved = false
}
artifacts.add(ghosttyWasmBinary.name, ghosttyNative.wasm.flatMap { it.outputDir.file("ghostty-vt.wasm") }) {
    builtBy(ghosttyNative.wasm)
}
publishing {
    publications.withType<MavenPublication>().matching { it.name == "wasmJs" || it.name == "js" }.configureEach {
        artifact(ghosttyNative.wasm.flatMap { it.outputDir.file("ghostty-vt.wasm") }) {
            classifier = "ghostty-vt"
            extension = "wasm"
            builtBy(ghosttyNative.wasm)
        }
    }
}
