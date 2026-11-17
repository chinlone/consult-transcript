pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // sherpa-onnx 的 Android 库：
        //  方式A（推荐，最省事）：把官方发布的 .aar 放进 app/libs/，本工程已按本地引用配置
        //  方式B：若官方提供 Maven 制品，按官方文档把仓库地址加在这里，并启用 app/build.gradle.kts 里的远程依赖
        // 详见《开发方案与源码说明.md》「第三方库版本核对」一节
    }
}
rootProject.name = "ConsultTranscript"
include(":app")
