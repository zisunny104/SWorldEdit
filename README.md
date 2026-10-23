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

Schematics use SWorldEdit-specific compressed NBT (not Sponge/FAWE `.schem`). Block IDs and metadata are stored as integer arrays, preserving IDs above 255. Older SWorldEdit byte-array files remain readable; previously truncated IDs cannot be recovered and require saving the original structure again. IDs remain core-specific: use a compatible core when loading. Unsupported ID/metadata pairs are rejected before placement. This format stores the primary block layer only; block entity NBT and additional layers are not included.

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
`stack.blocks-per-tick` in `config.yml`. The initial implementation copies the primary
block layer only, without block entity NBT, entities, or additional layers.
