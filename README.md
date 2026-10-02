# ArtifactBoost（Android）

GitHub Actions 产物加速下载器。**多线程 + HTTP Range 分段并发 + 多通道并行**，把动手下载产物、构建日志、Release 附件、源码包这件事跑满带宽。

这是 [ArtifactBoost](https://github.com/yitenchen123/ArtifactBoost)（Swift / SwiftUI iOS 版）的 **Kotlin / Jetpack Compose 安卓移植版**，算法与行为 1:1 对齐。

---

## 功能

| 能力 | 说明 |
| --- | --- |
| **仓库列表** | 分页拉取当前 Token 可见的仓库，支持关键字过滤 |
| **仓库搜索** | 按关键字搜索 GitHub 仓库，可按最佳匹配 / 星标 / 更新时间排序；也可直接粘贴 `owner/repo` 打开 |
| **仓库详情** | 概览（README）/ 构建 / 正式版 / 源码 四个标签页 |
| **构建详情** | 构建日志 + 该次运行的全部产物，一键盘下载 |
| **正式版详情** | Release 附件 + 对应 tag 的源码包 |
| **下载管理** | 进度、实时速度、通道说明、取消 / 重试 / 完成后导出分享 |
| **通道测速** | 拿真实下载目标逐条通道测速，保存最快的，24h 内复用 |
| **前台服务** | 下载在通知栏持续运行，锁屏 / 切后台不中断 |

---

## 加速原理（核心算法，与 iOS 版一致）

1. **拦截 302 重定向**
   GitHub 的下载接口会 302 到签好名的 Azure Blob / codeload 地址，用 `followRedirects(false)` 的客户端读出 `Location` 头拿到真正的地址。

2. **探测是否支持分段**
   发一个 `Range: bytes=0-0` 探测请求：
   - 返回 `206` + `Content-Range` → 支持 Range，走分段并发；
   - 返回 `200` → 不支持，回退单连接下载。

3. **分块策略**
   分块数 = `min(并发数, 64) × 4`，单块最小 256KB。块足够多，慢块才有机会被抢回去重下。

4. **多 Session**
   `sessionCount = min(4, max(1, 并发数 / 8))`，每个 Session 一份独立的 OkHttp 连接池 —— 避免所有请求挤在同一条 HTTP/2 TCP 连接上被复用天花板限速。

5. **按速度分配分块**
   每块下完记录速度，下一个块贪心地分给「当前负载 / 实测速度」比值最小的通道，快通道自动多干活。

6. **重试与回退**
   每块最多 3 次尝试，仍失败就回退「直连 + 单连接」兜底。

7. **完整性校验**
   每块按 `Content-Range` 校验长度，合并后再核对总大小；不一致就删掉重来并抛 `Incomplete`。

8. **进度节流**
   进度回调 250ms 一次，速度做 0.6 / 0.4 滑动平均，避免刷新过猛和数字乱跳。

9. **私有仓库强制直连**
   私有仓库的签名地址绝不交给第三方镜像。测速对象来自私有仓库时也只测直连。

10. **测速结果复用**
    测速结果缓存 24 小时，期间所有下载直接复用最快通道，不用每次现测。

---

## 下载通道

- **直连**：直接连 GitHub 存储，最安全，但国内通常很慢。
- **智能加速**：在直连与内置公共镜像（gh-proxy.com / slink.ltd / hk.gh-proxy.com / moeyy.xyz）之间自动测速，选最快的；多通道并行时带宽可以叠加。
- **自定义**：填你自己搭建的中转前缀（Cloudflare Worker / 反向代理）。

> 说明：智能加速与自定义通道会让产物数据经过第三方中转（**只中转已签名的产物地址，不接触你的 Token**）。私有仓库始终强制直连。

---

## 从 iOS 版到安卓的对应关系

| iOS | Android |
| --- | --- |
| SwiftUI View | Jetpack Compose Composable |
| `URLSession` / `URLSessionConfiguration` | OkHttp `OkHttpClient` + 连接池 |
| `URLSession.shared.data(for:)` | `suspend fun` + `Dispatchers.IO` |
| `Keychain` | `EncryptedSharedPreferences`（Keystore 不可用时降级明文，避免崩溃） |
| `UserDefaults` | `SharedPreferences` |
| `NavigationStack` | `androidx.navigation:navigation-compose` |
| `AsyncImage` | Coil `AsyncImage` / `SubcomposeAsyncImage` |
| `ShareLink` | `FileProvider` + `Intent.ACTION_SEND` |
| `UIBackgroundTask` | 前台服务（`foregroundServiceType="dataSync"`） |
| `@Published` / `ObservableObject` | `StateFlow` + `collectAsStateWithLifecycle` |
| Swift `Codable` | `kotlinx.serialization` |
| `AttributedString` Markdown | Compose `AnnotatedString` + 手写 `MarkdownParser` |

---

## 项目结构

```
app/src/main/java/com/artifactboost/app/
├─ ArtifactBoostApp.kt        # Application：持有 session / downloads
├─ MainActivity.kt            # 单 Activity，按登录态切换界面
├─ data/
│  ├─ GitHubModels.kt         # @Serializable 数据模型
│  ├─ GitHubClient.kt         # REST 调用 + 302 解析（resolveDownloadUrl）
│  ├─ DownloadRoute.kt        # 通道定义 / 加速设置 / 通道测速
│  ├─ DownloadItem.kt         # 可下载项与来源类型
│  ├─ TokenStore.kt           # 加密存储 Token
│  └─ SessionManager.kt       # 登录态
├─ download/
│  ├─ DownloadEngine.kt       # ★ 分段并发下载核心
│  ├─ DownloadManager.kt      # 任务调度 / 通道选择 / 重试
│  └─ DownloadService.kt      # 前台服务 + 通知进度
├─ ui/
│  ├─ theme/Theme.kt          # GitHub Primer 配色（浅色/深色）
│  ├─ components/             # 通用组件（卡片 / 胶囊 / 骨架屏 …）
│  └─ screens/                # 各页面 + Markdown 渲染
└─ util/Formatters.kt         # 大小 / 速度 / 相对时间 / ISO8601
```

---

## 构建

### 环境要求

- JDK 17
- Android SDK：platform 35、build-tools 35.0.0
- Gradle 8.9（仓库自带 wrapper）

### 命令行

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk

./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

./gradlew :app:assembleRelease   # 需要配置签名
```

> 国内构建时仓库已默认指向阿里云镜像（`settings.gradle.kts`），Gradle 发行版走腾讯云镜像。

### GitHub Actions

仓库内置 `.github/workflows/build.yml`，push / PR 时自动产出 debug APK，也可以在 Actions 页手动触发。

---

## 使用

1. 打开 app，粘贴一个 GitHub Personal Access Token。
   - 只读公开仓库：**无需任何 scope**；
   - 需要看私有仓库 / Actions 产物：勾选 `repo` + `actions:read`。
   - 快速创建：https://github.com/settings/tokens/new?scopes=repo,workflow&description=ArtifactBoost
2. Token 只保存在本机（加密存储），所有请求直连 `api.github.com`。
3. 进「仓库」选一个仓库 → 切到「构建 / 正式版 / 源码」→ 点下载。
4. 想更快：进「设置」→ 测速并保存最快通道 → 回到仓库继续下载。

下载完成的文件在 **`Android/data/com.artifactboost.app/files/Artifacts/`**，也可以在下载卡片上点「导出」通过系统分享面板发到别处。

---

## 免责声明

- 智能加速与自定义通道可能让产物数据流经第三方中转（**中转的只是已签名的产物地址，不涉及你的 Token**）。私有仓库会自动强制直连。
- 请仅下载你有权限访问的内容。
- 本项目与 GitHub 无关联。
