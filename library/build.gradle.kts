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
