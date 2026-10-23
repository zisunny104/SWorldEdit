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

Schematics are stored in **compressed NBT format** for maximum efficiency. This allows saving large structures in a compact way and reusing them easily.

### Utility Commands

- `//wand` - Give selection wand axe  
- `//rotate <axis> <degrees>` - Rotate clipboard content  
- `//help [page]` - Show command help (currently not working)  

## Block Specification

Blocks can be specified in multiple formats:

- **Numeric ID**: `1`, `5:2`  
- **Block Names**: `stone`, `oak_planks`  
- **Full Names**: `minecraft:stone`  

### Languages
Set `language: zh_TW` (or `en_US`) in `config.yml` and restart the plugin.
Bundled catalogues are copied to `lang/` without overwriting existing files.
Edit those YAML files to customize messages, or add `lang/<locale>.yml` for another
language. Missing entries fall back to English. `${0}`, `${1}`, etc. are literal
replacement arguments; preserve them when translating. Tool identifiers and block
names supplied by the core are not translated by the plugin.
