import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.chaquopy)
}

android {
    namespace = "com.panel.corescript"

    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    }

    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.valueOf("JVM_${libs.versions.jvmTarget.get()}")
        }
    }
}

// Chaquopy：CPython 嵌入，构建期从本机 Python 3.13 打包运行时；预置 requests 纯 Python 包
chaquopy {
    defaultConfig {
        version = "3.13"
        pip {
            install("requests")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.quickjs.kt)
}
