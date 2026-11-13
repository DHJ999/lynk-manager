plugins {
    id("com.android.application")
}

android {
    namespace = "com.duan.lynkmanager"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.duan.lynkmanager"
        minSdk = 26
        targetSdk = 34
        versionCode = 28
        versionName = "1.0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 无正式 keystore，装车验证阶段用 debug key。
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// 仅二维码生成用到 ZXing core；其余功能纯平台 API。
dependencies {
    implementation("com.google.zxing:core:3.5.3")
}
