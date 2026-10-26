# 黑洞系统验证与兼容记录

日期：2026-10-26。目标版本：1.1.0。此记录区分文件/字节码事实、代码推导、独立 GPU 测试与 Minecraft 游戏内验收。

## 交付结果

- 已实现命令、服务端存档、可选多人同步、参考图预设和客户端渲染。
- 保留模组 ID、Java 17 和 Minecraft 1.20.1；Forge 已根据实际 runClient 崩溃修正为 47.4.10，发行元数据最低版本同步为 47.4.10。
- 发行 JAR：`E:\CollapsingWorld\System-e\OverHeaven\mod\build\libs\overheaven_foliage_compat-1.20.1-forge-1.1.0.jar`。
- 大小：915,509 字节。
- SHA-256：`3896c2acff4a7603e18e8c497878126ca985c7c9e3105d51c2e9ba6db48c71d5`。
- 完整构建、生产映射、资源/元数据及验证任务通过。未启动 Minecraft，未替换实例模组、修改实例配置、发布、提交或推送。

## 当前环境透镜修复

2026-10-26：本轮基于已撤回重构后的版本，采用独立方向映射和分层背景选择；没有恢复逐步深度追踪。最新性能、证据和限制见 [环境透镜修复记录](LENS_ENVIRONMENT_FIX.md)。后面的旧实现/计时按历史记录理解。

## 已观察的文件与字节码事实

以实际实例 `E:\CollapsingWorld\System-G\.minecraft\versions\跨越天堂-世末歌者` 中的 JAR 和光影 ZIP 为准，不以本地终焉较新源码替代已安装版本接口。

| 工件 | 核对版本 / SHA-256 |
| --- | --- |
| EndingLibrary | 2.2.1fix / `a0c9bd0f52545ab047b969dd679daf6b05186484e88c520ceccda05c851f6600` |
| Oculus | 1.8.0 / `0945df0cba0f62b3901dd80c3268e5311b770ece78c78037a45db12ac0425fef` |
| Embeddium | 0.3.31 / `eed3d1325f2acc2fd4e69bb495e5ccb91d962126ac5330f0582ebc2a3daf47fb` |
| ComplementaryUnbound_r5.9.3.zip | `98ac741dee294d2a3c82d153349148c3bb7fb688c30eb3b49c3b9ac37e968c5a` |
| 原植物修复对象 | Interactive Foliage 2.2.0 + SWAY 2.4.5；FWA 1.2.31 为回归对象 |
| Forge | 当前编译/开发运行 47.4.10，与实例一致；原 47.3.12 不提供 Interactive Foliage 所需的 ResourceLocation 工厂 API |

保留的证据在 `audit/blackhole-1.1.0/engine/`：

- `GameRenderer.java`、`LevelRenderer.java`：Forge 世界绘制、AFTER_LEVEL、手部及 renderLevel 返回的调用顺序；普通透明与高品质透明分支的准确捕获位置。
- `EndingLibrary-GameRendererMixin.txt`：实际 JAR 在 GameRenderer.renderLevel TAIL 调用 `PostProcessingShaders.levelEffect(F)`。未使用实例尚未提供的新深度接口。
- `IrisRenderingPipeline.txt`、`IrisSamplers.txt`、`RenderTargets.txt`：beginTranslucents、finalizeLevelRendering、depthtex0/1、flipped 主副纹理与 colortex0/3 选择。
- `CompositeRenderer.txt`、`ProgramSet.txt`：composite1 的真实名称、createProgram 描述符、Program/Framebuffer 在 renderQuad 结束时仍绑定的事实。
- `composite.glsl`、`composite1.glsl`、`gbuffers_water.glsl`、`shaders.properties`：实际光影的透明信息、折射顺序及颜色变换；前景阶段使用其 `pow(color, 2.2)` 颜色关系。
- `GlStateManager.java`、`RenderStateShard.java`：透明混合的实际 alpha 关系。Embeddium TerrainRenderPass 仍调用对应 RenderType 状态设置/清理。

Interactive Foliage 的 GPU 绘制位于 Forge AFTER_CUTOUT_BLOCKS，先于本次不透明采集，匹配版本反编译证据另见 `audit/decompiled/foliage/net/karto/mc2/mc2_interactivefoliage/platform/forge/ForgeFoliageHooks.java`。原植物兼容的三项 Forge 扩展委托实现、版本守卫和原诊断类不因黑洞渲染改写。

## 从调用链得到的实现结论

AFTER_LEVEL 适用于原版最终合成，但 Oculus 的 HDR composite 链先于该事件结束。为保留宿主水面折射，光影路径改为：不透明采集 → 透明表面采集 → 折射前后侧辐射 → composite1 折射结束后的前侧辐射 → 其余光影处理 → Forge AFTER_LEVEL → 终焉世界后处理。此处为基于实际字节码的时序推导，尚不是游戏截图证据。

不会重新绑定/替换全局后处理链，不改 EndingLibrary/Oculus/光影包内容。原版和 Oculus 都采集透明层输入，不能把最终颜色加一张深度图当作完整透明适配。当前设计仍受单透明表面深度及宿主排序限制；多层透明、TAA 历史/运动矢量和水下雾须游戏验收。

可选 Oculus 注入按客户端、模组版本和原始目标字节码门控；四个可选 Mixin 使用 @Pseudo 与 remap=false。原版 LevelRenderer/Minecraft 注入用生成 refmap 映射，发行包核对 `m_109599_` 生产选择器及稳定 `close()V`。没有 MixinExtras 依赖，没有内嵌第三方 JAR/类。

## 自动检查结果

最终日志：`audit/blackhole-1.1.0/build-final.log`。实例 Forge 版本补充日志：`audit/blackhole-1.1.0/runtime-forge-contracts.log`。

| 任务 | 结果 | 覆盖范围与边界 |
| --- | --- | --- |
| verifyBlackHole | 72 项通过 | 有限值/范围/几何、无效原子编辑、NBT 往返/坏记录、增删改包、维度副本、动画暂停恢复、命令语法；不代表真实联机测试 |
| verifyBlackHoleContracts | 692 项通过 | 实际 Forge/Oculus 符号、注入描述符、客户端隔离与调用结构；47.3.12 和单独指定的 47.4.10 均通过 |
| verifyBlackHoleGpu | 4,665,662 项断言通过 | 生产 GLSL 编译/链接、透明前后分段、墙/墙边深度、核心/玻璃合成、前景折射阶段、像素有限值、GL 状态与资源；计数含逐像素断言，不是同等数量的独立场景 |
| verifyCompatibility | 97 项通过 | 实际 SWAY/Interactive Foliage 结构、版本、客户端边界及按键初始化的 ResourceLocation API 链接 |
| verifyDelegation | 通过 | ModelData 身份/属性、空层、GPU 空几何、fallback 几何、单次委托 |
| verifyGuard | 38 项通过 | 旧 ModLauncher 异常复现、SecureJar 读取、禁用旧 provider API |
| verifyArtifact | 1650 项通过 | 重映射发行包、Java 17、元数据、双 Mixin 配置、共享 refmap、shader/preset 资源、未打入原模组 |
| build / reobfJar | 成功 | 可供手动游戏测试的生产 JAR |

GPU 检查使用隐藏的独立 GLFW/OpenGL 3.3 上下文，实际显卡 NVIDIA GeForce RTX 3060，驱动显示 NVIDIA 560.94。它没有启动 Minecraft。检查还包括颜色/深度/混合/逐附件混合和颜色写掩码恢复、纹理/采样器与 FBO、PBO 绑定下安全分配、目标 resize/删除重建、无 GL error。资源重载/退出的实际游戏事件仅做静态调用检查，不能据此声称长时间游戏资源泄漏已被排除。

构建复现（Windows PowerShell，Java 17 与已缓存依赖）：

```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\ms-17.0.18'
$env:GRADLE_USER_HOME = 'E:\CollapsingWorld\System-e\.gradle'
.\gradlew.bat --offline verifyBlackHoleGpu build '-Dnet.minecraftforge.gradle.check.certs=false' --console=plain

.\gradlew.bat --offline verifyBlackHoleContracts '-Pforge_version=47.4.10' '-Dnet.minecraftforge.gradle.check.certs=false' --console=plain
```

禁用 ForgeGradle 证书检查只用于当前离线缓存构建命令，不写入项目或实例配置；其他环境可按正常网络证书配置构建。API 废弃提示、Gradle 的 APT/Eclipse 提示和无终端高级功能提示未导致失败。

## 独立 GPU 性能记录

最终一次 1920×1080、全屏单黑洞夹具，24 次 GPU 时间戳均值：

| 档位 | 求解尺寸 | 基础步数 | 核心求解均值 |
| --- | --- | --- | --- |
| low | 720×405 | 24 | 0.554 ms |
| balanced | 960×540 | 48 | 0.961 ms |
| high | 1440×810 | 96 | 2.602 ms |

这些值只测独立求解内核，不包含采集、最终合成、辉光、Minecraft、Oculus、EndingLibrary；不是游戏增量开销，不用于承诺固定 FPS。`audit/blackhole-1.1.0/gpu/reference-{24,48,96}.png` 是独立夹具输出，`docs/blackhole-preview.png` 为 48 步副本，均不是游戏截图。

游戏内请固定 RTX 3060 / 1080p、同一相机位置、世界内容和光影设置，交替开启/删除一个黑洞，记录基线帧 GPU 时间及诊断四阶段时间；至少覆盖 low/balanced/high、无光影、Complementary、叠加终焉效果。无可见黑洞时应无黑洞采集/求解/辉光/合成 pass。此项尚未执行。

## 文件变化与差异方式

- `FoliageCompat.java`：注册黑洞通道和公共事件。
- `blackhole/`：不可变参数、服务端 SavedData/命令/同步、客户端维度副本及可选目标守卫。
- `blackhole/client/`：独立 GPU 目标/着色器/状态、原版与 Oculus 场景适配、渲染事件、资源生命周期、质量命令和异步计时。
- `blackhole/mixin/`：两个原版生命周期注入，四个可选 Oculus 注入。
- `assets/overheaven_foliage_compat/shaders/blackhole/` 与 `blackhole/presets/reference.json`：原创建模/合成 GLSL 与预设描述。
- `overheaven_foliage_compat.blackhole.mixins.json`、`build.gradle`、`gradle.properties`、模组说明：注册、可选编译依赖、版本 1.1.0 与构建检查。
- `src/verification/`：黑洞公共/契约/GPU 验证；扩充原发行包验证。
- `README.md` 与本说明：交付、命令、性能和验收记录。

项目原本没有 Git 仓库，不初始化。差异使用 `audit/blackhole-1.1.0/before` 和 `after` 快照；检查命令 `git diff --no-index --check <before> <after>`。旧文档不修改，after/docs 仅包含本次新增黑洞文档及预览。原植物 Mixin/守卫/诊断及原有委托/守卫/兼容验证源码的 SHA-256 与 before 一致；16 个原文件中仅 build.gradle、gradle.properties、README.md、FoliageCompat.java 和 ArtifactVerification.java 有修改。哈希报告为 `audit/blackhole-1.1.0/original-file-hashes.json`。`git -c core.autocrlf=false diff --no-index --check` 已通过（无空白错误；no-index 返回 1 表示快照有差异），记录见 `whitespace-check.log`。

## 待执行的游戏内验收

以下全部尚未执行，不用构建或独立 GL 结果替代勾选。

- [ ] 参考图对照：默认预设、正面/侧面/背面、近距离、移动视角；暗核/近侧盘/宽弧/暖尘埃和辉光层次。
- [ ] 不透明：墙后全遮挡、墙边部分、地面、树叶孔洞、前景实体经过；第一/第三人称、视角摇晃与受伤变化。
- [ ] 透明：黑洞处于单层玻璃和水面之前/之后，玻璃透射、水面颜色/折射、水下观察；多层透明的宿主排序边界。
- [ ] Embeddium 0.3.31 主复现场景；草/蕨类/花/双格植物/树叶及交互形变，无静态重复模型。
- [ ] Oculus 1.8.0 关闭光影与开启已核对 Complementary r5.9.3；切换后检查残留/顺序/深度。
- [ ] 分别叠加实际 EndingLibrary 2.2.1fix 世界后处理，核对启停和先后顺序；FWA 1.2.31 自身动画与植物仍正常。
- [ ] 命令权限、非法参数不改变状态、跨维度 move、当前维度 clear、唯一 ID 和数量限制。
- [ ] 两个客户端登录/换维度/重连/服务器重启，快照与增量一致，种子与暂停相位保留。
- [ ] 客户端单独安装植物补丁；服务端/对端缺少补丁时可选通道跳过；缺少 SWAY/Interactive Foliage/Oculus 时按门控跳过。
- [ ] 专用服务器真实启动与连接；静态客户端隔离检查不能替代实际启动。
- [ ] F3+T 资源重载、窗口缩放、离开/重进世界、关闭游戏的资源/状态恢复；无持续增长或旧包引用。
- [ ] RTX 3060 / 1080p 单黑洞开关的增量 GPU 时间、诊断档位和无可见黑洞时零渲染 pass。

## 2026-10-26：远程 FG 依赖调整

按用户要求，构建改用 Modrinth Maven 和 `implementation fg.deobf(...)` 引用 SWAY 2.4.5、Interactive Foliage 2.2.0、Oculus 1.8.0；通过 `runtimeOnly fg.deobf(...)` 加入 Oculus 所需的 Embeddium 0.3.31。固定版本 ID 见 README，不再读取实例目录或接受手动 JAR 路径。

字节码检查的 `compatibilityArtifacts` configuration 从相同远程坐标解析发布原件，防止把开发映射后的类误当生产字节码。Forge 检查从当前 FG 类路径取得对应版本，不再接受本地 runtime Forge JAR 参数。要核对其他 Forge 补丁版本，使用 `-Pforge_version=47.4.10`。以上原始调查的 JAR/实例路径保留为历史证据，不参与依赖解析。

调整前快照与本轮构建记录位于 `audit/fg-dependencies-2026-10-26/`。保留现有 gradle.properties（包括用户代理配置）及全部游戏功能源码；未启动 Minecraft 或改动实例。

## 2026-10-26：Java 包名迁移

项目 Java 包名与 Maven group 统一为 `com.mega.overheaven.foliagecompat`，包括主源码、验证源码、Mixin package/plugin、反射类名、字节码路径、Gradle JavaExec 入口及文档源码路径。模组 ID、网络通道名、存档键和游戏功能保持原值。

迁移前完整源码和旧发行工件保存在 `audit/package-rename-2026-10-26/before/`。本次先保留旧工件，再执行 Gradle clean，防止旧包名 class/refmap 混入新 JAR；命名空间标准化后的源码比较用于确认无额外功能变更。上述初次黑洞实现的快照/哈希结论保留为历史记录，包名迁移后的最终构建及差异记录以 `audit/package-rename-2026-10-26/` 为准。

本轮结果：远程依赖解析及 Forge 47.4.10 契约检查通过；包名迁移后执行 clean，随后离线 build、72 项黑洞行为、692 项契约、94 项植物兼容、委托、38 项守卫、1650 项发行包检查全部通过。最终 JAR 含 34 个 `com/mega/` 类，所有 class、JSON/refmap 中无旧包名引用；未内嵌原模组。49 个 src 源码/资源文件在包名标准化后与迁移前一致，gradle.properties 除 group 外保持原内容（包括用户代理）。GLSL 和算法未变，本轮未重复独立 GPU 测试；初次 GPU 记录仍以上文原日志为准。

最新构建：`audit/package-rename-2026-10-26/build-final.log`。远程依赖解析图及原始工件指纹：`audit/fg-dependencies-2026-10-26/build-final.log`、`resolved-artifacts.json`。包名标准化比较：`audit/package-rename-2026-10-26/namespace-comparison.json`。

## 2026-10-26：runClient 的 ResourceLocation API 缺失

用户手动运行后的 `run/logs/latest.log` 与 `crash-2026-10-26_03.38.32-client.txt` 显示开发客户端使用 Forge 47.3.12，在 Interactive Foliage 的 `ForgeKeyBindings.<clinit>` 注册按键时抛出 `NoSuchMethodError: ResourceLocation.fromNamespaceAndPath(String,String)`。这是依赖运行所需的 Forge API 缺失；日志中的本项目 refmap 读取警告不是这次抛出的异常位置。

重新核对了实际固定发布工件的 ForgeKeyBindings 字节码，以及 FG 提供的 47.3.12 / 47.4.10 ResourceLocation 类：前者没有该工厂，后者提供准确描述符 `(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;`。未用最新分支替代版本核对，不推断该 API 最早加入的 Forge 补丁版本。

修复将 `forge_version` 和发行范围下限统一到实例已使用的 47.4.10。没有回退远程 FG 依赖、改写第三方 JAR 或改变渲染逻辑；保留 com.mega 包名及原代理设置。`verifyCompatibility` 新增对实际 ForgeKeyBindings 中 ResourceLocation 静态调用的字节码链接检查，比较名称、完整描述符及 public/static 标记，不初始化游戏类。旧 47.3.12 作为负例必须被该检查拒绝，当前 47.4.10 必须通过。

此前只验证了模组委托/渲染契约与编译，没有覆盖这段第三方按键初始化的 API 约束，不能把先前构建通过解释为 runClient 启动成功。本轮证据、调整前后快照和构建记录保存在 `audit/runclient-resource-location-2026-10-26/`，未自动重启 Minecraft。

本次验证：旧 Forge 47.3.12 的负例准确触发新增 API 链接检查；当前 Forge 47.4.10 的 97 项兼容检查及 692 项渲染契约检查通过。`prepareRunClient build` 完成，72 项黑洞、委托、38 项守卫、1650 项发行包检查通过，发行包 Forge 下限为 `[47.4.10,48)`。

另使用审计目录内临时 init 脚本调用已核实的 ForgeGradle 6.0.54 令牌解析方法，核对真实 runClient 参数为 `--fml.forgeVersion 47.4.10`，并刷新 `build/classpath/runClient_minecraftClasspath.txt`，确认不再含 47.3.12。该检查只解析参数/系统属性，不调用 MinecraftRunTask.exec 或 JavaExec，不启动游戏。注意 prepareRunClient 本身不会解析所有延迟类路径令牌；不能凭旧文件时间判断 Gradle 实际版本。具体日志为 `inspect-runclient.log`，完整构建为本审计目录的 `build-final.log`。

本次没有修改主源码或 GLSL，未重复独立 GPU 测试。需要用户刷新 Gradle 项目后手动重新运行 runClient，真实启动、游戏画面及运行兼容仍以那次日志为准。

## 2026-10-26：Shadertoy 参考材质改造

参考核对与机制见 [SHADERTOY_REFERENCE.md](SHADERTOY_REFERENCE.md)。原站自动验证后加载失败；使用内置浏览器核对了明确引用 lstSRS 的公开移植画面与固定提交代码，不宣称已读到原站当前源码。已检查两份输入纹理，发行包使用自行生成的周期数据。保持模组版本 1.1.0，以本节 SHA-256 区分新的构建。

最终执行隐藏 OpenGL 验证及 build，日志为 audit/shadertoy-reference-2026-10-26/build-final.log：

- 五个生产 shader 编译链接通过。新增接缝、材质动画、采样收敛、发光盘透射、纹理上传/PBO/unpack、mip 链、单元 10 恢复及重载删除检查通过；连同已有图像逐分量检查为 4,665,675 项。此数字包含逐像素断言，不等于相同数量的独立场景。
- 72 项命令/数据/网络/动画检查、692 项 API/注入/客户端隔离检查、97 项植物兼容/运行 API 检查、模型委托、38 项守卫和 1,722 项发行包检查通过。生产重映射/refmap 及素材入包均已检查。
- 材质生成器重复运行产生相同 SHA-256：f8c176a82f596f57300d710c013ea9a3ed48ec481d9f9ac61705559335a48b38。
- 当前目录不是 Git 仓库；对 before/after 快照执行 git diff --no-index --check，并核对构建配置、公共数据代码、Mixin 和植物补丁未变化。具体清单见本审计目录 changed-files.json、whitespace-check.log。

在 RTX 3060 / NVIDIA 560.94 / 隐藏 OpenGL 3.3 / 1080p 固定场景中，交替测量本轮修改前后的生产 shader，每档三批、每批 24 帧，另有预热；记录批均值的中位数：

| 质量 | 修改前内核 | 修改后内核 | 本次差值 |
| --- | --- | --- | --- |
| 低，24 步 / 720×405 | 0.847 ms | 0.959 ms | +0.111 ms |
| 均衡，48 步 / 960×540 | 1.543 ms | 1.482 ms | -0.061 ms |
| 高，96 步 / 1440×810 | 3.496 ms | 3.703 ms | +0.207 ms |

这是求解、辉光、合成的独立夹具计时，不含场景采集、Minecraft、Oculus、EndingLibrary，不能解释成游戏帧率或整套后处理增量。GPU 时钟/系统负载会影响结果，先前一轮同上下文均衡档差值为 +0.162 ms，故不把本轮负差值解释为稳定加速。24/48/96 步当前固定场景 HDR 平均值约 0.409 / 0.399 / 0.397，最大/最小比小于 1.10；仅证明该场景的整体能量收敛，不能代替细纹或轮廓的游戏验收。

预览 docs/blackhole-preview.png 及 audit/blackhole-1.1.0/gpu/ 的同名可再生图已更新；初始预览保存在本轮 before/docs/blackhole-preview.png。新证据另存 audit/shadertoy-reference-2026-10-26/gpu/。这些是执行生产 shader 产生的合成图，不是 Minecraft 截图。

发行 JAR：E:\CollapsingWorld\System-e\OverHeaven\mod\build\libs\overheaven_foliage_compat-1.20.1-forge-1.1.0.jar

大小：909050 字节。SHA-256：E8502FAA941319A812D63349618026359C5D0ADADAC4CA343B4E20167C72942F。本轮没有启动游戏、修改实例配置、安装/替换模组、创建提交或推送。保留上文完整游戏验收清单；真实地形/透明层、当前光影包及终焉组合画面仍待手动验收。

## 2026-10-26：近距离背景条带与前景重影

本次定位、修改前负例、15 组视点/质量回归、性能测量与新发行包哈希记录在 [NEAR_CAMERA_FIX.md](NEAR_CAMERA_FIX.md)。执行 `verifyBlackHoleLens verifyBlackHoleGpu build` 通过。旧 shader 的前景复制像素合计为 68,547，新 shader 为 0；这是独立 GPU 夹具证据，未启动 Minecraft。植物委托及所有既有构建检查通过。

本次新 JAR 为 909991 字节，SHA-256：24B546D226F9BBFE76E7D3ADE3D7D2294BC0FCB4FA1F971402BABEDA7A13C1C0。版本仍为 1.1.0，使用本次哈希区分，不能沿用上一节旧 JAR 哈希。游戏内原截图复现、Oculus/终焉组合效果与实际帧率尚待用户手动验收。

## 2026-10-26：尘埃盘、局部薄雾和多尺度辉光

本轮实现、固定条件对照、GPU 开销、最新 JAR 指纹和验收边界见 [DISK_BLOOM.md](DISK_BLOOM.md)。最终日志见 audit/disk-bloom-2026-10-26/final-check.log。已完成生产 shader GPU 验证、近距离回归和完整构建；游戏内 Oculus/终焉组合画面仍待验收。以上旧测试包指纹均属于历史记录，本轮使用 4617410F0241358DFE0EB2DB97885C1414D453062149A003625ED7461E2B512C。

## 2026-10-26：近距离有效透镜源不连续保护与内侧降饱和

最新测试包和证据见 [LENS_DISCONTINUITY.md](LENS_DISCONTINUITY.md)。已用旧 shader 失败、新 shader 通过的 GPU 反例验证源 UV 插值缺陷；原截图实际游戏复测仍待确认。内侧色度以平滑曲线从 0% 过渡到原色度的 50%。本轮完整构建和植物回归通过，最新 SHA-256：FF7FCF285FF706E19339B3E8588A25AA050990C8F06D99A1EF951D34A81CA205。

## 外层透镜减弱（2026-10-26）

环境偏折新增平滑衰减：以视线到质量轴的 impact 距离衡量，影响半径的 35% 内保留原强度，35%–80% 之间平滑降低，80% 外保留原强度的 65%；原有外缘归零曲线继续叠加。只调整背景偏折，不改变盘面积分。范围内相机、近距离 GPU 回归与 build 通过，日志在 audit/lens-outer-soften-2026-10-26/check.log。本次未重新计时，前文性能表属于减弱前版本，实际游戏效果仍待验收。
