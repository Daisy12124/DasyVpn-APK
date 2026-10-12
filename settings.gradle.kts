pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.aliyun.com/repository/google")
            content {
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("com\\.android(\\..*)?")
                includeGroupByRegex("com\\.google\\.android(\\..*)?")
                includeGroupByRegex("com\\.google\\.(gms|firebase|testing|ar|play)(\\..*)?")
            }
        }
        maven {
            url = uri("https://maven.aliyun.com/repository/gradle-plugin")
            content {
                includeGroupByRegex("com\\.android(\\..*)?")
                includeGroupByRegex("org\\.jetbrains(\\..*)?")
                includeGroupByRegex("org\\.gradle(\\..*)?")
                includeGroupByRegex("com\\.google(\\..*)?")
            }
        }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            url = uri("https://maven.aliyun.com/repository/google")
            content {
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("com\\.android(\\..*)?")
                includeGroupByRegex("com\\.google\\.android(\\..*)?")
                includeGroupByRegex("com\\.google\\.(gms|firebase|testing|ar|play)(\\..*)?")
            }
        }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        google()
        mavenCentral()
    }
}

rootProject.name = "DasyVpn"
include(":app")
