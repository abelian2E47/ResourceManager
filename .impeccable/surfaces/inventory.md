---
title: ResourceManager 原版物品栏
mode: Operate
target: src/client/java/com/abelian/client/page/InventoryPage.java
platform: Minecraft 1.21.4 Fabric native Screen
status: implemented
---

# 对象资源编辑

用户指定原版物品栏形式。单一网格入口支持全部/物品/方块/生物筛选、中英文名称与 ID 搜索、点击或 Enter 打开对象。实体以刷怪蛋或盔甲架表示。

详情为外观、名称、音效三个直接任务；外观只列贴图，模型、状态与动画元数据统一参与整组切层。名称按翻译键覆盖，声音按事件独立选择与试听。点击覆盖层预览，应用后重载；本地编辑复制当前完整外观并保留已有文件。

灰色原版容器、按钮、输入框与层列表统一。矮窗口通过预览/覆盖层按钮切换控件组。视觉值见 [设计](../../DESIGN.md)，使用与实机证据见 [说明](../../docs/INVENTORY.md)。
