import org.jetbrains.dokka.gradle.tasks.DokkaGeneratePublicationTask
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

val ghosttyWasm = configurations.create("ghosttyWasm") { isCanBeConsumed = false }
dependencies {
    add(ghosttyWasm.name, project(mapOf("path" to ":library", "configuration" to "ghosttyWasmBinary")))
}
val copyGhosttyWasm = tasks.register<Sync>("copyGhosttyWasm") {
    from(ghosttyWasm)
    into(layout.buildDirectory.dir("ghostty-wasm"))
}

kotlin {
    js {
        browser()
        binaries.executable()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("web") {
                withJs()
                withWasmJs()
            }
        }
    }
    sourceSets {
        named("webMain").dependencies {
            implementation(libs.kotlinx.browser)
        }
        commonMain.dependencies {
            implementation(project(":demo-shared"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.components.resources)
        }
        named("webMain") {
            resources.srcDir(copyGhosttyWasm)
        }
        named("webMain").dependencies {
            implementation(project(":library-compose"))
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
        }
    }
}

compose.resources {
    packageOfResClass = "io.github.xchacha20_poly1305.libghostty.demo.resources"
}

val offlinePackages = listOf("js" to "js", "wasm" to "wasmJs").associate { (name, target) ->
    val title = name.replaceFirstChar { it.uppercase() }
    val directory = layout.buildDirectory.dir("offline/$name")
    val skiko = layout.buildDirectory.file("compose/skiko-for-web-runtime/skiko.wasm")
    val pack = tasks.register<Exec>("package${title}Offline") {
        dependsOn("${target}BrowserDistribution")
        val distribution = layout.buildDirectory.dir("dist/$target/productionExecutable")
        inputs.dir(distribution)
        inputs.files("scripts/package-offline.py", "scripts/offline-runtime.js")
        inputs.file(skiko)
        inputs.property("target", name)
        outputs.dir(directory)
        workingDir(rootProject.projectDir)
        commandLine(
            "python3", project.file("scripts/package-offline.py").absolutePath,
            distribution.get().asFile.absolutePath, directory.get().asFile.absolutePath,
            "--skiko", skiko.get().asFile.absolutePath,
            "--target", name,
        )
    }
    val zip = tasks.register<Zip>("${name}OfflineZip") {
        dependsOn(pack)
        from(directory)
        archiveFileName.set(providers.gradleProperty("VERSION_NAME").map {
            "demo-web-$name-$it.zip"
        })
        destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    }
    name to (pack to zip)
}

tasks.register("offlineZip") {
    group = "distribution"
    description = "Build both standalone Kotlin/JS and Kotlin/Wasm offline ZIPs."
    dependsOn(offlinePackages.values.map { it.second })
}

val dokkaHtml = rootProject.tasks.named<DokkaGeneratePublicationTask>("dokkaGeneratePublicationHtml")
tasks.register<Sync>("preparePages") {
    group = "distribution"
    description = "Assemble the API documentation and browser demos for GitHub Pages."
    from(dokkaHtml.flatMap { it.outputDirectory })
    offlinePackages.forEach { (name, tasks) ->
        from(tasks.first) {
            into("demo/$name")
            filesMatching("index.html") {
                filter { line ->
                    line.replace("<html lang=\"en\"", "<html lang=\"en\" data-pages-docs=\"../../index.html\"")
                }
            }
        }
    }
    into(layout.buildDirectory.dir("pages"))
}
