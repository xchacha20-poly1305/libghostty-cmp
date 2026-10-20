pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "libghostty-cmp"
include(":library")
include(":library-extras")
include(":library-compose")
include(":shrink-check")
include(":demo-desktop")
include(":demo-android")
