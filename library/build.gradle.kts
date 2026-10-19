plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.maven.publish)
    id("ghostty.native")
}

val ghosttyNative = the<GhosttyNative>()
// Only the sysroot is used: zig links the JNI library against bionic.
ghosttyNative.ndkHome.set(androidComponents.sdkComponents.sdkDirectory.map { it.dir("ndk/${libs.versions.ndk.get()}") })

kotlin {
    android {
        namespace = "io.github.xchacha20_poly1305.libghostty"
        @Suppress("UnstableApiUsage")
        optimization {
            consumerKeepRules.apply {
                publish = true
                file("consumer-rules.pro")
            }
        }
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }
    jvm("desktop")

    sourceSets {
        commonMain {
            kotlin.srcDir(ghosttyNative.kotlin.flatMap { it.outputDir })
            dependencies {
                api(libs.kotlinx.coroutines.core)
            }
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
        }
        named("desktopMain") {
            resources.srcDir(ghosttyNative.desktopJni.flatMap { it.outputDir })
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
