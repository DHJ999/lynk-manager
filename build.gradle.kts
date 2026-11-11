plugins {
    id("com.android.application") version "9.0.1" apply false
    // 不应用 Kotlin，只是对齐 AGP 依赖的 kotlin-gradle-plugin 版本，
    // 与本地 Gradle 缓存中已有的 2.3.20 保持一致，保证离线可构建。
    id("org.jetbrains.kotlin.android") version "2.3.20" apply false
}
