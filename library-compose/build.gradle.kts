plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "io.github.xchacha20_poly1305.libghostty.compose"

    defaultConfig {
        minSdk = 23
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    api(project(":library"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
}
