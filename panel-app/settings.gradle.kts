pluginManagement {
    repositories {
        // KSP 插件 marker 只发布在 Maven Central / Gradle Plugin Portal，
        // Google 与阿里云镜像上均不存在（实测 404），官方源必须排在前面，
        // 否则解析成败全压在最后一个仓库上，CI 一抖动就报 "plugin was not found"
        gradlePluginPortal()
        mavenCentral()
        google()
        // 国内本地开发加速用；GitHub Runner 上这些镜像经常超时或 403，放最后仅作兜底
        maven { url = java.net.URI("https://maven.aliyun.com/repository/public") }
        maven { url = java.net.URI("https://maven.aliyun.com/repository/google") }
        maven { url = java.net.URI("https://maven.aliyun.com/repository/gradle-plugin") }
    }
    // 兜底：把插件 id 直接映射到真实构件，绕开 marker 解析
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.google.devtools.ksp") {
                useModule("com.google.devtools.ksp:symbol-processing-gradle-plugin:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = java.net.URI("https://jitpack.io") }
        maven { url = java.net.URI("https://maven.aliyun.com/repository/google") }
        maven { url = java.net.URI("https://maven.aliyun.com/repository/public") }
    }
}

rootProject.name = "PanelApp"
include(":app")
