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
- `//schem paste <name>` - Load a schematic at player's position or at specified coordinates  

Schematics use SWorldEdit-specific compressed NBT (not Sponge/FAWE `.schem`). Block IDs and metadata are stored as integer arrays, preserving IDs above 255. Older SWorldEdit byte-array files remain readable; previously truncated IDs cannot be recovered and require saving the original structure again. IDs remain core-specific: use a compatible core when loading. Unsupported ID/metadata pairs are rejected before placement. This format stores the primary block layer only; block entity NBT is preserved, including sign text, container contents and reciprocal double-chest links; additional layers are not included. Older files without NBT remain readable and initialize clean block entities. Schematic paste keeps its existing skip-air behavior and uses the batched clipboard placement/undo pipeline.

### Utility Commands

- `//wand` - Give selection wand axe  
- `//rotate <axis> <degrees>` - Rotate clipboard content  
- `//help [page]` - Show command help (currently not working)  

## Block Specification

Blocks can be specified in multiple formats:

- **Numeric ID**: `1`, `5:2`  
- **Block Names**: `stone`, `oak_planks`  
- **Full Names**: `minecraft:stone`  


### Stack
`//stack <count> [north|south|east|west|up|down]` repeats the selection by its full dimensions.
Omit direction to use the dominant axis of your look direction. Air is included.
The selection and clipboard stay unchanged; `//undo` restores the destination in one step.
World access is split over server ticks. Configure `stack.max-blocks` and
`stack.blocks-per-tick` in `config.yml`. Stack copies the primary block layer and supported block entity NBT, without entities or additional layers.

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
undo run in server-thread batches, configured by `clipboard.max-blocks` (default 100000)
and `clipboard.blocks-per-tick` (default 512). Once placement finishes, paste, stack and
undo refresh connection-dependent blocks at the destination and its immediate boundary.
The primary layer now includes block entity NBT; extra layers remain outside this scope.

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
