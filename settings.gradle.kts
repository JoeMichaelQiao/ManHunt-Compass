pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven { url = uri("https://maven.fabricmc.net/") } // 用于 fabric-loom 插件
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url = uri("https://maven.fabricmc.net/") } // 用于 fabric 依赖
    }
}

rootProject.name = "manhunt-compass"
