plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// 模拟器验证用的被管控应用，打出 A、B 两个包
android {
    namespace = "com.stopthephone.fixture"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.stopthephone.fixture"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1"
    }

    flavorDimensions += "target"
    productFlavors {
        create("a") {
            dimension = "target"
            applicationIdSuffix = ".a"
            resValue("string", "app_name", "测试应用A")
        }
        create("b") {
            dimension = "target"
            applicationIdSuffix = ".b"
            resValue("string", "app_name", "测试应用B")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
