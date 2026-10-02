pluginManagement {
    repositories {
        // 只保留可达的国内镜像。
        // 注意：不要在这里保留 google() / mavenCentral() 兜底——在无法直连
        // dl.google.com / repo1.maven.org 的网络里，Gradle 会在镜像未命中时
        // 去请求这些死地址，每个构件都要卡一次超时，构建看起来就像「卡住了」。
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

rootProject.name = "ArtifactBoost"
include(":app")
