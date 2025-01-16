//用于管理和配置 Gradle 插件 的版本和插件仓库
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral() // Maven 官方仓库
        gradlePluginPortal() // 用于访问 Gradle 插件
        maven("https://jitpack.io")// 用于访问 JitPack 仓库

    }
}
//这个配置块主要涉及到非插件的依赖，如库、模块等。在 Gradle 构建系统中，依赖项（例如第三方库）会从仓库中下载并集成到构建过程中。
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://www.jitpack.io" ) }
        maven { url = uri("https://oss.sonatype.org/content/repositories/ksoap2-android-releases/" ) }

    }
}

rootProject.name = "Squirrel"
include(":app")
