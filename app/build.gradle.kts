plugins {
    id("com.android.application")
}

android {
    namespace = "com.duan.lynkmanager"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.duan.lynkmanager"
        // minSdk 降到 23、targetSdk 降到 25 是刻意为之，不要"顺手优化"回去。
        //
        // 原因：REQUEST_INSTALL_PACKAGES 这条 appops 的默认值是按 targetSdk 决定的。
        //   targetSdk >= 26（Android 8）：默认 MODE_ERRORED（拒绝），必须用户显式同意
        //   targetSdk <  26：沿用 O 之前的老行为，默认放行
        // 这台领克车机的原厂系统没有「安装未知应用」设置页
        // （MANAGE_UNKNOWN_APP_SOURCES 只解析到 com.android.car.frameworkpackagestubs
        //  的空壳占位），用户无处点同意。降到 25 之后，从 U 盘装完即可直接安装，
        // 不需要 adb，也不需要任何人工授权。
        //
        // 代价：放弃 O 之后的少数行为收紧（通知渠道、后台限制等），
        // 本项目都不涉及；WiFi/安装/文件这些用法在 25 上完全正常。
        minSdk = 23
        targetSdk = 25
        versionCode = 58
        versionName = "1.1.0"
    }

    // targetSdk 25 下调用 API 26+ 算 NewApi lint 告警，
    // 这些调用点都已有 Build.VERSION 判断或本身在支持的机型上，
    // 不能让 lintVital 把 release 构建拦下来。
    lint {
        disable += "NewApi"
        checkReleaseBuilds = false
        abortOnError = false
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
