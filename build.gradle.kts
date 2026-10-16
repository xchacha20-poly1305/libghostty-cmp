plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.maven.publish) apply false
}

val kotlinVersion = libs.versions.kotlin.get()

allprojects {
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    // AGP 9.4.1 resolves its built-in Kotlin from these two configurations at 2.2.10; raising them
    // requires kotlin.compiler.runViaBuildToolsApi, which lifts the plugin/compiler version check.
    configurations.matching { it.name in setOf("kotlinCompilerClasspath", "kotlinBuildToolsApiClasspath") }
        .configureEach {
            resolutionStrategy.eachDependency {
                if (requested.group == "org.jetbrains.kotlin") {
                    useVersion(kotlinVersion)
                }
            }
        }
}

subprojects {
    plugins.withId("com.android.library") {
        extensions.configure<com.android.build.api.dsl.LibraryExtension> {
            compileSdk = 37
            defaultConfig {
                minSdk = 21
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension> {
            explicitApi()
        }
        dependencies {
            add("api", libs.kotlin.stdlib)
        }
    }
}
