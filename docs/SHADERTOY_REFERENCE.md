# Shadertoy lstSRS 参考改造（2026-10-26）

## 参考核对与边界

用户指定：https://www.shadertoy.com/view/lstSRS 。已使用 Codex 内置浏览器打开，原站先进入 Cloudflare 自动验证，随后显示 ERR_CACHE_MISS，未取得原站编辑器代码、原作者身份或完整通道清单。

随后在同一个内置浏览器中查看了 https://blackhole.misterprada.com/ 的动态画面。其公开源码 https://github.com/MisterPrada/black-hole 的 README 明确将上述 lstSRS 链接列为 Original Shader。本次逐文件检查固定提交 62b0fe17e5b640e35adebc433326977aa0164e25 的 buffer1–4、render 及 JS 通道绑定。这能确认该公开移植的实现，不能证明它与原站当前代码完全相同。

观察到的画面是白热宽亮弧、倾斜薄盘、暖色尘埃以及柔光覆盖的暗核。公开移植以 200 步弯曲射线、多尺度乘性噪声区分气体发光与覆盖率，并使用多级 HDR 辉光和显示转换。它也包含相机控制、时间累积和独立星空；这些部分不能直接代替 Minecraft 世界输入。

内置浏览器资源清单确认输入为 noise.png 与 gas.jpg；已下载到临时浏览器资源目录并检查。gas.jpg 实际是岩石状纹理。项目未附带能明确覆盖这些素材的授权说明，因此发行包使用本项目新生成的采样数据；没有将下载纹理或参考源码放进模组 JAR。没有在未核实的情况下给整个项目套用第三方许可。

本次实现是独立编写的视觉近似，保留原有有限弯曲求解器。没有宣称精确广义相对论、原站源码直移植或像素级复刻。

## 2026-10-26 后续核实与改造

后续已在内置浏览器打开 lstSRS 原站并查看 Buffer A、Buffer B 和 Image。页面显示 Gargantua With HDR Bloom / sonicether；直接核对了 GasDisc、局部 Haze 与多尺度 HDR 辉光。下文是首次改造的历史记录；最新实现已改为自有金字塔，见 [DISK_BLOOM.md](DISK_BLOOM.md)。本轮没有导入参考源码或外部纹理。

## 首次落地内容（历史）

- field.glsl：盘面发光和覆盖率分开计算，加入分层尘埃、径向细纹、内外温度和原有运动参数。每个射线与薄层相交区间使用两个积分点，继续按光程积累并按透明表面拆分前后两段。保留核心前侧盘光和后侧截断。
- disk_detail.png：自有 512×512 周期数据纹理，RGBA 存储分形云、脊状结构、细颗粒与不透明 alpha。一次上传并生成完整 mip 链；显式足迹选择 LOD，包含径向轨道剪切，避免在非一致控制流中使用隐式导数。角向重复次数是整数，跨 atan 接缝连续。
- GlDiskTexture：通过现有资源管理器读取材质，仅在有可见黑洞、初始化渲染器时创建。上传时隔离 PBO 和 unpack 布局，完整恢复绑定；重载、切换世界、退出沿用 renderer.close 释放，再使用新资源。
- GlState：额外保护纹理/采样器单元 10。新增资源不会逐帧上传、分配或回读。
- blur.fsh：低分辨率稠密归一化 Gaussian 柔光，避免稀疏大跨度采样形成离散亮环。窄高光由直接盘光保留。
- composite.fsh：原版路径对 HDR 光使用保留高光层次的显示曲线。Oculus 继续接收 HDR 光，由宿主光影包做自己的显示转换。

材质生成器是 tools/generate-blackhole-texture.mjs，仅使用 Node 内置模块；运行 node tools/generate-blackhole-texture.mjs 即可重建相同 PNG，正常 Gradle 构建无需 Node。SHA-256：f8c176a82f596f57300d710c013ea9a3ed48ec481d9f9ac61705559335a48b38。

## 保留的兼容边界

没有改动植物委托补丁、命令/网络/存档、原版透明采集点、Oculus 前后段合成顺序或 EndingLibrary 衔接；没有注册或替换全局 PostChain。单纯增加材质细节不能证明兼容真实光影运行。

维持 Minecraft 1.20.1、Java 17、Forge 47.4.10 与 com.mega 包名；依赖继续使用固定远程坐标与 ForgeGradle fg.deobf，无本地 JAR、无 compileOnly。Oculus 仍只支持已核对哈希的原版 ComplementaryUnbound_r5.9.3.zip，不扩大到 Euphoria 或其他包。

不复制参考演示的星空：背景仍来自 Minecraft 采集的实际场景。没有直接搬用其时间反馈、固定相机、200 步预算或不受地形约束的全屏绘制。

## 验证与复查

生产 GLSL 在隐藏的独立 OpenGL 3.3 上下文编译链接，未启动 Minecraft。测试覆盖核心、整墙/墙边、透明遮挡和前后段、水面折射夹具、角向接缝、动画推进、采样亮度收敛、材质上传时的 PBO/unpack 状态、单元 10 恢复、完整 mip 链及旧资源释放。预览直接执行生产 composite 的原版显示路径，并使用各质量档的实际分辨率。

详细构建、同一上下文的新旧 shader 计时和指纹见 BLACK_HOLE_VALIDATION.md 与 audit/shadertoy-reference-2026-10-26/。预览图 blackhole-preview.png 及该审计目录的 GPU 图像都是固定场景夹具，不是游戏截图。

游戏内仍要检查：正面/侧面/背面与近距离；实体经过；墙边、树叶孔洞、玻璃、水上/水下；关闭光影及原版 Complementary；终焉效果启停；FWA 动画；资源重载、窗口缩放、换维度和退出。保留 docs/BLACK_HOLE_VALIDATION.md 的原有验收清单。未自动安装或启动游戏。
