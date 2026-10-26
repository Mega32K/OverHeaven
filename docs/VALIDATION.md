# 构建与验收记录

日期：2026-10-20（Asia/Shanghai）。

## 运行反馈：1.0.0 未应用

用户的 2026-10-20 latest.log 确认 1.0.0 因 ModLauncher 拒绝 getClassNode(target, false) 而被跳过。原静态检查没有覆盖该服务限制。本次修复该读取路径；以下通过项均对应 **1.0.1**，不表示旧版已通过游戏验收。

## 1.0.1 已通过

- 使用 Microsoft JDK 17.0.18、Gradle 8.8、ForgeGradle 6.0.54，保留 Minecraft official 1.20.1 与 Forge 47.3.12。
- 实例准确版本的 6 个重点类已通过 javap -p -c -s、Vineflower 1.10.1 和匹配 Forge 源码/字节码核对。另复核实例 Forge 47.4.10 的缓存安装顺序。
- compileJava、processResources 通过。
- verifyCompatibility：**91 项**实际 JAR 字节码、符号、版本与客户端/可选依赖调用边界检查通过。
- verifyDelegation：ModelData 对象身份与原属性保留、GPU 标记保留、渲染层数据转发、GPU 空几何、后备非空几何与准确调用次数通过。此检查直接执行补丁方法体，不声称验证游戏内的 Mixin 应用或实际动画。
- verifyGuard：**38 项**针对性回归检查通过。直接调用实际 Mixin 0.8.5 重现原 IllegalArgumentException；从实际 SWAY SecureJar union 路径用生产读取器核对结构、重复读取、缺失资源及文件系统不被关闭；检查 plugin 不再调用 MixinService/getBytecodeProvider/getClassNode。此项运行独立 Java 验证进程，没有启动 Minecraft。
- verifyArtifact：**45 项**发行包检查通过，覆盖 Java 17 class major 61、展开后的元数据、客户端 Mixin 列表、plugin、manifest、refmap、正常 Minecraft 调用的生产 SRG 重映射，以及未内嵌原模组类或 JAR。
- reobfJar 与完整 build 成功，最终日志 BUILD SUCCESSFUL，16 actionable tasks。
- 资源重载静态检查：补丁不创建模型包装器、不重写模型表、不缓存模型实例；诊断仅保留字符串样本和 ModelProperty 字段，最多处理 4 次重载。不存在补丁自身的旧模型引用缓存或重复包装路径。
- 专用服务器静态边界：公共入口没有客户端/可选模组调用；两个诊断订阅器都有 Dist.CLIENT，Mixin 位于 client 数组，plugin 先检查 FMLLoader.getDist，再检查依赖/版本，最后才读取可选目标字节码。
- 模组缺失、版本不支持、parent/transformQuads 不匹配、上游已实现 Forge 扩展方法的守卫分支通过无游戏检查；尚未以实际 Forge 客户端/专服启动验证。
- 使用修改前快照与当前文件执行 git -c core.autocrlf=false diff --no-index --check，未输出空白错误；--no-index 的退出码 1 表示两份快照确有差异。未初始化 Git 仓库、创建分支或提交。
- 五个原模组 JAR 在调查前后的 SHA-256 一致。本次操作只读实例配置，没有安装或替换实例中的补丁，没有启动游戏或发布。日志中的 1.0.0 游戏运行来自用户测试。

完整构建日志：audit/revision-1.0.1/build.log。针对性检查日志：audit/revision-1.0.1/targeted.log。

最终命令（项目根目录）：


```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\ms-17.0.18'
.\gradlew.bat --offline build '-Dnet.minecraftforge.gradle.check.certs=false' --console=plain
```

本机联网初次解析遇到仓库响应慢以及 ForgeGradle 主机证书预检失败。依赖随后从官方仓库解析，最终使用已缓存依赖完成离线构建。命令行的 check.certs=false 只跳过 ForgeGradle 的主机预检，没有把关闭 TLS 校验写入项目配置，也未修改 JVM truststore。普通网络环境先使用 README 的正常构建命令。

构建过程仅有已弃用 API 注释、MixinGradle 没有 Eclipse APT 插件、终端功能不可用等提示，无 Java 编译错误。

## 产物

- 路径：E:/CollapsingWorld/System-e/OverHeaven/mod/build/libs/overheaven_foliage_compat-1.20.1-forge-1.0.1.jar
- 大小：20521 bytes
- SHA-256：2760117941eb99509212fca903296c6932ab933d15d93f509a34193dda3d5cfb
- 同目录附 .jar.sha256 校验文件。
- 构建/调查本机日志在 audit/，未打入发行包。可分发源码、构建配置与本目录证据说明即可，不应附带反编译的第三方源码。

## 1.0.1 待游戏内验收（全部未执行）

先手动用 1.0.1 替换 1.0.0 补丁，检查日志出现 Applied SWAY Forge model delegation patch，并为本次复测开启 README 所列 JVM 诊断参数。使用同一世界的测试副本，放置草、蕨类、蒲公英等花、双格高草/向日葵（上下半部）及橡树叶。记录补丁有无时的同一位置画面；诊断按 README 手动启用。

| 场景 | 验收内容 | 状态 |
| --- | --- | --- |
| 主要复现：Embeddium + IF + SWAY | 静止时仅一份正常植物；GPU 已接管时 LEFT_TO_GPU=true、空渲染层或空扩展几何；最终缓存模型链可见 | 未执行 |
| 草、蕨、花 | 实体靠近/碰撞时交互形变仍发生；原位置无残留静态副本；离开后正常恢复 | 未执行 |
| 双格植物 | 上下半部无重复、无断层、无只隐藏一半，SWAY 锚点行为保留 | 未执行 |
| 树叶 | 正常画质与 IF 叶片风动保留，没有全部隐藏、重复或永久空洞 | 未执行 |
| GPU 区域内外 | 近远距离交接、实体离开、移动跨区块后无重复或缺失 | 未执行 |
| 区块重载 | 卸载/重新加载区块，或 F3+A 后仍正常 | 未执行 |
| 资源重载 | 连续 F3+T 后正常，模型链不增长，不持有旧模型；诊断达到预算后停止 | 未执行 |
| Oculus：关闭光影 | 当前主要配置，交互与区块抑制正常 | 未执行 |
| Oculus：开启光影 | 使用现有光影包，几何/阴影/交互/资源重载正常，无重复与整片消失 | 未执行 |
| FWA 安装 | 植物行为正常；FWA 自身生长/移动/方块动画及取消静态绘制仍正常 | 未执行 |
| IF 缺失，SWAY 存在 | 仅补丁守卫跳过，日志明确，无补丁引起的可选类加载错误；原模组依赖自行满足 | 未执行 |
| SWAY 缺失 | 补丁跳过；若 IF 自身不能独立启动，应同时移除依赖它的原模组后验证补丁安全边界 | 未执行 |
| 两个目标均缺失 | 补丁无操作，客户端正常启动 | 未执行 |
| 不支持版本 | 明确警告并跳过，不能静默声称已修复 | 未执行 |
| 专用服务器 | 补丁自身可加载但不应用客户端 Mixin，不加载渲染诊断 | 未执行 |

实际根因是否已完全闭环，以第一项最终模型链和补丁前后画面对照为准。没有游戏启动验收前，不宣称已解决本实例所有植物问题。
