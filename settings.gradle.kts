// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // 预加载 Kotlin 插件
    id("org.jetbrains.kotlin.jvm") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20" apply false
    // 预加载 vanniktech 插件（2025 年最推荐的 Maven Central 发布插件）
    id("com.vanniktech.maven.publish") version "0.28.0" apply false
    // Sonatype Nexus 发布插件（提供 publishToSonatype 任务）
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0" apply false
}

rootProject.name = "kotlin-sdk"

// 如果你有子模块，可以在这里 include
// include("submodule1", "submodule2")