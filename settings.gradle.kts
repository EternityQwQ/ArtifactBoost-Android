/**
 * 国内镜像只在需要时启用。
 *
 * 默认走 Google / Maven Central 官方源 —— GitHub Actions 在海外的机器上走官方源最快。
 * 本机在国内、拉不动依赖时，设置环境变量再构建：
 *
 *     export AB_USE_CN_MIRROR=1
 *     ./gradlew assembleDebug
 */
val useCnMirror = System.getenv("AB_USE_CN_MIRROR") == "1"

pluginManagement {
    repositories {
        // 注意：`pluginManagement {}` 是独立的编译作用域，
        // 看不到上面脚本里声明的变量，所以这里必须重新取一次环境变量。
        if (System.getenv("AB_USE_CN_MIRROR") == "1") {
            maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (useCnMirror) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "ArtifactBoost"
include(":app")
