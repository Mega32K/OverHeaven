# 重复植物渲染调查

调查日期：2026-10-20（Asia/Shanghai）。工作区：E:/CollapsingWorld/System-e/OverHeaven/mod。

## 已观察事实

1. 工程最初是 Forge 1.20.1 / 47.3.12 的原始 MDK 示例，没有 .git；父级也不是 Git 仓库。保留 Gradle 8.8 包装器、official 1.20.1 映射和 Java 17。修改前的工程文件副本保存在 audit/baseline。
2. 直接读取实例中的 5 个 JAR，通过 Java 17 javap -p -c -s 核对签名，再用本机 Vineflower 1.10.1 反编译。文件 SHA-256 见 instance-manifest.json。外部主分支仅作参考，不代替这些版本的字节码。
3. SWAY 文件名实际为 !sway-2.4.5-forge+1.20.1.jar，内部版本为 2.4.5，onModifyBakingResult 中含 [SWAY-DIAG] 日志。是否与公开发行文件逐字节一致未核实，补丁按当前实物类结构判断。
4. 实例 mc2_interactivefoliage.json 中 gpuRenderer、wavingFoliage、wavingLeaves 都为 true；sway.json 中 enabled=true。oculus.properties 中 enableShaders=false。配置仅被读取，没有修改。
5. 初次调查使用归档 2026-10-19-7.log.gz 读取匹配启动记录，显示 Forge 47.4.10、Embeddium/IF/FWA 被发现，SWAY 烘焙日志 totalModels=309254、wrapped=388。随后用户提供的 2026-10-20 latest.log 记录了安装 1.0.0 后仍有问题；它没有输出最终缓存链。

## 1.0.0 的实际运行失败与 1.0.1 修订

2026-10-20 的 latest.log 第 38 行发现了 1.0.0 补丁；第 164–167 行明确记录：

```text
Foliage patch skipped: cannot inspect optional SwayModel target.
java.lang.IllegalArgumentException: ModLauncher service does not currently support retrieval of untransformed bytecode
    at org.spongepowered.asm.launch.MixinLaunchPluginLegacy.getClassNode(...)
    at ...FoliageCompatMixinPlugin.shouldApplyMixin(FoliageCompatMixinPlugin.java:47)
```

日志没有 Applied SWAY Forge model delegation patch，也没有 [foliage-diag] 样本。第 1321 行仍有 SWAY 的 wrapped=388。这次可以确认的是 **1.0.0 在目标结构检查阶段跳过，新增的 Forge 委托没有应用**。错误来自补丁使用 getClassNode(target, false)，不是 SWAY 类缺失或版本不匹配。之前的静态委托检查没有覆盖真实 ModLauncher 服务的这项限制。

1.0.1 保留三条委托修复，改为通过 LoadingModList.getModFileById("sway").getFile().findResource(...) 获取所属模组的类资源，使用 Files.newInputStream + ASM ClassReader 只读结构。该路径不加载 SwayModel、不进入 Mixin 转换管线，也不关闭 Forge 管理的文件系统；缺少资源或结构不匹配时仍跳过并记录原因。相关 ModFile.findResource → SecureJar.getPath 路径已核对本机 Forge 字节码。

新增 verifyGuard 使用本机 Mixin 0.8.5 直接复现原异常，再由实际 SWAY JAR 的 SecureJar union 路径运行相同的生产读取器，验证重复读取、文件系统仍打开和资源缺失处理。独立验证进程需要的 java.lang.invoke 模块开放参数仅加在该 JavaExec 任务上，不是客户端安装要求。匹配的 Mixin 0.8.5 源码也明确拒绝 runTransformers=false：
https://github.com/SpongePowered/Mixin/blob/0.8.5/src/modlauncher/java/org/spongepowered/asm/launch/MixinLaunchPluginLegacy.java

以上检查不等于实际 Forge 启动和 Mixin 应用验证。最终缓存链、GPU 标记及视觉结果仍须在 1.0.1 的游戏日志和画面中确认。

## 实物调用流程

### 烘焙、包装与缓存

- SWAY ForgeClientEventSubscriber.onModifyBakingResult(ModelEvent.ModifyBakingResult) 对 SwayAPI.isInteractive(block) 的模型包上 SwayModel，监听注解没有额外优先级。
- IF ForgeFoliageHooks.register(IEventBus) 将 keepPlainModels 注册到本模组总线的 HIGHEST，将 wrapModels 注册到 LOWEST。
- keepPlainModels 清空并暂存 GpuFoliageSplit.mayWave 对应的原模型；wrapModels 以当时 models.get(id) 为内部模型创建 ForgeFoliageModel，并为每个候选状态 registerGpuModel(state, plain)，最后清空 PLAIN_MODELS。
- wrapModels 还会对最外层 SwayModel 做一个特定的 isPushedOnlyBlockModel 判断并解包，条件是可交互但没有形变贡献。此逻辑不是通用的包装顺序保证。
- Forge 47.3.12 的 ForgeHooksClient.onModifyBakingResult 调用 ModLoader.postEvent。匹配 fmlcore 字节码显示 postEvent 按 ModList.forEachModInOrder 向每个模组派发，故单模组总线内部的 HIGHEST/LOWEST 不提供跨模组的先后保证。
- ModelManager.loadModels 在 onModifyBakingResult 返回后，立即把模型表按每个 BlockState 复制成新的 IdentityHashMap 缓存。
- 随后的 ModelManager.apply 先安装 bakedRegistry，派发 BakingCompleted，然后 blockModelShaper.replaceCache(reloadState.modelCache)。实例使用的 Forge 47.4.10 缓存应用字节码也核实了相同的顺序。因此仅在 BakingCompleted 替换模型表里的对象，会晚于状态模型缓存的复制，不能保证区块使用该修改。

最终缓存链可能为 ForgeFoliageModel -> SwayModel -> 原模型，也可能为 SwayModel -> ForgeFoliageModel -> 原模型；第三方包装器还可能位于其他位置。现有日志没有最终链，实际顺序仍待默认关闭的诊断确认。

### 静态模型的抑制

准确签名：

- ForgeFoliageModel.getModelData(BlockAndTintGetter, BlockPos, BlockState, ModelData): ModelData
- ForgeFoliageModel.getRenderTypes(BlockState, RandomSource, ModelData): ChunkRenderTypeSet
- ForgeFoliageModel.getQuads(BlockState, Direction, RandomSource, ModelData, RenderType): List

getModelData 只有在 isFoliageAt(level,pos,state) && leaveToGpu(level,pos) 时，才在输入 ModelData 的 derive() 上添加 LEFT_TO_GPU。getRenderTypes 看到该属性后返回 none()；五参数 getQuads 也返回空列表。雪覆盖有额外条件，本补丁保留该代码。

leaveToGpu 明确识别原版 RenderChunkRegion，以及类名 org.embeddedt.embeddium.render.world.WorldSliceLocal。后续 decide 判断 GPU 区域与 GPU_READY 等交接状态，控制静态网格移交；不是对所有植物无条件隐藏。

### SWAY 缺失的委托

当前 SwayModel 直接 implements BakedModel，父类为 Object，private final BakedModel parent。它只声明三参数 getQuads，所有分支调用 parent 的三参数 getQuads。没有声明 getModelData、getRenderTypes、五参数 getQuads。

Forge IForgeBakedModel 默认 getModelData 直接返回传入值；默认 getRenderTypes 从方块默认渲染层取值；默认五参数 getQuads 回退到 this.getQuads(state, side, rand)。因此在 SwayModel -> ForgeFoliageModel 结构中：

1. 内层 IF 的 getModelData 不会被调用，LEFT_TO_GPU 无法写入。
2. 查询渲染层也不经过 IF，区块仍得到普通植物渲染层。
3. 即使其他代码提供了该属性，默认五参数路径仍转成 SWAY 三参数，再调用 IF 继承的三参数路径，绕开 IF 五参数的空几何逻辑。

### Embeddium 与 GPU 绘制

Embeddium 0.3.31 的 ChunkBuilderMeshingTask 通过 BlockRenderCache.getBlockModels() 返回的 BlockModelShaper 获取已缓存的方块模型；调用 model.getModelData(context.localSlice(),pos,state,snapshot)，接着 model.getRenderTypes(state,random,modelData)。在 ModelData 取得之后还调用 UnwrappableBakedModel.unwrapIfPossible；它只处理实现该接口的模型，当前 IF/SWAY 包装器并未实现。每个 layer 被写入 BlockRenderContext 并交给 BlockRenderer。其 getGeometry 调用 model.getQuads(state,face,random,ctx.modelData(),ctx.renderLayer())。

IF 的 registerGpuModel 保留用于 GPU 几何生成的模型。GpuFoliageRenderer 的 section 构建使用 GpuFoliageSplit.modelFor(state, cachedFallback)，再经 tesselate 生成 GPU 几何；ForgeFoliageHooks 在 RenderLevelStageEvent.AFTER_CUTOUT_BLOCKS 调用 GpuFoliageRenderer.draw。SWAY 的区块包装与这条 GPU 绘制路径同时存在时，静态抑制失效能解释两份模型并存。

FoliageSettings.gpuRenderer() = values.gpuRenderer || !cpuRendererAvailable()；cpuRendererAvailable() 在 embeddium 或 rubidium 存在时为 false。因此 gpuRenderer 配置为 false 也不保证改走 CPU。

### 普通植物与树叶

草由 SWAY 注册为可交互方块，会进入其模型包装流程。IF 的 mayWave/waves 也覆盖 WavingWhitelist；树叶由其 LEAVES 分组、leavesWave、画质和其他专用条件控制，不要求每种树叶都经 SWAY 包装。所以普通植物受外层 SWAY 截断而部分树叶正常，与此机制相容。具体资源包/其他模组改变后的树叶链仍须诊断，不能静态断言每一种树叶都不受影响。

### FWA

FWA 1.2.31 的 fr.madu59.fwa.mixin.sodium.BlockRenderContextMixin 针对 Embeddium BlockRenderContext.update 在 TAIL 注入，仅在 FancyWorldAnimationsClient.shouldCancelBlockRendering(pos) 为真时，将 context.model 替换为 RenderHelper.getInvisibleModel(model)。没有看到它创建 IF/SWAY 的烘焙包装，也没有由此推导出它是重复植物的直接原因。本补丁不修改该类，其动画和取消区块绘制行为仍需回归。

## 补丁机制与范围

使用一个仅客户端、双依赖和准确版本守卫的 @Pseudo Mixin，为现有 SwayModel 增加三个缺失的 Forge 扩展方法。getModelData/getRenderTypes 原样委托 parent。五参数 getQuads 先委托 parent 的同一重载，再在非空几何和有效 SWAY 上下文时调用原 resolveSwayPos、SwayEngine.get 和原 transformQuads，保留强度阈值、插值、行为管线和顶点形变。

不重排模型，不添加新包装器，不替换模型表或缓存，不修改任何原 JAR，不扫描每帧模型链。补丁在类加载时应用，早于模型实例和缓存创建，故不依赖跨模组烘焙优先级。资源重载中每个新 SwayModel 自动具有相同委托，不保留旧模型引用。

守卫检查 Minecraft/IF/SWAY 精确版本、目标继承结构、parent 字段、构造器、两个私有形变方法和三个 Forge 扩展方法是否尚未实现。缺依赖/不兼容版本/不兼容结构时给出原因并跳过。其他模组若在同一类转换阶段另外注入同名方法，仍可能产生 Mixin 冲突，需要新增兼容性核对。

补丁不使用 MixinExtras。不存在基于猜测的 @Inject 字节码位置或 ordinal；这是给目标类增加缺失接口覆盖方法。所有外部稳定名字均 remap=false；调用的正常 Minecraft 成员仍经 ForgeGradle reobfJar 重映射。

## 结论的边界

**已确认的代码缺陷**是 SWAY 当前包装器不转发三条 Forge 扩展路径，且 Embeddium 正好使用这些路径。**代码推导**是：当 SWAY 位于 IF 外层时，该缺陷会绕过 GPU 静态抑制并允许重复模型。**尚未运行确认**的是本实例最终缓存链、该条件是否为当前全部视觉问题的唯一原因，以及补丁后 GPU 交接/光影/FWA 是否完全正常。

公开参考：

- https://github.com/Kart0/mc2-interactivefoliage
- https://github.com/RazorPlay01/SWAY
- https://github.com/maDU59/FancyWorldAnimations
- https://github.com/Kart0/mc2-interactivefoliage/issues/34

IF/SWAY/FWA 项目和 Issue 网页在初次调查中未能成功取回正文。用户提供的 Issue #34 线索报告相似现象，列表包含 IF、SWAY、Embeddium，未列 FWA；仅为相似案例，没有作为本实例根因证明。
