# ResourceManager

**在游戏内直接调试资源包的 Fabric 客户端模组（Minecraft 1.21.4）。**

按 **F8** 打开 GUI：像文件管理器一样浏览所有已启用的资源包，把某个包里不想要的贴图 / 模型 / 声音 /
UI 贴图直接禁用掉 —— 这个包就不再提供那个文件，加载顺序中它下面的包接管；也可以直接调 `sounds.json`
里每个音效事件的音量与音高并当场试听；还能预览选中文件的贴图、直接修改 `lang` 文本并立刻在游戏里生效。
所有改动都记在一个配置文件里，重启后依然有效。

[English README](README.md) · [反馈问题](https://github.com/abelian2E47/ResourceManager/issues)

![界面全貌：左侧过滤器与已禁用列表、中间资源树、右侧检查器](docs/screenshot-overview.png)

## 特点

- **文件管理器式布局**：工具栏 / 侧栏 / 资源树 / 检查器 / 状态栏是各自独立的板块，窗口太窄时侧栏自动
  收起，控件永远按内容与窗口尺寸动态计算，不会互相重叠。
- **层级资源树**：资源包 → 命名空间 → 目录 → 文件。右侧徽标是该节点下的文件数，`off` 是其中已被禁用的
  数量，`♪` 是 `sounds.json` 里的音效事件。
- **搜索与分类**：按资源包名 / 命名空间 / 路径 / 文件名过滤，命中的分支自动展开并显示「命中 / 总数」；
  也可以只看 贴图 / 声音 / UI / 模型 / 文本 / 其它。
- **禁用单个资源**：这个包不再提供该文件，回退链继续往下走。检查器会列出还有哪些包提供同一个文件，
  **Next provider** 可以直接跳到下一个提供者。资源包 / 命名空间 / 目录节点上是批量禁用（点两次确认）。
- **音效调试**：每个 `sounds.json` 事件可以单独乘算 0–4 倍的音量与音高，**试听**会真的出声，并且会告诉你
  到底有没有响；**重置**回到 1.00 / 1.00。
- **贴图预览**：检查器按整数倍放大渲染选中的图片（像素画不会被糊掉），并标出原始尺寸。
- **文本编辑**：任何 `lang` 键都能改，改完游戏内立刻生效（连已经渲染过的文本也会刷新），**还原**即恢复资源包
  原本的文本。
- **禁用列表全屏**：DISABLED 栏的「Full」按钮把列表铺满整个内容区，行分成「资源包 | 资源」两列，
  支持鼠标与键盘操作。

## 截图

| | |
| --- | --- |
| ![搜索](docs/screenshot-search.png) | ![全屏禁用列表](docs/screenshot-disabled-full.png) |
| 用搜索词过滤资源树 | 禁用列表铺满整个窗口 |
| ![音效调试](docs/screenshot-sound.png) | ![文本编辑](docs/screenshot-text-editor.png) |
| 调某个音效事件的音量 / 音高 | 编辑某个资源包（或原版）的 `lang` 键 |
| ![贴图预览](docs/screenshot-texture-preview.png) | |
| 预览某个被覆盖的贴图 | |

## 环境要求

| | |
| --- | --- |
| Minecraft | 1.21.4 |
| Fabric Loader | 0.16.14 或更高 |
| Fabric API | 必需 |
| Java | 21 |
| 运行侧 | 客户端；在连服务器的客户端上也能用，所有操作都是本地的 |

## 安装

1. 装好 [Fabric Loader](https://fabricmc.net/use/installer/)，把
   [Fabric API](https://modrinth.com/mod/fabric-api) 放进 `mods/`。
2. 把 [Releases](../../releases) 里的 `resourcemanager-1.0.0.jar` 放进 `.minecraft/mods/`。
3. 启动游戏，按 **F8**（可在「选项 → 控制 → Resource Manager」里改键）。

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

窗口太窄时侧栏自动收起（点工具栏上的「Sidebar」按钮可再打开），树与检查器始终并排、互不重叠。
三个面板各有分工，只有需要的时候才出现对应的控件：左侧是分类过滤 + 已禁用列表，中间是资源树，右侧检查器
跟着选中项切换形态 —— 文件 → 信息 + 贴图预览 + 禁用按钮；音效事件 → 音量/音高滑条 + 试听；
`lang` 文件 → 文本编辑器。

### 禁用的含义

禁用一个文件 = **这个资源包不再提供这个文件**，于是回退链继续往下走，由包里排在它下面（优先级更低）的包
接管。所以最常见的用法就是：把最上面那个包的某张贴图禁掉，下面所有包里的同一张贴图立刻重新显示出来。
检查器里的 **State** 一行会把当前情况说清楚：

- `Active (this pack wins)` —— 这个包赢了加载顺序，现在用的就是它。
- `Shadowed by <包名>` —— 它被上面的包盖住了。
- `Disabled (lower packs can provide it)` —— 你把它禁掉了，现在由下面的包提供。
- `No provider left` —— 所有提供者都被禁掉了。

改动不会写进资源包本身，全部记在 `config/resourcemanager.json` 里，启动时自动应用，
点「重载资源包」立刻生效。

### 已禁用列表的全屏视图

侧栏里「DISABLED (n)」标题栏右侧有一个「Full」按钮，点开后整块内容区都变成禁用列表：

![全屏禁用列表](docs/screenshot-disabled-full.png)

行内分成「资源包 | 资源」两列便于扫读，单击跳到该文件，双击（或选中后按 `Enter`）直接恢复，
工具栏的搜索框此时会过滤列表，`ESC` 返回三面板视图（不会关掉 GUI）。

### 音效调试

选中 `sounds.json` 下的 `♪` 事件后拖滑条调音量 / 音高（0–4 倍，滚轮可 0.05 微调），**试听**立即播放，
**重置**恢复默认；改动立刻影响游戏内的音效播放。试听是**相对声源、无衰减**的，而且会告诉你到底有没有响：
状态栏显示「正在播放 xxx」，若事件解析不出来（被更高优先级包的 `sounds.json` 覆盖、文件缺失、被标记为静音）
会直接写明原因，而不是像以前那样静默失败。

### 贴图预览

选中任意图片文件，检查器顶部按整数倍放大渲染该文件的贴图（像素画不会被糊掉，最大 6 倍），并标出原始尺寸。
像素来自该文件所属的资源包，所以「已被覆盖」的文件也能看到它自己的图。

### 文本编辑（原版文本也是资源）

任意 `lang/*.json` 都是一棵树节点（过滤到「文本」分类或直接搜 `en_us.json`）。选中它，检查器就变成
文本编辑器：上面一行是键过滤框，中间是该文件所有文本键（有改动的键右边带 ✎），点一行即可编辑，
下面填新文本并点「应用」。

![文本编辑器](docs/screenshot-text-editor.png)

游戏内所有引用这个键的文本立刻变成新值（连已经渲染过的缓存文本也会刷新）。「还原」移除该键的覆盖，
恢复资源包原本的文本。

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

## 配置文件

`config/resourcemanager.json`：

```json
{
  "disabled": [
    "Squareful 方纹v3.8 for MC 1.20.2~1.21.8.zip|minecraft:textures/block/redstone_block.png"
  ],
  "sounds": {
    "minecraft:block.note_block.harp": {
      "volume": 1.5,
      "pitch": 0.8
    }
  },
  "texts": {
    "mco.notification.transferSubscription.buttonText": "我自己的文字"
  }
}
```

- `disabled` —— `资源包id|资源路径`。资源包 id 用的是包文件夹/文件名（游戏内部加的 `file/` 前缀会被去掉，
  所以配置可读、可迁移）。
- `sounds` —— 每个音效事件的倍率；等于 `1.0 / 1.0` 的条目会被删掉。
- `texts` —— `lang` 键的覆盖值；留空即移除覆盖。

只有真的发生变化时才会写文件。

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

## 自动验证

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

MIT，见 [LICENSE](LICENSE)。
