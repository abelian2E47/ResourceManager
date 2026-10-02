# ResourceManager

在游戏内直接调试资源包的 Fabric 客户端模组（Minecraft 1.21.4 / Fabric Loader）。

按 **F8**（可在「选项 → 控制」里改键）打开 GUI：像文件管理器一样浏览所有已启用的资源包，把某个包里
不想要的贴图 / 模型 / 声音直接禁用掉，让加载顺序更靠前的资源包覆盖它；也可以直接调 `sounds.json`
里每个音效事件的音量与音高并当场试听。

## 界面

```
┌ 工具栏  标题 · 搜索框 · 侧栏开关 · 重载资源包 · 关闭 ─────────────────────┐
├──────────────┬──────────────────────────────┬────────────────────────────┤
│ 过滤器       │ 资源树                       │ 检查器                     │
│  全部        │  ▾ 资源包            #2 1 off │  名称                      │
│  贴图        │    ▾ 命名空间        554 off  │  命名空间:路径             │
│  声音        │      ▾ 目录                   │  所属资源包 / 类型 / 状态  │
│  UI          │        • 文件        ♪ 事件   │  覆盖来源（provider 列表） │
│  模型/文本/  │                              │  音量 ▬▬▬▬▬ 1.00           │
│  其它        │                              │  音高 ▬▬▬▬▬ 1.00           │
│ 已禁用 (n)   │                              │  [重置]      [试听]        │
│  列表        │                              │                            │
│ [清空全部]   │                              │                            │
├──────────────┴──────────────────────────────┴────────────────────────────┤
│ 状态栏  25142 files · 58 packs · 1669 sound events · 4 disabled │ 提示     │
└──────────────────────────────────────────────────────────────────────────┘
```

窗口太窄时侧栏自动收起（点工具栏上的「侧栏」按钮可再打开），树与检查器始终并排、互不重叠。

## 能做什么

- **层级资源树**：资源包 → 命名空间 → 目录 → 文件。`▾/▸` 展开折叠，右侧徽标是该节点下的文件数，
  `off` 表示其中已被禁用的数量，`♪` 是 `sounds.json` 里的音效事件。
- **搜索**：按资源包名 / 命名空间 / 路径 / 文件名过滤，命中的分支自动展开，并显示「命中 / 总数」。
- **分类过滤**：全部 / 贴图 / 声音 / UI / 模型 / 文本 / 其它。
- **禁用单个资源**：选中文件后按 `Enter` 或点「禁用」。禁用的含义是「这个包不再提供该文件」，
  回退链继续往下走 —— 也就是交给加载顺序更靠前的包（检查器里会列出还有哪些包提供同一文件）。
  在资源包 / 命名空间 / 目录节点上是批量禁用，需要点两次确认。
- **音效调试**：选中 `sounds.json` 下的事件后拖滑条调音量 / 音高（0–4 倍，滚轮可 0.05 微调），
  「试听」立即播放，「重置」恢复默认；改动立刻影响游戏内的音效播放。
- **重载**：状态栏提示「N 项待应用」时点「重载资源包」，贴图 / 模型 / 声音改动立刻生效。
- 结果持久化在 `config/resourcemanager.json`（`disabled` 列表 + `sounds` 表），重启后仍然有效。

## 键盘 / 鼠标

| 操作 | 说明 |
| --- | --- |
| `F8` | 打开 / 关闭 GUI |
| 单击 | 选中行 |
| 双击 / `→` | 展开节点（文件则切换禁用） |
| `←` | 折叠节点，已在最外层则回到父节点 |
| `↑` `↓` | 移动选择（自动滚动到可见） |
| `Enter` | 切换选中项的禁用状态 |
| 搜索框内 `↓` / `Enter` / `Tab` | 结束输入，把焦点交给资源树 |
| 滚轮 | 列表滚动；指针在滑条上时微调数值 |
| 拖动滚动条 | 快速跳转 |

## 工作原理

| 模块 | 作用 |
| --- | --- |
| `ResourceIndex` | 从运行时 `ResourceManager` 建索引：按加载顺序列举资源、解析 `sounds.json` 事件、记录每个 location 的 provider 链 |
| `ResourceNode` / `ResourceTree` | 树节点模型与「扁平化成行 + 搜索/过滤 + 展开状态」 |
| `ui/`（`Ui` `TreeView` `ScrollList` `UiButton` `UiSlider` `DisabledList`） | 面板配色与几何、可滚动列表、树视图、滑条、禁用列表 |
| `ResourceManagerScreen` | 三段式布局（工具栏 / 侧栏+树+检查器 / 状态栏）、检查器、输入处理 |
| `mixins/FallbackResourceManagerMixin` | 包装每个 pack：被禁用的文件在该包里直接不出现，回退链自然落到下一个包 |
| `mixins/SoundInstanceMixin` | 按音效事件 id 乘算音量 / 音高 |
| `ResourceManagerConfig` | 读写 `config/resourcemanager.json`，packId 归一化（去掉 `file/` 前缀） |

### 1.21.4 API 上的两个坑（代码内有注释）

1. `ResourceManager.listResources(path, …)` **拒绝空路径**（`FileUtil.decomposePath` 只接受真实路径段），
   因此一个命名空间无法一次列完。索引的做法是：列举一组已知 asset 根目录 + 从 `resourcepacks/`
   里扫描出来的根目录，再单独探测 `sounds.json` 这类命名空间根文件。
2. `AbstractWidget.setRectangle` 的参数顺序实际是 `(width, height, x, y)`，与形参名相反，用它定位会
   「宽高与坐标互换」。所有控件都经过 `Ui.place(widget, x, y, w, h)` 定位。

## 构建 / 运行

```powershell
.\gradlew.bat build        # 产物：build/libs/resourcemanager-1.0.0.jar
.\gradlew.bat runClient    # 开发环境直接启动客户端（把资源包放进 run/resourcepacks）
```

依赖：Java 21、Minecraft 1.21.4、Fabric Loader ≥ 0.16.14、Fabric API。

## 自动验证（可选）

`src/client/java/com/abelian/client/verify/VerifyHarness.java` 是一个自检驱动：它会用快捷键打开 GUI、
注入按键与鼠标点击，断言布局不重叠 / 不越界、禁用后确实回退到下一个包、重新启用后恢复、滑条点击确实
写入配置，并把截图写到 `run/screenshots/`、报告写到 `run/verify-report.txt`，运行结束时把配置还原成
本次运行前的状态。它在 `fabric.mod.json` 里已注册，但**只有设置环境变量时才启动**，平时游玩完全不生效。

```powershell
$env:RESOURCEMANAGER_VERIFY = "1"; .\gradlew.bat runClient     # 跑完会自动退出客户端
```

报告里会列出所有 `PASS` / `FAIL` / `SKIP` 行，例如：

```
PASS: a disabled file is no longer served by that pack (file/Squareful ….zip -> vanilla)
PASS: re-enabling restores the original provider
PASS: the key binding opened the GUI
[compact] PASS: visible widgets do not overlap (0)
[wide]    PASS: the tree panel starts right of the sidebar controls (sidebar right=152, tree x=164)
PASS: the volume slider changed the stored tuning
```

## License

CC0-1.0
