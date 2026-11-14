# ResourceManager 架构

Minecraft 1.21.4 Fabric 原生 Screen，单一物品栏流程。旧文件浏览器、音效工具页、文本工具页、覆盖管理页及其页面宿主、树节点基础设施已移除。

## 主要模块

| 模块 | 职责 |
| --- | --- |
| ResourceManagerScreen | 控件、输入、异步扫描、资源重载和延迟保存 |
| InventoryPage | 注册表对象网格，以及外观、名称、音效三种详情 |
| SubjectCatalog | 模型/物品定义/方块状态引用、装备贴图、实体纹理与相关音效 |
| AppearanceLayers | 一个对象的外观依赖集合、统一来源和内置回退 |
| ResourceIndex | 相关文件到原始资源包提供者的不可变索引 |
| FilteredPackResources | 运行时外观来源过滤及声音事件的局部选择 |
| ResourceWorkspace | 保留已有文件的整组本地外观副本 |
| ResourceManagerConfig | 外观选择、独立音效层/音量/音高、名称覆盖与旧配置兼容 |
| PackSoundPreview | 从指定包字节解码 OGG，并管理异步试听取消 |

## 外观层

关联图包括贴图、物品定义、模型、方块状态、装备定义及 PNG 元数据。界面只列 PNG。选择一个资源包后，每个文件只允许选中包提供；缺文件时使用内置来源，不混用其他目录/ZIP 包。动画元数据跟随图片来源。

共享文件的冲突会合并到已有对象组，避免新选择把另一个已配置对象拆成不同来源。没有冲突的共享内置父模型不触发合并。

本地编辑复制当前外观的完整依赖，已有文件保持不变。选中整组 ResourceManager-Edits 后再重载，模型和贴图均使用这组副本。

## 独立名称和音效

名称始终按对象翻译键覆盖。ClientLanguage 与 TranslatableContents Mixin 根据文本版本使已有组件即时更新。预览只读取实际包含这个键的包。

声音列表以事件为单位，不显示原始 JSON 或重复 OGG 条目。选层时从其他声音定义中移除该事件，仅在选中声明上标记 replace；其他事件保留。音频文件的来源选择独立于外观。SoundInstance Mixin 将音量和音高乘入实际游戏声音；试听从所选包读取声音文件，缺文件时使用内置音频。

## 性能

ResourceIndex 仅扫描 textures、models、blockstates、items、equipment、lang、sounds 和 sounds.json，不构建目录树或读取未启用的磁盘包。原始包索引不会因运行时过滤而丢失其他可选层。

目录按索引版本建立；关联图、JSON 和名称解析缓存复用；双语译名后台预热；音效声明延迟到首次详情时读取。未变化的布局不切换资源层、不解码预览；滑条修改延迟保存，关闭/重载前保存。

## 验证

[InventoryVerify](../src/client/java/com/abelian/client/verify/InventoryVerify.java) 仅在 RESOURCEMANAGER_INVENTORY_VERIFY=1 时注册。真实客户端检查输入、显示文本、所选包音频及重载后的实际资源来源。结果和边界见 [使用与验证](INVENTORY.md)。
