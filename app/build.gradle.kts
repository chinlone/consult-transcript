import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// 读取本地签名配置（key.properties 不进版本库，示例见 signing/key.properties.example）
val keyPropsFile = rootProject.file("key.properties")
val keyProps = Properties().apply {
    if (keyPropsFile.exists()) load(FileInputStream(keyPropsFile))
}

android {
    namespace = "com.xiaodong.consulttranscript"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xiaodong.consulttranscript"
        minSdk = 26          // Android 8.0 及以上，覆盖绝大多数在用机型
        targetSdk = 35
        versionCode = 7
        versionName = "1.0.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 模型文件是二进制，禁止压缩：否则运行时要先解压、加载变慢且占双倍空间
        androidResources.noCompress += listOf("onnx", "bin", "gguf", "wav")
    }

    signingConfigs {
        create("release") {
            if (keyPropsFile.exists()) {
                storeFile = file(keyProps.getProperty("storeFile"))
                storePassword = keyProps.getProperty("storePassword")
                keyAlias = keyProps.getProperty("keyAlias")
                keyPassword = keyProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keyPropsFile.exists()) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // 调试包也要能装到真机测离线转写
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // 不同 so 架构：真机基本都是 arm64，只留它可显著减小包体
        // 若还要支持 32 位老机器，把下面这行注释掉
        jniLibs.excludes += setOf("lib/armeabi-v7a/**", "lib/x86/**", "lib/x86_64/**")
    }
}

dependencies {
    // ---- 界面：Jetpack Compose + Material 3 ----
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.fragment:fragment-ktx:1.8.5")  // BiometricPrompt 需要 FragmentActivity
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // ---- 本地数据库（本机自用版：明文 SQLite，不加密）----
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")
    implementation("androidx.sqlite:sqlite-framework:2.4.0")

    // ---- 后台长任务、系统文件选择器（无生物识别）----
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.documentfile:documentfile:1.0.1")

    // ---- 离线语音识别引擎 ----
    // 方式A：把官方 sherpa-onnx-release.aar 放到 app/libs/ 后启用下面这行
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))
    // 方式B：若改用 Maven 制品，注释上面的 fileTree，改成官方给出的坐标，例如：
    // implementation("com.k2fsa.sherpa:onnx:1.10.0")
}
