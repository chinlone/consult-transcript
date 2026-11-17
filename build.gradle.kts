// 根构建文件：只声明插件版本，具体依赖在 app/build.gradle.kts
// 版本号若与你本机 Android Studio 自带的不一致，以 IDE 提示为准，改这里即可
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.25" apply false
}
