plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.xchacha20_poly1305.libghostty.demo"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.xchacha20_poly1305.libghostty.demo"
        minSdk = 23
        targetSdk = 37
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// AGP compiles apps with its built-in Kotlin, older than the libraries' Kotlin; raise it to read their metadata.
// Raising it requires kotlin.compiler.runViaBuildToolsApi, which lifts the plugin/compiler version check.
configurations.matching { it.name in setOf("kotlinCompilerClasspath", "kotlinBuildToolsApiClasspath") }
    .configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") useVersion(libs.versions.kotlin.get())
        }
    }

dependencies {
    implementation(project(":library-compose"))
    implementation(project(":library-extras"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.material3)
}
