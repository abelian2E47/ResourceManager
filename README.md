# ResourceManager

**An in-game resource pack debugger for Minecraft 1.21.11 (Fabric).**

Press **F8** to browse every enabled pack as a file-manager style tree and switch off a single texture,
model, sound or UI sprite: that pack stops providing the one file and the next pack in the load order
takes over. You can also tune any `sounds.json` event, preview the texture you are about to touch, and
edit `lang` keys so the new text appears in game at once.

[中文说明](README.zh_cn.md) · [Report an issue](https://github.com/abelian2E47/ResourceManager/issues)

![The GUI: filters and disabled list on the left, the resource tree in the middle, the inspector on the right](docs/screenshot-overview.png)

## Features

- **File manager layout** — toolbar, sidebar, tree, inspector and status bar are separate panels; when
  the window gets narrow the sidebar folds itself away instead of overlapping anything.
- **Hierarchical tree** — pack → namespace → directory → file, with a file count per node, the number
  of disabled files (`off`) and `♪` markers for `sounds.json` events.
- **Search and categories** — filter by pack, namespace, path or file name (matching branches expand
  themselves, the header shows `hits / total`), or narrow the tree down to
  Textures / Sounds / UI / Models / Text / Other.
- **Disable exactly one resource** — the pack stops serving that file and the pack below it takes over.
  The inspector shows what is active, what is shadowed, and which other packs ship the same file.
  Whole packs, namespaces and directories can be disabled in one click, with a confirmation.
- **Sound tuning** — 0–4× volume and pitch per `sounds.json` event, applied to real playback, with
  **Play** to hear it and a status line that says whether it actually started.
- **Texture preview** — the inspector draws the selected image at an integer scale, next to its real
  size and the pack that is serving it right now.
- **Text editing** — edit any `lang` key, item names included; the key list shows the text each key
  produces and the filter matches it, so you can search for `Diamond Sword` instead of the key.
- **Full screen disabled list** — the DISABLED panel expands to the whole window, two columns, mouse
  and keyboard.
- **Client side only** — it changes what your client loads and renders. Nothing is sent to the server,
  and packs the server pushes can be switched off locally just like your own.
- **Everything lives in one config file** and is re-applied on startup.

## Screenshots

| | |
| --- | --- |
| ![Search](docs/screenshot-search.png) | ![Full screen disabled list](docs/screenshot-disabled-full.png) |
| Filtering the tree with a search query | The DISABLED panel expanded to the whole window |
| ![Sound tuning](docs/screenshot-sound.png) | ![Text editor](docs/screenshot-text-editor.png) |
| Tuning a sound event, with Play on the right | Editing a `lang` key of a pack (or of vanilla) |
| ![Texture preview](docs/screenshot-texture-preview.png) | |
| Previewing a texture that a pack overrides | |

## Using it

Press **F8** anywhere — title screen, pause screen, in a world. The key is rebindable in
*Options → Controls → Resource Manager*.

**Find a file.** Type in the search box: pack, namespace, path and file name all match, and branches
with hits expand themselves. The category buttons cut the tree down to one kind of resource. `↑` / `↓`
walk the rows, `→` expands, `←` collapses, a double click does both.

**Disable a resource.** Select a file and press `Enter` (or *Disable resource*). "Disabled" means *this
pack no longer provides that file*, so the fallback chain keeps walking and the next pack down the list
serves it — disable the topmost copy of a texture and the copy below becomes visible again. The
inspector's **State** line names the situation (`Active (this pack wins)`, `Shadowed by …`,
`Disabled (lower packs can provide it)`), **Effective source** asks the running game who serves the file
right now, and **Next provider** jumps to the next pack that ships it. Pack, namespace and directory
nodes can be disabled in one click (click again to confirm); *Clear all* empties the list.

**Tune a sound.** Under `sounds.json` (`♪` rows) select an event. Drag the volume / pitch sliders —
0–4×, mouse wheel for 0.05 steps — and *Play* auditions it; the status bar says `Playing …` when it
really started, or why it could not. *Reset* goes back to 1.00 / 1.00. The values multiply into real
playback, so you hear the change immediately.

**Look at a texture.** Select any image file and the inspector renders it scaled by whole numbers (up to
6×), with its real size and the pack that is serving it right now, so disabling pack A's copy switches
the preview to pack B's.

**Edit text.** Filter to *Text* (or search `en_us.json`), select a `lang` file and the inspector becomes
a text editor: key filter on top, key list in the middle, value box and **Apply** / **Revert** at the
bottom. Every row shows the text the game displays for that key and the filter matches it too, so typing
`Diamond Sword` (or `钻石剑`) finds `item.minecraft.diamond_sword`. That covers item and block names,
tooltips, GUI labels and mod strings. If no loaded file declares the key you want, put the key itself in
the filter box and press **Add key**. Item names change in the tooltip, inventory, chat and hotbar at
once.

**Full screen disabled list.** The **Full** button in the DISABLED panel spreads the list over the whole
window in two columns (`pack | resource`): one click jumps to the entry in the tree, a double click or
`Enter` restores it, the search box filters it, and `ESC` goes back to the three panels.

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

## License

MIT — see [LICENSE](LICENSE).
