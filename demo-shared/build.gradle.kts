import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    android { namespace = "io.github.xchacha20_poly1305.libghostty.demo.shared" }
    jvm("desktop")
    js {
        browser()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }
    sourceSets {
        commonMain.dependencies { api(project(":library")) }
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}
