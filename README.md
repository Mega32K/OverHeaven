# OverHeaven Foliage Compat

Minecraft Forge 1.20.1 模组，包含 SWAY / MC2 Interactive Foliage 植物兼容修复，以及命令管理、多人同步、存档保存的世界坐标黑洞系统。模组 ID 保持 `overheaven_foliage_compat`，Java 包名为 `com.mega.overheaven.foliagecompat`。

源码仓库：https://github.com/Mega32K/OverHeaven-Mod

2026-10-29：七道冲击波在上一版基础上再提速 3 倍；末段星空最终角速度为原来的 4 倍，前四分之一阶段完成提速。星空退场采用参考 dissolve_2d 的方向噪声阈值溶解，保留存活区域的亮度与主体分层。[参数与验证说明](docs/冲击波与星空溶解-2026-10-29.md)。

2026-10-29：演出星空增加解析长曝光：星点逐渐拉出弯曲光轨，银河保持 85% 清晰主体与 15% 短曝光，尾迹随噪声溶解并在末段收短。独立于主体渲染，不保存历史画面。[参数、预览与验证](docs/星空延时摄影-2026-10-29.md)。

## 当前状态

当前版本 **1.1.0**。用户提供的 2026-10-20 启动日志确认：1.0.0 因调用 ModLauncher 不支持的未转换字节码接口，在结构检查阶段跳过了 Mixin。1.0.1 改用所属 SWAY 模组的 Forge 资源路径读取类文件，已通过原异常复现、实际 SecureJar 读取和完整构建。详见 [调查记录](docs/INVESTIGATION.md) 与 [验证记录](docs/VALIDATION.md)。

用户已确认 1.0.1 修好了原先的植物重复渲染。本次保留该修复，并通过原有委托/字节码/版本守卫检查。更广泛的 Oculus/FWA 游戏内回归仍需验收。

1.1.0 新增参考图风格黑洞：白热倾斜盘、暗核、暖色尘埃、背景偏折和辉光；服务端命令、SavedData 与可选网络通道；客户端分档采样、场景透明层分段合成，以及针对实际 Oculus 1.8.0 / Complementary Unbound r5.9.3 的适配。构建、实际 API 检查与独立 OpenGL 测试通过，尚未启动 Minecraft 验收。详见 [黑洞使用说明](docs/BLACK_HOLE.md) 和 [黑洞验证/性能记录](docs/BLACK_HOLE_VALIDATION.md)。

2026-10-26 增强黑洞盘面材质与柔光：参照用户指定 Shadertoy 的公开移植，加入自有周期纹理与分层尘埃，保留世界遮挡和既有后处理顺序。来源核对、限制及预览见 [参考改造记录](docs/SHADERTOY_REFERENCE.md)。

2026-10-29：EXPOSE 使用八秒《因果解封》，在 0–4.0 秒膨胀至 125%，再于 4.0–4.4 秒快速收至 60%，末尾保留内收速度，4.4–4.55 秒继续收束盘面并连续显现核心，包含原创释放/回弹音效与短促镜头反馈。背景随蓄压降至 25%，显现时快速恢复并闪出近远可见的十字芒，显现瞬间使用 0.20 秒参考 game_1_portal 的高反差黑白明暗块与正负片闪切，取消开场径向推拉；持续全屏扭曲仍保持移除。冲击波改为解析积分的 IN_EXPO 持续加速。源码、分镜和独立 GPU 验证边界见 [因果解封说明](docs/因果解封-2026-10-28.md)。未生成新 JAR。

## 支持范围

- Minecraft **1.20.1**，Java **17**。
- Forge **47.4.10**（与实际实例一致），声明支持范围为 `[47.4.10,48)`。Interactive Foliage 2.2.0 的按键初始化调用 `ResourceLocation.fromNamespaceAndPath`，旧默认 47.3.12 缺少该 API，不能用于当前依赖组合。
- 仅为 **MC2 Interactive Foliage 2.2.0 + SWAY 2.4.5** 启用补丁，且必须满足字节码结构检查。
- 调查所用渲染组合为 Embeddium **0.3.31**、Oculus **1.8.0**、FWA **1.2.31**；后二者仍待游戏内回归。
- 缺少任一目标模组、版本不支持或类结构不兼容时跳过对应 Mixin 并记录原因。补丁不能代替原模组自身声明的依赖。
- 专用服务器加载黑洞命令、存档和网络功能；客户端渲染类、Mixin 与 GPU 诊断不应用。仅使用植物补丁仍可只在客户端安装。
- 黑洞完整功能要求服务端和观看客户端安装本版本。可选通道允许对端缺席，无补丁客户端看不到黑洞。
- 黑洞光影适配仅接受已核对文件名及 SHA-256 的原版 `ComplementaryUnbound_r5.9.3.zip`；其他光影包（包括 Euphoria 修改版）不声明支持。实际叠加终焉后处理/水下/透明效果等待游戏内验收。

## 安装与诊断

将构建出的 `overheaven_foliage_compat-1.20.1-forge-1.1.0.jar` 放入客户端实例的 `mods` 目录；使用黑洞时也安装到服务端。先移除旧补丁，避免同时存在两个版本。保留原始 Interactive Foliage、SWAY 及渲染模组。无需修改原 JAR 或关闭动画。移除补丁 JAR 即可撤销。不要安装 sources/dev 工件。

启动日志应出现 `Applied SWAY Forge model delegation patch`；条件不符时显示 `Foliage patch skipped` 及原因。该成功消息代表类转换完成，视觉效果仍需验收。

诊断默认关闭。需要定位实际链时，手动在启动器的 **JVM 参数**中添加：

```text
-Doverheaven_foliage_compat.diagnostics=true
```

以 `[foliage-diag]` 筛选日志。每个进程最多记录 4 次资源重载，每次 8 个代表性方块状态的已安装缓存模型链，以及最多 64 种去重的运行样本。链只展开已核实的 SWAY/Forge 包装器；未知包装器标记为 `leaf or opaque`，不推断其内部。缺少依赖也可输出缓存链。

运行样本在真实模型调用中记录 `LEFT_TO_GPU`、渲染层是否为空及扩展四边形数量。只采集经过 SWAY 新增委托的路径；若 Foliage 位于外层且直接返回空层，不会产生 SWAY 的 GPU 样本。这不等于补丁失效。采样不会主动调用 GPU 分流决策，也不强制改变渲染状态。

## 构建

在项目根目录使用 Java 17：

```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\ms-17.0.18'
.\gradlew.bat build
```

依赖通过 Modrinth Maven 获取，并由 ForgeGradle 重映射，使用固定版本 ID：

| 模组 | 发布版本 | Maven 版本 ID | 声明 |
| --- | --- | --- | --- |
| SWAY | 2.4.5-forge+1.20.1 | `zZZeiMZe` | `implementation fg.deobf(...)` |
| Interactive Foliage | 2.2.0-forge+1.20.1 | `M4DdktEm` | `implementation fg.deobf(...)` |
| Oculus | 1.20.1-1.8.0 | `iQ1SwGc3` | `implementation fg.deobf(...)` |
| Embeddium | 0.3.31+mc1.20.1 | `UTbfe5d1` | `runtimeOnly fg.deobf(...)`，满足 Oculus 开发运行依赖 |

构建无需本机游戏实例或手动准备依赖 JAR；首次构建需要联网下载，缓存完整后可使用 `--offline`。已移除实例目录扫描及本地 JAR 路径参数。字节码验证通过独立 Gradle configuration 获取相同远程坐标的原始工件，开发编译/运行使用 FG 重映射后的工件。标准 `implementation`/`runtimeOnly` 声明不会将第三方模组打入本模组 JAR。

正常 Java 代码由 ForgeGradle reobfJar 重映射。植物 Mixin 与 Oculus Mixin 的稳定外部符号明确 remap=false；新增原版渲染注入通过共享 refmap 重映射，发行包核对 renderLevel 的生产选择器 m_109599_。补丁不使用 MixinExtras。

`verifyArtifact` 检查重映射后的发行 JAR，包括 Java 17、Mixin 注册、refmap、正常 Minecraft 调用生产映射和未内嵌原模组。

`verifyCompatibility` 核对固定发布版本原始工件的方法描述符、结构与版本守卫，以及编译后委托路径和客户端边界；还逐项核对 Interactive Foliage 按键初始化实际使用的 ResourceLocation 静态 API，避免编译成功后在 runClient 初始化时出现 NoSuchMethodError；`verifyDelegation` 验证 ModelData 身份/属性保留、空与非空几何委托、调用次数。`verifyGuard` 复现 1.0.0 的 ModLauncher 异常，使用下载的 SWAY 原始工件的 SecureJar union 路径执行生产读取器，并检查没有调用旧接口、没有关闭共享文件系统。四项均纳入 `check` 和 `build`，均不会启动 Minecraft。

黑洞新增 `verifyBlackHole`（参数/存档/包/维度缓存/动画/命令语法）与 `verifyBlackHoleContracts`（实际 Forge/Oculus 符号、注入、客户端隔离），同样纳入构建。`verifyBlackHoleGpu` 是显式运行的隐藏独立 OpenGL 检查，会验证生产 GLSL、合成遮挡和 GPU 资源状态；不启动 Minecraft，也不代表游戏性能。

`verifyBlackHoleLens` 增加相机靠近/进入影响球、偏出屏幕与前景重影的 GPU 回归，需显式运行。近距离背景条带的定位、修改前负例、修复和验证见 [近距离透镜修复记录](docs/NEAR_CAMERA_FIX.md)。

```powershell
.\gradlew.bat verifyBlackHoleLens verifyBlackHoleGpu build
```

完整调用流程、缓存时机、根因证据与验收矩阵见 [调查记录](docs/INVESTIGATION.md) 和 [验证记录](docs/VALIDATION.md)。原 MDK 文件副本、反编译资料和本地构建日志保存在被 Git 忽略的 `audit/` 中。工作区最初没有 Git 仓库，本次未创建提交、分支或推送。
