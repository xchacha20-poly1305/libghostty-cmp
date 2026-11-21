plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dokka)
    id("ghostty.assets")
}

val ghosttyAssets = the<GhosttyAssets>()

kotlin {
    android {
        namespace = "io.github.xchacha20_poly1305.libghostty.extras"
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }
    jvm("desktop")

    sourceSets {
        commonMain {
            resources.srcDir(ghosttyAssets.resourceDir)
            dependencies {
                api(project(":library"))
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

dokka {
    moduleName.set("libghostty-extras")
    moduleVersion.set(providers.gradleProperty("VERSION_NAME"))
    val desktopClasspath = configurations.named("desktopCompileClasspath")
    dokkaSourceSets.configureEach {
        // These shared sources target the JVM on both Android and desktop.
        if (name == "commonMain") classpath.from(desktopClasspath)
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    pom {
        properties.set(mapOf("ghostty.commit" to ghosttyCommit(project)))
    }
}
