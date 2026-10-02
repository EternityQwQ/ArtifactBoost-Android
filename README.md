# ArtifactBoost — Android

GitHub 构建产物 / 源码包 **加速下载器**（Android 原生版）。

iOS 版（SwiftUI）的 Kotlin 重写：[`ArtifactBoost`](https://github.com/yitenchen123/ArtifactBoost)。
两端共用同一套配色（GitHub Primer）、同一套 GitHub REST API 模型、同一套下载引擎算法。

---

## 它解决什么问题

GitHub Actions 的产物（artifacts）、构建日志、Release 附件都托管在 Azure Blob 上，
国内直连经常只有几十 KB/s。这个 App 做的事很简单：

1. 用 Token 调 GitHub API **解析出带签名的真实下载地址**
2. 对该地址做**多通道并发测速**（直连 + 若干公共镜像）
3. 挑出最快的通道，**分段并发**下载
4. 每段校验字节数，**数量对不上就判失败**，绝不会给你一个悄悄损坏的压缩包

源码包（zipball / tarball）由 GitHub 现场打包，不支持 `Range`，自动降级为单连接。

---

## 功能

| 模块 | 说明 |
|---|---|
| **我的仓库** | 自己 + 协作 + 组织仓库，按最近更新 / 星标排序 |
| **搜索** | 搜全站仓库，别人的公开仓库也能直接下产物 |
| **仓库详情** | 概览（README 完整 Markdown 渲染）/ 构建 / 正式版 / 源码 |
| **构建详情** | 单次 run 的产物列表 + 一键下构建日志 |
| **正式版详情** | 更新说明 + 全部附件 |
| **下载管理** | 实时进度、速度、当前通道、落盘路径 |
| **设置** | 通道模式（直连 / 智能 / 自定义）、并发连接数、一键测速 |

---

## 加速原理

### 多通道测速

```
候选通道 = [直连] + [gh-proxy.com, slink.ltd, hk.gh-proxy.com, moeyy.xyz]
           ↓ 各拉 256KB 样本并发测速
           ↓ 只保留 ≥ 最快通道 40% 速度的线路
           ↓ 按实测速度加权，把分块分给快通道
```

慢通道并进来只会拖后腿，所以按 40% 阈值砍掉。测速结果存 24 小时，下次直接复用。

**私有仓库强制直连** —— 签名地址虽然本身不含 Token，但它是临时凭证，
不应该交给第三方镜像中转。

### 分段并发

- 分块大小下限 **256 KB**，分块数上限 `连接数 × 4`
- 按通道速度**加权分配**分块（快的通道分到更多块），而不是平均分
- 每块独立重试 3 次，单块失败不影响其他块
- 合并用 `RandomAccessFile.seek()` 直接写偏移，不需要占额外磁盘

### 完整性校验

每个分块下完后，**实际写入字节数必须等于 Range 声明的长度**，
否则抛 `DownloadException.Incomplete`。这是防「下载完的 zip 打不开」的关键。

---

## 技术栈

| 层 | 选型 |
|---|---|
| UI | Jetpack Compose + Material 3 |
| 网络 | OkHttp 4.12 |
| 异步 | Kotlin Coroutines + Flow |
| 序列化 | kotlinx.serialization |
| 持久化 | DataStore Preferences |
| 构建 | AGP 8.5 / Kotlin 1.9.24 / Gradle 8.9 |
| 最低版本 | Android 7.0 (API 24) |

---

## 构建

```bash
./gradlew assembleDebug          # Debug APK
./gradlew assembleRelease        # Release APK（未签名）
./gradlew lint                   # 静态检查
```

产物在 `app/build/outputs/apk/`。

---

## 使用

1. 去 [GitHub Settings → Developer settings](https://github.com/settings/tokens) 建一个 Token
   - **Fine-grained token**：勾 `Public Repositories` 只读；要下私有仓库再加对应仓库权限
   - **Classic token**：勾 `repo`
2. 打开 App 粘贴 Token
3. 进仓库 → 选构建 / 正式版 / 源码 → 点「加速下载」

---

## 隐私

- Token 存在本机 `DataStore`（应用私有目录），**只用于调 `api.github.com`**
- 下载流量：直连时打到 GitHub 的 Azure Blob；智能加速时可能经由你选的公共镜像
- 私有仓库**永远不走镜像**
- 没有埋点、没有统计、没有自建服务器

---

## 目录结构

```
app/src/main/java/com/local/artifactboost/
├── MainActivity.kt              导航骨架 + 底部 Tab
├── AppContainer.kt              依赖装配
├── SessionManager.kt            Token / 会话
├── data/
│   ├── GitHubModels.kt          REST API 模型（@Serializable）
│   ├── DownloadItem.kt          可下载项的统一种类
│   └── Formatters.kt            字节 / 速度 / 相对时间格式化
├── net/
│   └── GitHubClient.kt          全部 API 调用 + 签名地址解析
├── download/
│   ├── DownloadRoute.kt         通道定义（直连 / 镜像 / 自定义）
│   ├── RouteProbe.kt            256KB 采样测速
│   ├── DownloadEngine.kt        分段并发下载核心
│   ├── DownloadManager.kt       编排：解析 → 选道 → 下载 → 回退
│   └── AccelerationSettings.kt  设置持久化 + 测速结果缓存
├── runtime/
│   └── AppViewModel.kt          全局状态
└── ui/
    ├── theme/Theme.kt           Primer 配色
    ├── components/              通用组件 + Markdown 渲染器
    └── screens/                 各页面
```
