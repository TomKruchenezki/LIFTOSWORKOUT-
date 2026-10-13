pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "LiftOS"

include(":domain")

// The Android module needs the Android SDK and Google's Maven repository.
// Pass -Pliftos.domainOnly=true to build/test only the pure-Kotlin :domain module
// on machines that have neither (e.g. restricted sandboxes).
if (providers.gradleProperty("liftos.domainOnly").orNull != "true") {
    include(":app")
}
