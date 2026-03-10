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

        // Resolve libadb and its transitive JitPack deps.
        exclusiveContent {
            forRepository {
                maven("https://jitpack.io") {
                    metadataSources {
                        mavenPom()
                    }
                }
            }
            filter {
                includeGroup("com.github.MuntashirAkon")
                includeGroup("com.github.MuntashirAkon.spake2-java")
            }
        }
    }
}

rootProject.name = "Hyflash"

include(":app")
