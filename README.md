# ResourceManager

**An in-game resource pack debugger for Minecraft 1.21.4 (Fabric).**

Open it with **F8**, browse every enabled pack as a file-manager style tree, and switch off a single
texture, model, sound or UI sprite: that pack stops providing the one file and the next pack in the
load order takes over. You can also tune the volume and pitch of any `sounds.json` event and hear it
right away, preview the texture you are about to touch, and edit `lang` keys so the new text shows up
in game immediately. Everything you change lives in one config file and survives a restart.

[中文说明](README.zh_cn.md) · [Report an issue](https://github.com/abelian2E47/ResourceManager/issues)

![The GUI: filters and disabled list on the left, the resource tree in the middle, the inspector on the right](docs/screenshot-overview.png)

## Highlights

- **File manager layout** — toolbar / sidebar / tree / inspector / status bar are separate panels.
  When the window gets too narrow the sidebar folds itself away instead of overlapping anything.
- **Hierarchical tree** — pack → namespace → directory → file, with a file count per node, the number
  of disabled files (`off`) and `♪` markers for `sounds.json` events.
- **Search & categories** — filter by pack, namespace, path or file name (matching branches expand
  themselves and the header shows `hits / total`), or narrow the tree down to
  Textures / Sounds / UI / Models / Text / Other.
- **Disable one resource** — the pack stops serving that file, so the next pack in the fallback chain
  provides it. The inspector lists the other packs that ship the same file. Batch disable works on
  pack / namespace / directory nodes, with a click-again confirmation.
- **Sound tuning** — 0–4× volume and pitch per `sounds.json` event, applied to the real playback, and
  a **Play** button that tells you whether the sound actually started (and why not, if it did not).
- **Texture preview** — the inspector renders the selected image at an integer scale, with its real
  size next to it, using the pack that owns the file.
- **Text editing** — edit any `lang` key and see the change in game at once, including text that was
  already drawn (item names, tooltips, GUI labels). The key list shows the text each key produces and
  the filter matches it, so you can search for `Diamond Sword` instead of the key.
- **Full screen disabled view** — the "Full" button in the DISABLED panel turns the sidebar list into
  a two-column window-wide view with mouse and keyboard navigation.

The mod is client side only: it changes what **your** client loads and renders and never talks to the
server or the world. Packs the server pushes are labelled as such and can be switched off locally just
like your own — the change stays on your machine.

## Screenshots

| | |
| --- | --- |
| ![Search](docs/screenshot-search.png) | ![Full screen disabled list](docs/screenshot-disabled-full.png) |
| Filtering the tree with a search query | The DISABLED panel expanded to the whole window |
| ![Sound tuning](docs/screenshot-sound.png) | ![Text editor](docs/screenshot-text-editor.png) |
| Tuning a sound event, with Play on the right | Editing a `lang` key of a pack (or of vanilla) |
| ![Texture preview](docs/screenshot-texture-preview.png) | |
| Previewing a texture that a pack overrides | |

## Supported versions and branches

Every supported Minecraft version lives on its own branch; `main` follows the newest one. Only the
branch name differs — the GUI, the config format and the behaviour are the same.

| Branch | Minecraft | Fabric API used | Automated verification |
| --- | --- | --- | --- |
| `1.21.4` (also `main`) | 1.21.4 | 0.119.4+1.21.4 | 43 assertions, all PASS, 15 screenshots |
| `1.21.1` | 1.21.1 | 0.116.8+1.21.1 | 32 PASS, 4 SKIP |
| `1.21.8` | 1.21.8 | 0.136.1+1.21.8 | 32 PASS, 4 SKIP |
| `1.21.11` | 1.21.11 | 0.141.3+1.21.11 | 32 PASS, 4 SKIP |

Build the branch you need with `./gradlew build`; the jar lands in `build/libs`. To keep several
versions side by side: `git worktree add "..\resourcemanager-1.21.8" 1.21.8`.

The `SKIP`s on the non-1.21.4 branches are the checks that need an external resource pack: the packs
used as fixtures were built for 1.21.4, and 1.21.1 / 1.21.8 mark them incompatible while 1.21.11
rejects them outright (`min_format` / `max_format` metadata). Everything else still runs — both mixins
report `applied = true`, the GUI opens from the key bind, search, disabling, tuning, previews, text
editing and the item-name override are all exercised on every branch.

## Requirements

| | |
| --- | --- |
| Minecraft | 1.21.4 |
| Fabric Loader | 0.16.14 or newer |
| Fabric API | required |
| Java | 21 |
| Environment | client side; safe to use on a server-connected client, everything it does is local |

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) and put
   [Fabric API](https://modrinth.com/mod/fabric-api) in `mods/`.
2. Drop `resourcemanager-1.0.0.jar` from [Releases](../../releases) into `.minecraft/mods/`.
3. Start the game and press **F8** (rebindable in *Options → Controls → Resource Manager*).

## Using it

```
┌ toolbar   title · search · sidebar · reload packs · close ────────────────┐
├──────────────┬──────────────────────────────┬────────────────────────────┤
│ FILTERS      │ RESOURCE TREE                │ INSPECTOR                  │
│  All         │  ▾ pack                #2 off│  name                      │
│  Textures    │    ▾ namespace       554  off│  namespace:path            │
│  Sounds      │      ▾ directory             │  pack / type / state       │
│  UI          │        • file         ♪ event│  ┌────────┐ texture preview│
│  Models      │                              │  │ 16x16  │ 16x16          │
│  Text        │                              │  └────────┘                │
│  Other       │                              │  providers (load order)    │
│ DISABLED (3) │                              │  volume ▬▬▬▬▬ 1.00         │
│  list  [Full]│                              │  pitch  ▬▬▬▬▬ 1.00         │
│ [Clear all]  │                              │  [Reset]      [Play]       │
├──────────────┴──────────────────────────────┴────────────────────────────┤
│ status  25142 files · 58 packs · 1669 sound events · 3 disabled │ hints   │
└──────────────────────────────────────────────────────────────────────────┘
```

**Disabling.** Select a file and press `Enter` (or *Disable resource*). "Disabled" means *this pack no
longer provides that file*: the fallback chain keeps walking, so the next pack further down the pack
list serves it. That is also the point of the tool — disable the topmost pack's copy of a texture and
the copy from every pack below it becomes visible again. The inspector's **State** line spells out the
current situation (`Active (this pack wins)`, `Shadowed by …`, `Disabled (lower packs can provide it)`)
and **Next provider** jumps to the next pack that ships the same file.

**Where the changes go.** Nothing is written into the resource packs themselves. Everything is stored
in `config/resourcemanager.json`, re-applied on startup and re-read whenever you press *Reload packs*.

### Full screen disabled view

The DISABLED panel has a **Full** button that spreads the list over the whole content area:

![Full screen disabled list](docs/screenshot-disabled-full.png)

Rows are split into `pack | resource` columns, a single click jumps to the entry in the tree, a double
click (or `Enter`) restores it, the toolbar search box filters the list, and `ESC` returns to the
three-panel view without closing the GUI.

### Sound tuning

Select an event under `sounds.json` (`♪` rows) and drag the volume / pitch sliders — 0–4×, with the
mouse wheel as a 0.05 fine adjustment. **Play** previews the event relative to the listener and
without attenuation, and reports the outcome in the status bar: `Playing minecraft:block.bamboo.step`
when it really started, or the reason when it could not (`is not provided by the loaded sounds.json`,
`has no usable sound file`, `is marked intentionally silent`). **Reset** goes back to 1.00 / 1.00.
Your values are multiplied into the real sound playback, so in-game audio changes immediately.

### Texture preview

Any image file renders in the inspector above its metadata, scaled by an integer factor (never
blurred, up to 6×). The pixels are the ones the game actually uses — they are fetched from the running
resource manager, so as soon as pack A's copy is disabled the preview shows the copy pack B provides.
The caption inside the frame says which of the two you are looking at (`in use now`, or
`this pack's copy (not served)` when nothing serves the file any more), and the source size sits next
to it.

### What the inspector reports, and why it can be trusted

Everything it shows is derived from live state instead of a private cache:

| Line | Source |
| --- | --- |
| `Pack`, `Type`, `Files` | the scan of the loaded packs |
| `Providers` | every pack that ships this file, highest priority first, with the pack that wins once the packs are reloaded |
| `Effective source` | asked from the running resource manager right now — the authoritative answer |
| `State` | `Active` / `Shadowed by …` / `Disabled (lower packs can provide it)` from your config |
| `Source` | shown when the pack was pushed by the server (`server/…` ids); disabling it is a local override |

Three consequences worth knowing:

* The tree is built by listing the packs themselves, not the resource manager's filtered view, so a
  file you disabled **keeps its row** after a reload — otherwise it would drop out of the tree and you
  could not inspect or enable it again.
* The GUI notices when the game reloads resources on its own (`F3+T`, ticking a pack in the options
  screen, joining a world) and rescans, instead of reporting an index that no longer describes the
  game.
* Reopening the GUI paints the previous scan at once and refreshes in the background, so the window is
  never empty waiting for the scan.

### Text editing

Every `lang/*.json` is a node in the tree (filter to *Text* or just search `en_us.json`). Select it and
the inspector becomes a text editor: a key filter on top, the keys of that file in the middle (edited
keys carry a ✎), and a value box with **Apply** / **Revert** at the bottom.

![Text editor](docs/screenshot-text-editor.png)

The key list shows each key together with the text the game displays for it, and the filter matches
both, so you can go from what you see to the key that produces it: type `Diamond Sword` (or
`钻石剑`) and `item.minecraft.diamond_sword` shows up. That covers everything the game renders through
a translation key — item and block names, tooltips, enchantments, GUI labels, death messages, and the
names mods add. Type a new value and press **Apply**; **Revert** removes the override so the pack (or
vanilla) text is used again. Edits are stored per key, so they apply whatever pack or language file
declares them.

If no loaded file declares the key you are after — a mod string that has no translation yet, say — put
the key itself into the filter box (`item.minecraft.diamond_sword`) and press **Add key**: the inspector
starts editing it anyway, and the override is served just like any other.

Item names really change in game: the name in the tooltip, in the inventory, in chat, and in the item
count of the hotbar all resolve through that key.

## Keyboard & mouse

| Input | Action |
| --- | --- |
| `F8` | Open / close the GUI |
| single click | Select a row (in the disabled list: jump to that file) |
| double click / `→` | Expand a node (on a file: toggle disabled); in the disabled list: restore the entry |
| `←` | Collapse a node, or fold back to its parent |
| `↑` / `↓` | Move the selection (scrolls into view); in the full screen list: move the highlight |
| `Enter` | Toggle disabled on the selection; in the full screen list: restore the selection |
| `ESC` | Close the GUI; in the full screen list: go back to the three-panel view |
| `↓` / `Enter` / `Tab` in the search box | Leave the box and hand the keyboard to the tree |
| `↓` / `Enter` in the key filter | Leave the box and select the first key |
| `Enter` in the value box | Apply that text edit |
| mouse wheel | Scroll a list; over a slider, fine-tune the value |
| drag the scrollbar | Jump through a long list |

## Config file

`config/resourcemanager.json`:

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
    "mco.notification.transferSubscription.buttonText": "My own label"
  }
}
```

- `disabled` — `packId|resourceLocation`, where `packId` is the pack folder/file name (the `file/`
  prefix the game adds is stripped, so the entries stay readable and portable).
- `sounds` — per sound event multiplier; an entry equal to `1.0 / 1.0` is dropped again.
- `texts` — `lang` key overrides; an empty value removes the override.

The file is rewritten only when something actually changed.

## How it works

| Module | Role |
| --- | --- |
| `ResourceIndex` | Builds the model from the live `ResourceManager`: walks resources in load order, parses `sounds.json`, records the provider chain of every location and keeps a packId → `PackResources` map so files can be read per pack |
| `ResourceNode` / `ResourceTree` | Tree nodes (pack / namespace / directory / file / sound event) and the flattening into rows with search, category filter and expansion state |
| `ui/` (`Ui` `TreeView` `ScrollList` `UiButton` `UiSlider` `DisabledList` `TextKeyList` `PreviewTexture`) | Panel colours and geometry, scrollable lists, the tree view, sliders, the disabled list, the text key list and the texture preview |
| `ResourceManagerScreen` | Layout (toolbar / sidebar + tree + inspector / status bar), the inspector that changes shape with the selection, the full screen disabled list, the text editor and all input handling |
| `LangText` | Reads `lang/*.json` per pack, lists keys and resolves the text that is currently in effect |
| `SoundPreview` | Decides whether a sound event can be played (unknown / empty / intentionally silent) and builds the preview instance |
| `mixins/FallbackResourceManagerMixin` | Wraps every pack so a disabled file simply does not exist in it; the fallback chain then lands on the next pack |
| `mixins/SoundInstanceMixin` | Multiplies volume and pitch per sound event id |
| `mixins/ClientLanguageMixin` | `ClientLanguage.getOrDefault` / `has` return the text override first |
| `mixins/TranslatableContentsMixin` | Invalidates the cached `decomposedWith` of translated components so an edited text appears immediately |
| `ResourceManagerConfig` | Reads and writes `config/resourcemanager.json`, normalises pack ids, reports a version so the UI can refresh |

### Gotchas on the 1.21.4 API (the code has comments about them)

1. `ResourceManager.listResources(path, …)` **rejects empty paths** (`FileUtil.decomposePath` only
   accepts real path segments), so a namespace cannot be listed in one call. The index instead walks a
   set of well-known asset roots plus the roots found in `resourcepacks/`, and probes namespace-root
   files such as `sounds.json` separately.
2. `AbstractWidget.setRectangle` takes `(width, height, x, y)` — the opposite of its parameter names,
   so using it directly swaps position and size. Every widget is placed through
   `Ui.place(widget, x, y, w, h)`.
3. `Screen.render` paints the background again, so calling `super.render` from a subclass wipes custom
   panels. This screen draws its children through `children()` itself.
4. `GuiGraphics.blit` only accepts a `Function<ResourceLocation, RenderType>` as its first argument;
   the preview uses `RenderType::guiTextured` to go through the GUI shader.
5. A text override has to cover two paths: `ClientLanguage.getOrDefault` / `has` for freshly parsed
   text, and the `TranslatableContents.decomposedWith` cache for components that were already drawn —
   hence the second mixin, which clears that cache when the text version changes.

## Build from source

```powershell
.\gradlew.bat build        # -> build/libs/resourcemanager-1.0.0.jar
.\gradlew.bat runClient    # dev client; put test packs into run/resourcepacks
```

## Automated verification

`src/client/java/com/abelian/client/verify/VerifyHarness.java` is a self-checking driver. It opens the
GUI through the key binding, injects key presses and mouse clicks, and asserts: no overlapping or
out-of-bounds widgets in both a compact and a wide window, a disabled file really falls back to the
next pack, re-enabling restores the original provider, a slider click is stored in the config, the
category filter leaves only `textures/` paths, a `lang` file is recognised as text and an edit takes
effect immediately (and reverting brings the pack text back), the texture thumbnail really renders at
its size, the disabled column expands to the whole window, and `ESC` returns from it. It also covers
the display accuracy work: it disables a file whose own pack currently wins, then checks that the live
manager answers with the lower pack, that the inspector's `Effective source` line agrees with it (no
divergence marker), and that after a reload the disabled file is still listed in the tree. Finally it
edits a real item name: it reads the name a diamond sword shows, searches the key list by that text,
applies an override and asserts that the item stack itself reports the new name, then reverts it.
Screenshots go to `run/screenshots/`, the report to `run/verify-report.txt`, and the config is restored
to its pre-run state (disabled, sounds and texts).

```powershell
$env:RESOURCEMANAGER_VERIFY = "1"; .\gradlew.bat runClient     # exits the client when done
```

Last run: 43 assertions, all `PASS`, 15 screenshots, about a minute. Sample report lines:

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
PASS: the live manager answers with the lower pack (Squareful ….zip)
PASS: the file is no longer answered by its own pack (§cCozyUI§b+ ….zip)
PASS: the inspector agrees with the live manager, with no divergence marker
PASS: the disabled file is still listed in the tree after the reload (…|appleskin:textures/icons.png)
PASS: the thumbnail shows the file the game serves
PASS: searching by the text you see in game finds the key behind it
PASS: the item name override is stored in the config
PASS: the item really displays the new name in game (was "Diamond Sword")
PASS: reverting brings the original item name back
```

> The harness ships inside the jar, but its entry point checks the environment variable first and
> returns immediately without it, so it is simply an unused class during normal play.

## License

MIT — see [LICENSE](LICENSE).
