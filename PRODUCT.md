# ResourceManager

ResourceManager 是 Minecraft 1.21.4 Fabric 客户端资源编辑模组，F8 打开原生 Screen。产品方向已由用户指定：原版物品栏、搜索物品/方块/生物、点击进入关联资源，支持试听、修改及资源包覆盖层预览与切换。

当前界面是单一物品栏流程；详情只有外观、名称、音效。外观只显示贴图，模型、物品定义、方块状态、装备定义和动画元数据在内部统一跟随外观层。所选目录包/ZIP 包缺少的部分使用内置资源。名称按对象翻译键覆盖，声音按事件选择来源，二者独立于外观。

旧文件浏览器、独立音效/文本/覆盖管理页及页面宿主代码已移除。保留相关资源发现、名称保存/恢复、贴图本地编辑、音量/音高、试听/停止、覆盖层预览和应用。声音层同时包含声明事件的包和只覆盖相关音频文件的包。

本地编辑使用独立 ResourceManager-Edits 资源包，复制当前完整外观依赖并保留已有文件，原资源包不被修改。共享全局资源 ID 的对象发生来源冲突时保持组内一致。第三方 renderer 的任意动态引用无法静态保证发现。

实现依据：[InventoryPage](src/client/java/com/abelian/client/page/InventoryPage.java)、[SubjectCatalog](src/client/java/com/abelian/client/catalog/SubjectCatalog.java)、[AppearanceLayers](src/client/java/com/abelian/client/catalog/AppearanceLayers.java)。使用和验证见 [说明](docs/INVENTORY.md)。
