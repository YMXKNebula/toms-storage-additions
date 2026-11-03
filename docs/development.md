# 构建与测试

使用 Java 25 和项目自带的 Gradle 9.4.1 Wrapper。

```powershell
./tools/build.ps1 build
```

首次构建联网下载固定依赖，并核对哈希。之后可加 `--offline`。安装包和源码包输出到 `build/libs`，测试代码不进入正式 JAR。

本项目可以单独构建，不要求存在 TVSN 源码目录。有相邻的 `toms-persistent-cables` 项目时，构建脚本可复用其 JDK 和 Gradle 缓存。

也可自行准备 Java 25，将 `tools/dependencies.json` 指定的 Tom's Storage JAR 及 `tools/build.ps1` 指定的编译用 Cloth API 下载至 `vendor`，再运行 `./gradlew build`。公开构建不需要整合包。

## 只安装 Tom's Storage

```powershell
./tools/build.ps1 build runGametest '-PtestLabel=release-native' --offline
```

服务器在 `run/release-native` 创建独立测试世界，执行 18 项测试。

## 搭配 TVSN

先取得 TVSN 1.0.0 主 JAR，然后运行：

```powershell
./tools/build.ps1 runGametest '-PtestLabel=release-tvsn' -PtvsnCompat '-PtvsnJar=TVSN主JAR的完整路径' --offline
```

指定的是运行时测试依赖，不会写入正式依赖。未指定 `tvsnJar` 时，旧兼容测试默认读取 `vendor/toms-virtual-storage-network-26.3-0.2.2-dev.jar`。

测试包含“箱子 → 源连接器 → 曲率漏斗 → 线缆 → 目标连接器 → 仓库”的真实接法、持续少量补货，以及远端连接器和漏斗区块的实际卸载与重载。测试中的强制加载只用于控制测试场景，正式模组不强制加载区块。

## 修改堆叠上限

`-PstackCompat` 可加载自行取得的 `vendor/compat/*.jar`。在对应 `run/<测试名称>/config` 准备测试配置，再运行：

```powershell
./tools/build.ps1 runGametest '-PtestLabel=release-stack128' -PtvsnCompat '-PtvsnJar=TVSN主JAR的完整路径' -PstackCompat --offline
./tools/build.ps1 runGametest '-PtestLabel=release-stack256' -PtvsnCompat '-PtvsnJar=TVSN主JAR的完整路径' -PstackCompat --offline
```

本次两组设置分别为普通物品/末影珍珠/工具的 128/32/1 和 256/48/4。配置及第三方模组需要自行准备，仓库不包含整合包、第三方模组安装包或玩家配置。

四组测试成功后，可运行 `./tools/package-release.ps1` 校验并整理安装包、源码包及 SHA-256 清单。

## 实现位置

- `Additions`：注册曲率漏斗，复用原生基础漏斗方块与实体类型。
- `CurvatureHopperMixin`：只对新方块改变单次传输量，保留原生调度与事务。
- `AdditionsMixinPlugin`、`TvsnUnloadedConnectorMixin`：仅对旧 TVSN 0.2.2-dev 启用的重载兼容补丁。TVSN 1.0.0 在主包内修复。
- `src/gametest`：真实原生接口与区块重载测试。

物品 ID 为 `toms_storage_additions:curvature_storage_hopper`。修改时应保留方向、过滤数据、动态堆叠上限和单源格传输规则。
