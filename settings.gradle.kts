pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
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
        google()
        mavenCentral()
    }
}

rootProject.name = "JustPlay"
include(":Employ_JustPlay")
project(":Employ_JustPlay").projectDir = file("Employ_JustPlay/app")
include(":AdminJustPlay")
project(":AdminJustPlay").projectDir = file("AdminJustPlay/app")
