plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        register("ghosttyAssets") {
            id = "ghostty.assets"
            implementationClass = "GhosttyAssetsPlugin"
        }
        register("ghosttyNative") {
            id = "ghostty.native"
            implementationClass = "GhosttyNativePlugin"
        }
    }
}
