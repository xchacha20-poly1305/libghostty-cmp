import org.gradle.jvm.tasks.Jar
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.dokka.gradle.engine.plugins.DokkaHtmlPluginParameters
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.dokka)
}

dependencies {
    dokka(project(":library"))
    dokka(project(":library-extras"))
    dokka(project(":library-compose"))
}

dokka {
    moduleName.set("libghostty-cmp")
    moduleVersion.set(providers.gradleProperty("VERSION_NAME"))
    dokkaPublications.html {
        includes.from("docs/quick-start.md")
    }
}

val dokkaLayout = layout.projectDirectory.dir("docs/dokka")
allprojects {
    plugins.withId("org.jetbrains.dokka") {
        extensions.configure<DokkaExtension> {
            pluginsConfiguration.named("html", DokkaHtmlPluginParameters::class.java) {
                templatesDir.set(dokkaLayout.dir("templates"))
                customStyleSheets.from(dokkaLayout.file("sidebar.css"))
            }
        }
    }
    plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin> {
        extensions.configure<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec> {
            download.set(false)
            command.set("node")
        }
    }
    // Executable tasks use a project-scoped Node environment, separate from Wasm's root tooling setup.
    plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin> {
        extensions.configure<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec> {
            download.set(false)
            command.set("node")
        }
    }
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
            explicitApi()
            compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
            targets.withType<org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget>().configureEach {
                compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
            }
            targets.withType<com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget>().configureEach {
                compileSdk = 37
                minSdk = 21
                compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }
    plugins.withId("com.vanniktech.maven.publish") {
        val projectLicense = rootProject.layout.projectDirectory.file("LICENSE")
        val ghosttyLicense = ghosttyLicense(project)
        tasks.withType<AbstractArchiveTask>().configureEach {
            if (this is Jar || name.endsWith("Aar")) {
                from(projectLicense) {
                    into("META-INF")
                }
                from(ghosttyLicense) {
                    into("META-INF")
                }
            }
        }
    }
}

// Use the Node installation supplied by the developer or CI, without adding a distribution repository.
plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsRootPlugin> {
    extensions.configure<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec> {
        download.set(false)
    }
}

// Kotlin's bundled browser tooling currently includes a Git-hosted Karma dependency.
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinToolingSetupTask>().configureEach {
    args.add("--allow-git=all")
}

plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin> {
    extensions.configure<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec> {
        download.set(false)
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinNpmInstallTask>().configureEach {
    args.add("--allow-git=all")
}
