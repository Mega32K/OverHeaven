# 电鳗发动机

通过本机 Blockbench MCP 制作的 Java 方块模型。打开 `electric_eel_engine.bbmodel` 可继续编辑，纹理已嵌入工程。

- 注册名：`overheaven_foliage_compat:electric_eel_engine`。
- 整体尺寸：宽 16、高 12.8、深 9.6 模型单位，即 1 × 0.8 × 0.6 格。几何范围为 X=[0,16]、Y=[0,12.8]、Z=[3.2,12.8]。
- 45 个立方体，按框架、水箱、电鳗、电气结构、标牌分组。电鳗与气泡都是静态模型细节。
- 512×512 无损 PNG 图集，全部 267 个可见面统一为每模型单位 8 像素。71 个按实际面尺寸绘制的 UV 区域只在材质和尺寸相同时复用，周围留两像素扩边。玻璃使用透明像素和蓝色边框、反光；不模拟水体折射。独立 16×16 金属纹理用于破坏粒子。
- 默认正面朝北；方块状态负责水平旋转。物品显示变换保存在模型内。
- 使用 `minecraft:cutout` 普通区块模型，无方块实体、动态渲染器或每 tick 工作。

导出时使用 Blockbench 的 Java 1.9–1.21.5 格式，并保留 `render_type`、纹理引用和显示变换；游戏模型去除编辑器元数据 `format_version`、`texture_size`、`credit`、`groups`。游戏资源位于 `src/main/resources/assets/overheaven_foliage_compat/` 下相应的 `models/block`、`models/item`、`blockstates` 和 `textures/block` 目录。

修改几何后必须重新按面尺寸铺设 UV：纹理像素跨度 = 面的模型单位跨度 × 8，横纵两轴分别计算，不能把整张方形纹理压入细长面。小数模型尺寸允许裁到部分边缘像素，不能把 UV 长宽强行取整后拉伸。金属纹理颗粒、眼睛和闪电图案在实际像素网格上绘制，不按各面的长宽独立缩放。

`gradlew.bat verifyElectricEelUv` 会逐面校验导出模型的横纵像素密度。该检查已作为 `processResources` 的前置任务，异常时停止资源处理，不触发打包。

方块沿用原版红石块的恒定 15 级信号行为；可在红石创造标签页找到，镐采集掉落自身。未指定合成方式，因此没有添加配方。

预览图由 Blockbench 离屏视图输出，不代表 Minecraft 或光影包内的实机效果。
