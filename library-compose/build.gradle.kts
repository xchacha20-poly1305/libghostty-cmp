plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.maven.publish)
}

kotlin {
    android {
        namespace = "io.github.xchacha20_poly1305.libghostty.compose"
        minSdk = 23
        androidResources {
            enable = true
        }
    }
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            api(project(":library"))
            api(libs.compose.runtime)
            api(libs.compose.ui)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.components.resources)
        }
        named("desktopTest").dependencies {
            implementation(libs.kotlin.test)
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.resources {
    packageOfResClass = "io.github.xchacha20_poly1305.libghostty.compose.resources"
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
}
