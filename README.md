# ResourceManager

在游戏内直接调试资源包的 Fabric 客户端模组（Minecraft 1.21.4 / Fabric Loader）。

按 **F8**（可在「选项 → 控制」里改键）打开 GUI：像文件管理器一样浏览所有已启用的资源包，把某个包里
不想要的贴图 / 模型 / 声音直接禁用掉，让加载顺序更靠前的资源包覆盖它；也可以直接调 `sounds.json`
里每个音效事件的音量与音高并当场试听；还能直接修改原版/资源包里的文本（`lang` 键值），改完立刻在游戏里生效。

## 界面

```
┌ 工具栏  标题 · 搜索框 · 侧栏开关 · 重载资源包 · 关闭 ─────────────────────┐
├──────────────┬──────────────────────────────┬────────────────────────────┤
│ 过滤器       │ 资源树                       │ 检查器                     │
│  全部        │  ▾ 资源包            #2 1 off │  名称                      │
│  贴图        │    ▾ 命名空间        554 off  │  命名空间:路径             │
│  声音        │      ▾ 目录                   │  所属资源包 / 类型 / 状态  │
│  UI          │        • 文件        ♪ 事件   │  ┌────────┐ 贴图预览       │
│  模型/文本/  │                              │  │ 16x16  │ 16x16          │
│  其它        │                              │  └────────┘                │
│ 已禁用 (3) 全屏│                             │  覆盖来源（provider 列表） │
│  列表        │                              │  音量 ▬▬▬▬▬ 1.00           │
│ [清空全部]   │                              │  音高 ▬▬▬▬▬ 1.00           │
│              │                              │  [重置]      [试听]        │
├──────────────┴──────────────────────────────┴────────────────────────────┤
│ 状态栏  25142 files · 58 packs · 1669 sound events · 3 disabled │ 提示     │
└──────────────────────────────────────────────────────────────────────────┘
```

窗口太窄时侧栏自动收起（点工具栏上的「侧栏」按钮可再打开），树与检查器始终并排、互不重叠。

三个面板各有分工，只有需要的时候才出现对应的控件：

- **侧栏**：分类过滤 + 已禁用列表（点标题栏的「全屏」按钮可把它铺满整个内容区，见下）。
- **中间**：资源树。选中什么就检查什么。
- **右侧检查器**：随选中项切换形态 —— 文件 → 信息 + 贴图预览 + 禁用按钮；音效事件 → 音量/音高滑条
  + 试听；`lang` 文件 → 文本编辑器。

### 已禁用列表的全屏视图

侧栏里「已禁用 (n)」标题栏右侧有一个「全屏」按钮，点开后整块内容区都变成禁用列表：

```
┌ 已禁用 (3)                        [全部清除] [返回] ──────────────────────┐
│ Squareful 方纹v3.8 …   minecraft:textures/block/redstone_block.png        │
│ Squareful 方纹v3.8 …   minecraft:textures/gui/widgets.png                 │
│ CozyUI+ v1.10 …        minecraft:textures/gui/sprites/hud/heart.png       │
└──────────────────────────────────────────────────────────────────────────┘
```

行内分成「资源包 | 资源」两列便于扫读，单击跳到该文件，双击（或选中后按 `Enter`）直接恢复，
工具栏的搜索框此时会过滤列表，`ESC` 返回三面板视图（不会关掉 GUI）。侧栏太窄放不下列表时，
全屏按钮依然可用（它只依赖侧栏本身）。

### 文本编辑（原版文本也是资源）

任意 `lang/*.json` 都是一棵树节点（过滤到「文本」分类或直接搜 `en_us.json`）。选中它，检查器就变成
文本编辑器：上面一行是键过滤框，中间是该文件所有文本键（有改动的键右边带 ✎），点一行即可编辑，
下面填新文本并点「应用」——游戏内所有引用这个键的文本立刻变成新值（连已经渲染过的缓存文本也会刷新）。
「还原」移除该键的覆盖，恢复资源包原本的文本。

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
  试听是**相对声源、无衰减**的，而且会告诉你到底有没有响：状态栏显示「正在播放 xxx」，
  若事件解析不出来（被更高优先级包的 `sounds.json` 覆盖、文件缺失、被标记为静音）会直接写明原因，
  而不是像以前那样静默失败。
- **贴图预览**：选中任意图片文件，检查器顶部按整数倍放大渲染该文件的贴图（像素画不会被糊掉），
  并标出原始尺寸；像素来自该文件所属的资源包，所以「已被覆盖」的文件也能看到它自己的图。
- **文本编辑**：见上，可改原版文本并即时生效，改动存在配置里，重启后依然生效。
- **重载**：状态栏提示「N 项待应用」时点「重载资源包」，贴图 / 模型 / 声音改动立刻生效。
- 结果持久化在 `config/resourcemanager.json`（`disabled` + `sounds` + `texts` 三张表），重启后仍然有效。

## 键盘 / 鼠标

| 操作 | 说明 |
| --- | --- |
| `F8` | 打开 / 关闭 GUI |
| 单击 | 选中行（已禁用列表：跳到该文件） |
| 双击 / `→` | 展开节点（文件则切换禁用）；已禁用列表里双击 = 恢复 |
| `←` | 折叠节点，已在最外层则回到父节点 |
| `↑` `↓` | 移动选择（自动滚动到可见）；全屏禁用列表里同样是移动高亮 |
| `Enter` | 切换选中项的禁用状态；全屏禁用列表里 = 恢复选中项 |
| `ESC` | 关闭 GUI；全屏禁用列表里先返回三面板视图 |
| 搜索框内 `↓` / `Enter` / `Tab` | 结束输入，把焦点交给资源树 |
| 键过滤框内 `↓` / `Enter` | 结束输入，选中第一条文本键 |
| 文本值框内 `Enter` | 应用这条文本改动 |
| 滚轮 | 列表滚动；指针在滑条上时微调数值 |
| 拖动滚动条 | 快速跳转 |

## 工作原理

| 模块 | 作用 |
| --- | --- |
| `ResourceIndex` | 从运行时 `ResourceManager` 建索引：按加载顺序列举资源、解析 `sounds.json` 事件、记录每个 location 的 provider 链，并保留 packId → `PackResources` 映射供按包读文件 |
| `ResourceNode` / `ResourceTree` | 树节点模型与「扁平化成行 + 搜索/过滤 + 展开状态」 |
| `ui/`（`Ui` `TreeView` `ScrollList` `UiButton` `UiSlider` `DisabledList` `TextKeyList` `PreviewTexture`） | 面板配色与几何、可滚动列表、树视图、滑条、禁用列表、文本键列表、贴图预览 |
| `ResourceManagerScreen` | 三段式布局（工具栏 / 侧栏+树+检查器 / 状态栏）、随选中项切换的检查器、全屏禁用列表、文本编辑器、输入处理 |
| `LangText` | 按包读 `lang/*.json`、列出键值、查当前生效文本 |
| `SoundPreview` | 判定音效事件能否播放（未知 / 空 / 故意静音）并构造试听用的 `SoundInstance` |
| `mixins/FallbackResourceManagerMixin` | 包装每个 pack：被禁用的文件在该包里直接不出现，回退链自然落到下一个包 |
| `mixins/SoundInstanceMixin` | 按音效事件 id 乘算音量 / 音高 |
| `mixins/ClientLanguageMixin` | `ClientLanguage.getOrDefault/has` 优先返回文本覆盖值 |
| `mixins/TranslatableContentsMixin` | 文本改动后让已缓存的 `TranslatableContents` 重新解析，从而即时生效 |
| `ResourceManagerConfig` | 读写 `config/resourcemanager.json`（`disabled` / `sounds` / `texts`），packId 归一化（去掉 `file/` 前缀） |

### 1.21.4 API 上的几个坑（代码内有注释）

1. `ResourceManager.listResources(path, …)` **拒绝空路径**（`FileUtil.decomposePath` 只接受真实路径段），
   因此一个命名空间无法一次列完。索引的做法是：列举一组已知 asset 根目录 + 从 `resourcepacks/`
   里扫描出来的根目录，再单独探测 `sounds.json` 这类命名空间根文件。
2. `AbstractWidget.setRectangle` 的参数顺序实际是 `(width, height, x, y)`，与形参名相反，用它定位会
   「宽高与坐标互换」。所有控件都经过 `Ui.place(widget, x, y, w, h)` 定位。
3. `Screen.render` 会先铺一次背景，若在子类里调用 `super.render` 它会把自绘的面板盖掉；这里改成手动
   遍历 `children()` 绘制控件。
4. `GuiGraphics.blit` 只接受 `Function<ResourceLocation, RenderType>` 作为首参，贴图预览用
   `RenderType::guiTextured` 走 GUI 着色器。
5. 文本覆盖要同时处理两条路径：`ClientLanguage.getOrDefault/has`（新解析的文本）与
   `TranslatableContents.decomposedWith` 缓存（已经渲染过的组件），所以另有一个 mixin 在版本号变化时
   把它置空。

## 构建 / 运行

```powershell
.\gradlew.bat build        # 产物：build/libs/resourcemanager-1.0.0.jar
.\gradlew.bat runClient    # 开发环境直接启动客户端（把资源包放进 run/resourcepacks）
```

依赖：Java 21、Minecraft 1.21.4、Fabric Loader ≥ 0.16.14、Fabric API。

## 自动验证（可选）

`src/client/java/com/abelian/client/verify/VerifyHarness.java` 是一个自检驱动：它会用快捷键打开 GUI、
注入按键与鼠标点击，断言布局不重叠 / 不越界、禁用后确实回退到下一个包、重新启用后恢复、滑条点击确实
写入配置、分类过滤后树里只剩 `textures/` 路径、lang 文件被识别为文本资源且改动即时生效（改完还能还原）、
材质缩略图确实渲染出尺寸、禁用栏能全屏展开并用 ESC 返回，并把截图写到 `run/screenshots/`、报告写到
`run/verify-report.txt`，运行结束时把配置还原成本次运行前的状态（disabled / sounds / texts 三张表都会还原）。
它在 `fabric.mod.json` 里已注册，但**只有设置环境变量时才启动**，平时游玩完全不生效。

```powershell
$env:RESOURCEMANAGER_VERIFY = "1"; .\gradlew.bat runClient     # 跑完会自动退出客户端
```

最近一次运行：21 项断言全部 PASS，11 张截图，用时约 1 分钟。报告里会逐行列出 `PASS` / `FAIL` / `SKIP`，例如：

```
PASS: a disabled file is no longer served by that pack (file/Squareful ….zip -> vanilla)
PASS: re-enabling restores the original provider
PASS: the key binding opened the GUI
[compact] PASS: visible widgets do not overlap (0)
[wide]    PASS: the tree panel starts right of the sidebar controls (sidebar right=152, tree x=164)
PASS: the volume slider changed the stored tuning
PASS: the preview sound is really audible (the sound engine reports the instance as active)
PASS: the disabled column expanded to the whole window
PASS: ESC returns from the expanded view and keeps the GUI open (screen=ResourceManagerScreen)
PASS: the lang file is recognised as a text resource (path realms:lang/en_us.json)
PASS: the edited text takes effect immediately, including on cached components
PASS: the texture category only keeps textures (minecraft:textures/block/acacia_door3d_bottom.png)
PASS: the inspector renders a thumbnail of the selected texture (16x16)
```

> 这个 harness 会随 jar 一起发布，但入口第一件事就是检查环境变量，没设置时立刻返回，所以正常游玩时它只是多
> 一个不会被执行的类。要在自己的客户端里复现验证，直接 `$env:RESOURCEMANAGER_VERIFY = "1"` 再启动即可。

## License

CC0-1.0
