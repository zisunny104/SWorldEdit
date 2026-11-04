# SWorldEdit - Advanced World Editing Plugin for Nukkit

SWorldEdit is a powerful world editing plugin for Nukkit servers that provides extensive building and editing capabilities similar to WorldEdit for Bukkit/Spigot.

## Features

- Selection Tools: Easy area selection with wand axe
- Block Manipulation: Set, replace, copy, paste, and delete blocks
- Geometric Shapes: Create cylinders, spheres, and walls
- Rotation System: Rotate structures around axes
- Undo/Redo: Full history management for edits
- Asynchronous Processing: Non-blocking operations for better performance
- **Schematics System**: Save and load structures as compressed NBT files

## Commands

### Selection Commands

- `//pos1` or `//1` - Set first selection position  
- `//pos2` or `//2` - Set second selection position  
- `//desel` or `//deselect` - Clear current selection  

### Basic Editing

- `//set <block>` - Fill selected area with specified block  
- `//replace <original> <replacement>` - Replace specific blocks in selection  
- `//cut` - Remove blocks from selection (cut to clipboard)  
- `//copy` - Copy selected area to clipboard  
- `//paste` - Paste clipboard content at player position  
- `//undo` - Undo last operation  

### Geometric Shapes

- `//walls <block>` - Create vertical walls around selection  
- `//cyl <block> <radius>` - Create cylinder at player position  
- `//hcyl <block> <radius>` - Create hollow cylinder at player position  
- `//sphere <block> <radius>` - Create sphere at player position  
- `//hsphere <block> <radius>` - Create hollow sphere at player position  

### Schematics

- `//schem save <name>` - Save the current selection as a schematic  
- `//schem paste <name>` - Load a schematic at the player's position

Schematics use SWorldEdit-specific compressed NBT (not Sponge/FAWE `.schem`). Block IDs and metadata are stored as integer arrays, preserving IDs above 255. Older SWorldEdit byte-array files remain readable; previously truncated IDs cannot be recovered and require saving the original structure again. IDs remain core-specific: use a compatible core when loading. Unsupported ID/metadata pairs are rejected before placement. This format stores the primary block layer only; block entity NBT is preserved, including sign text, container contents and reciprocal double-chest links; additional layers are not included. Older files without NBT remain readable and initialize clean block entities. Schematic paste keeps its existing skip-air behavior and uses the batched clipboard placement/undo pipeline.

### Utility Commands

- `//wand` - Give selection wand axe  
- `//rotate <y> <degrees>` - Rotate the selected area around the player
- `//help [page]` - Show command help (currently not working)  

## Block Specification

### Selection utilities
These commands change the cuboid selection only; they never move/edit world blocks, replace the clipboard or create an undo step. They refresh the existing selection outline.

- `//size [-c]`: inclusive bounds, dimensions and volume; `-c` reports clipboard bounds and stored entries without needing a selection.
- `//expand <amount> [reverse-amount] [direction]`, or `//expand vert`: expand one face and optionally its opposite; `vert` uses the world's full height.
- `//contract <amount> [reverse-amount] [direction]`: contract the trailing face towards the direction, following FAWE. For example, `//contract 2 up` raises the lower face by 2; it does not lower the top face.
- `//shift <amount> [direction]`: move the selection only, preserving its dimensions.
- `//outset <amount> [-h|-v]` / `//inset <amount> [-h|-v]`: expand/contract both sides on all axes, horizontal axes only (`-h`) or the vertical axis only (`-v`). The two flags are mutually exclusive here.

Directions are `north`, `south`, `east`, `west`, `up`, `down`; omitted direction uses the dominant viewing axis. Amounts must be non-negative integers. Inverted/empty results, coordinate overflow and results outside world height are rejected before either point changes. Original corner roles are retained when reversed. Volumes use arbitrary-precision arithmetic. These are the listed cuboid forms, not all FAWE selectors, direction aliases or flags. See `//help 4` for the new commands.

Blocks can be specified in multiple formats:

- **Numeric ID**: `1`, `5:2`  
- **Block Names**: `stone`, `oak_planks`  
- **Full Names**: `minecraft:stone`  


### Stack
`//stack <count> [north|south|east|west|up|down] [-a]` repeats the selection by its full dimensions.
Omit direction to use the dominant axis of your look direction. Air is included.
The selection and clipboard stay unchanged; `//undo` restores the destination in one step.
World access is split over server ticks. Configure `stack.max-blocks` and
`stack.blocks-per-tick` in `config.yml`. Stack copies the primary block layer and supported block entity NBT, without entities or additional layers.

### Terrain smoothing
`//smooth [iterations]` smooths the selected surface height map using a Gaussian filter (radius 5, sigma 1; default 1 pass). Select terrain and enough headroom. Edges participate using extended edge samples; terrain writes stay inside the selection. Each pass rounds to block heights before the next pass. A square surface depression can get softer, rounded edges and become shallower; this does not guarantee a perfect circle. It is a terrain tool, not cave, wall or general object smoothing. Empty columns are skipped rather than filled without a source material.

Only configured terrain IDs define the surface. Grass, flowers, thin snow and complete double plants move with the ground, preserving metadata. Up to two layers immediately above the selection are captured so a boundary-cut plant can move as one unit; only associated surface cover may be changed there, and Undo includes it. No item drops are generated. Natural leaves use the running core's `isPersistent()` API: non-persistent canopies stay in place without excluding the ground below, and terrain/cover cannot grow through them. Persistent leaves, trunks, buildings, lava, unsupported flowing water and block entities still protect their columns. Buried obstacles and air gaps constrain the continuous surface terrain; these lower obstacles stay unchanged. Resampling preserves the surface material and underlying layers. Stone/dirt buildings still count as terrain, so choose the selection carefully. `smooth.terrain-ids`, `smooth.max-blocks`, `smooth.max-iterations` and `smooth.blocks-per-tick` control eligibility and work limits. Capture, filtering and planning run in server-thread batches; placement uses the existing clipboard pipeline. The clipboard and selection stay unchanged. A successful edit produces one `//undo` step, and partial placement failures retain undo data. Flat or fully skipped selections create no undo step.

`smooth.underwater` defaults to true. Contiguous source water (IDs 8/9, metadata 0)
above a selected bed is excluded from the height map. Raising the bed replaces water
with terrain; lowering it restores water beneath the observed original level. Existing
water above the selected terrain range stays unchanged. Newly lowered shoreline cells
inherit water only through adjacent water or other newly lowered shoreline cells;
the selection's highest water level is not applied to unrelated dry ground or caves.
If this would newly connect different water levels, the operation fails before editing;
select the water bodies separately. Source water is copied from its native snapshot.

Flowing/falling water, hanging water sheets, lava, buildings, block entities and
unrecognised aquatic plants remain protected. New shore water cannot overwrite a
protected canopy. Land plants/snow whose new ground is submerged are removed without
drops, and Undo restores them with the original bed and water. World physics may later
move fluids after other updates; this tool does not simulate a complete watershed or
edit waterlogging/additional layers. Use `smooth.underwater: false` to skip water columns.
`smooth.tick-millis` (default 3) adds an elapsed-time budget to the batch limit; one core
world access or one processing unit cannot be preempted. The command remains
`//smooth [iterations]` and retains the separate `smooth.max-blocks` limit.

The height-map command concept follows [WorldEdit's region smoothing](https://worldedit.enginehub.org/en/latest/usage/regions/regionops/), with Gaussian and boundary behavior checked against [FAWE](https://github.com/IntellectualSites/FastAsyncWorldEdit/blob/main/worldedit-core/src/main/java/com/sk89q/worldedit/math/convolution/HeightMapFilter.java). This implementation uses its own tick-batched planner and obstacle rules; it does not claim all FAWE masks, brushes or flags.

### Grass and natural terrain layers
`/green [radius] [height] [-f]` converts exposed ordinary dirt around the player into grass blocks; `-f` includes coarse dirt. `//green` is an alias. Radius defaults to 10; omitted height scans the full world height, while an explicit height limits the search to that many blocks above and below the player's block Y. Each column stops at the first movement-blocking block or liquid, so dirt under a roof or water is not converted. Passable plants are retained. This does not change elevations, biomes or plant tall grass.

`//naturalize` uses the existing selection. In each column it finds the highest ordinary grass/dirt/stone, then changes only those block types into grass at depth 0, dirt at depths 1–3 and stone below. Ores, stone variants, coarse dirt, air and other blocks remain unchanged; depth does not restart at a cave or skipped block. Columns whose terrain continues immediately above the selection are skipped, preventing a grass layer inside an underground cross-section. Select from below the intended soil layers to above the surface. Stone buildings in the selection can be affected.

Both commands plan changes in server-thread batches, leave the user's clipboard and selection unchanged, preserve block entities at skipped positions and use one `//undo` step. Limits are configurable under `surface`: `default-radius` (10), `max-radius` (128), `max-checks` (1000000 conservative bounding-box scan estimate), `max-blocks` (100000 actual planned changes) and `blocks-per-tick` (512). Exceeding either planning limit aborts before any writes; partial placement failures keep undo. Command metadata includes numeric arguments and `-f` candidates. New functionality remains local until owner gameplay verification and approval.

Behavior was checked against FAWE's [green implementation](https://github.com/IntellectualSites/FastAsyncWorldEdit/blob/main/worldedit-core/src/main/java/com/sk89q/worldedit/EditSession.java) and [Naturalizer](https://github.com/IntellectualSites/FastAsyncWorldEdit/blob/main/worldedit-core/src/main/java/com/sk89q/worldedit/function/block/Naturalizer.java). The implementation is adapted to Nukkit's block IDs and existing batched placement. Unlike FAWE's current green loop, the configured lower Y bound is included; candidates with block entities are never changed.

### Languages
Set `language: zh_TW` (or `en_US`) in `config.yml` and restart the plugin.
Bundled catalogues are copied to `lang/` without overwriting existing files.
Edit those YAML files to customize messages, or add `lang/<locale>.yml` for another
language. Missing entries fall back to English. `${0}`, `${1}`, etc. are literal
replacement arguments; preserve them when translating. Tool identifiers and block
names supplied by the core are not translated by the plugin.

### Clipboard air and connections
Copy captures the entire selected primary block layer, including air. `//paste` includes
air; `//paste -a` skips air. A clipboard remains reusable after pasting. Copy publishes
only after completion and preserves the previous clipboard if it fails. Copy, paste and
undo run in server-thread batches. `clipboard.blocks-per-tick` defaults to 512; the
streaming pipeline also yields after `clipboard.streaming.tick-millis` (default 3 ms).
A single world/chunk call cannot be preempted. Once placement finishes, paste, stack and
undo refresh connection-dependent blocks at the destination and its immediate boundary.
The primary layer now includes block entity NBT; extra layers remain outside this scope.

`clipboard.streaming.enabled` defaults to true for `//copy`, paged `//paste` and their
`//undo` history. Compressed detached pages and disk reads run on one background worker;
world reads/writes stay on the server thread. Only one page is in flight per active job.
The queue defaults to 4 jobs, with one job per player; destination recovery data is fully
prepared before placement, and a failed partial paste retains the attempted prefix for Undo.
Repeated paste and `-a` are supported, including NBT and chest pairing across pages.

Defaults: `clipboard.streaming.max-blocks` 50,000,000; `page-cells` 2048 (max 4096);
`memory-bytes` 1 MiB of compressed pages plus page bookkeeping before spilling;
`disk-bytes` 1 GiB per file; `reserve-bytes` 512 MiB free disk; `max-states` 8192 native
prototypes; `max-history` 10 per player and `max-stores` 128 retained stores overall.
Memory also includes bounded decoded pages and native prototypes. Unknown mutable native
state is not silently merged by numeric ID; a palette limit aborts instead of losing state.
These are finite resource limits, not unlimited FAWE compatibility.

Cache files under `clipboard-cache/` are **session-only**, including disk Undo: they do not
survive plugin reload or server restart as usable history. Startup removes abandoned owned
session files using locks, leaves active/unknown files alone and never resumes world edits.
Do not reload during an edit. Disabling the plugin cancels jobs and closes cache files.
`clipboard.max-blocks` remains 100,000 for schematic/legacy in-memory editing paths;
stack, smooth and surface also retain their separate limits. Increasing the streaming
limit does not increase these limits. General entities and additional block layers are not copied.

### Block entity NBT
Copy/paste, stack and their undo records capture deep-cloned block entity NBT after
flushing runtime inventory data. Each placed copy gets its destination coordinates,
its own block entity instance, and a dirty flag for persistence. Signs, containers
and other core-supported block entities retain their data when the core can load it.
Missing or incompatible data falls back to a clean usable entity with a warning.
If even clean initialization fails, the operation fails and attempts to restore the
previous destination; it does not report a broken container as a successful paste.
Double-chest source references are stripped during individual placement, then rebuilt
between mutually paired included halves once all blocks exist. Each stacked copy has
its own pair. Copying only one half keeps it single. Undo captures destination NBT
before either half is detached and can reconnect an untouched outside half without
restoring or overwriting that outside half's inventory. Pair failures report an error.
This does not copy entities, plugin database records, player ender-chest storage or
additional layers. Disk schematics now preserve block entity NBT as well. Game testing is still required.

Commands use the existing double-slash labels and declare typed parameters for Bedrock suggestions. All commands require `we.command` (default: OP). `//redo` is not registered because redo is not implemented; use `//undo` to revert the last operation. Block arguments remain strings to accept both namespaced names and numeric ID/metadata syntax.

Use `//stack 3 -a` to skip source air while using your viewing direction, or `//stack 3 east -a` for an explicit direction. Skipped cells keep destination blocks/NBT unchanged and are excluded from undo and the changed-block count. Without `-a`, air is copied as before; spacing still uses the full selection dimensions.

### Block query tool
Hold an item and run `/tool info`, then right-click a block to inspect its coordinates, type, available states, emitted light, block-entity presence and parser-verified block input. `/tool none` disables the held item type; `//tool` is an alias. Bindings are per-player and session-only, apply to that item type regardless of durability, and do not alter items or blocks. Binding the wand switches it to query mode; `//wand` restores selection mode. NBT contents are not dumped or included in the suggested block input. Selection visualization is not part of this feature.

### Selection and paste bounding boxes
The renderer from [BoundingBoxAPI](https://github.com/zisunny104/BoundingBoxAPI) is embedded internally; no separate plugin is needed. It sends client-only structure-block previews to the selecting player without world writes. Both wand and position commands update the selection box; deselection, world changes, logout and plugin shutdown clear it. The hidden markers use a separate vertical layer from the standalone API.

Configure `bounding-boxes.selection-enabled`, `selection-seconds` (0 means until cleared), `paste-enabled` and `paste-seconds` (default 10; 0 disables paste previews). After a successful clipboard or schematic paste, the affected bounding box is shown for that duration; ignored air does not expand it. Repeated pastes replace that player's previous paste box. Undo removes the paste box. Preview errors do not fail a successful edit. The client determines appearance and rendering limits; in-game verification is required.

## FAWE 差異盤點

更新基準：2026-11-02，本機整合版。依 [FAWE 指令分類](https://intellectualsites.gitbook.io/fastasyncworldedit/features/main-commands-and-permissions)及 [WorldEdit 指令文件](https://worldedit.enginehub.org/en/latest/commands/)盤點功能群組；這不是所有旗標、平台相容性或細項行為的完整相容聲明。下列「已有」也不代表已在 VPS 部署或通過本次遊戲測試。

| 類型 | 本機現況 | 尚缺或不同之處 | 順序 |
|---|---|---|---|
| 長方體選區 | 兩點、工具選取、取消、外框已有 | 非長方體與 FAWE CUI 不相容 | 基礎 |
| 選區調整 | 本批加入 size、expand、contract、shift、outset、inset；含 vert、反向數量、水平／垂直旗標 | 只支援上述基本語法；不接受空選區或超出世界高度 | 本批 |
| 選點輸入 | 玩家目前位置、工具點擊已有 | pos 座標參數、hpos、chunk、選區模式切換 | 後續 |
| 方塊統計 | size 可看體積與剪貼簿尺寸 | count、distr、搜尋與分布分析 | 下一批候選 |
| 填滿、替換、牆與幾何 | set、replace、walls、球與圓柱已有 | faces、outline、hollow、overlay、center、line、curve、錐體／金字塔；既有圓柱參數也不完整 | 分批 |
| 剪貼簿 | copy、可重複 paste、略過空氣、主要層狀態及 NBT；大型資料串流落盤 | cut 目前僅清除選區，尚不等同 FAWE 剪下；原點旗標、實體、生態域及額外層仍缺 | 分批 |
| 旋轉、翻轉與移動 | 既有 rotate 操作選區世界，並非 FAWE 的剪貼簿旋轉 | clipboard rotate／flip、move；方向狀態與 NBT 轉向須驗證，不能只旋轉座標 | 先確認相容語意 |
| 堆疊 | count、方向、視線方向、略過空氣、NBT、復原已有 | 其餘旗標、來源遮罩、一般實體等 | 部分 |
| 地形與草地 | smooth、green、naturalize 已有本機實作 | 尚無 smooth brush、任意地形遮罩；保護規則與 FAWE 不完全相同 | 待遊戲驗證 |
| 水、雪與附近方塊工具 | 尚缺 | drain、fixwater／fixlava、snow／thaw、removenear／replacenear、removeabove／below | 先核對核心物理更新 |
| 歷史 | 本次執行期間逐次 undo，大型剪貼簿 Undo 落盤且有保留上限 | redo、一次多步、清除、跨重啟與他人歷史 | 後續 |
| 遮罩與材質樣式 | 只支援單一方塊名稱、ID 與附加資料；少數指令有略過空氣 | 條件遮罩、交集／聯集、比例隨機材質與樣式解析器 | 共用層優先 |
| 筆刷與工具 | 唯讀 tool info 已有 | brush、sphere／smooth brush、筆刷尺寸／材質／遮罩、超級鎬等 | 依賴遮罩與共用編輯流程 |
| Schematic | 自有壓縮 NBT 格式，含整數 ID 及支援的方塊實體 | Sponge／FAWE 格式、格式選擇、列表、跨核心與 Java／Bedrock 資料轉換 | 不能把檔案副檔名當成格式相容 |
| 生態域與世界生成 | 尚缺 | biome 查詢／編輯、regen、樹木／植被、礦脈／洞穴等 | 依賴核心版本與生成器 |
| 主執行緒與工作控制 | clipboard、stack、smooth、surface 已分批處理 | set、walls、geometry、rotate 仍有舊 AsyncTask 路徑；未統一取消、逾時、進度與互斥 | 優先處理既有實作缺口 |
| 權限與參數提示 | 共用 we.command（預設 OP）、已宣告參數與候選 | FAWE 細分權限、個人限制與管理設定介面 | 分批 |
| 多語系 | 獨立 en_US／zh_TW、覆寫與英文備援已有 | 玩家個別語系、更多語系及核心名稱翻譯 | 部分 |
| 快照、腳本與外部服務 | 尚缺 | snapshot／restore、CraftScript、圖片生成地形、遠端資源等 | 非目前首要範圍 |

後續先統一既有編輯的執行流程，再補只讀統計與共用遮罩；大型新功能另開分支。既有 rotate 的行為切換、檔案格式或其他破壞相容性的變動需另行確認。新功能先在本機測試與編譯，取得擁有者遊戲內驗證及明確同意後才提交上游。
