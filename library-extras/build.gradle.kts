plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "io.github.xchacha20_poly1305.libghostty.extras"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        getByName("main") {
            assets.directories.add(layout.buildDirectory.dir("ghostty-assets").get().asFile.absolutePath)
        }
    }
}

dependencies {
    api(project(":library"))

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}

apply<GhosttyAssetsPlugin>()

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    pom {
        properties.set(mapOf("ghostty.commit" to ghosttyCommit(project)))
    }
}
