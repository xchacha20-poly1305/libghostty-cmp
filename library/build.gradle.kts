plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "io.github.xchacha20_poly1305.libghostty"

    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += "-DGHOSTTY_VT_DIR=${layout.buildDirectory.dir("ghostty-vt").get().asFile.absolutePath}"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}

apply<GhosttyNativePlugin>()

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    pom {
        properties.set(mapOf("ghostty.commit" to ghosttyCommit(project)))
    }
}
