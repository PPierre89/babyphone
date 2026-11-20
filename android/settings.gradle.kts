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

rootProject.name = "babyphone"

include(":app")
include(":core:crypto")
include(":core:detection")
include(":core:media")
include(":core:network")
include(":feature:pairing")
include(":feature:baby")
include(":feature:parent")
