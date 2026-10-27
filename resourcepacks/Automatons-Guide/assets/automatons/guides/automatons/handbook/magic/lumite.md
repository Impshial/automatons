---
navigation:
  title: "Finding Lumite"
  parent: "magic/index.md"
  position: 2
  icon: "automatons:lumite_ore"
item_ids:
  - "automatons:lumite_ore"
  - "automatons:deepslate_lumite_ore"
  - "automatons:lumite_shard"
---

# Finding Lumite

Mine stone Lumite in newly generated **Overworld** terrain from **Y=-32 through Y=64**, favoring **Y=16**. **Deepslate Lumite** has its own distribution from **Y=-64 through Y=0**, peaking at **Y=-32**. Each replaces only its matching rock type. Existing chunks are unchanged.
Use a **stone-tier or better pickaxe**. The stone and deepslate forms follow their surrounding rock.

<ItemImage id="automatons:lumite_ore" scale="3" />
<ItemImage id="automatons:deepslate_lumite_ore" scale="3" />

Either ore drops **1-3 raw Lumite Shards** without enchantments. Fortune increases the yield; Silk Touch returns the corresponding ore block.
The bright veins remain visible in darkness, but emit **no block light**. They cannot replace torches or prevent spawning.

The default generation uses **8 attempts per chunk** and configured vein size **8**.
Size is a generation setting, not an exact block count. Rock, caves, biome eligibility and vein overlap change actual yields.
Existing chunks and mined-out areas are not refilled. Server data packs can replace the default generation settings.

When optional **Just Enough Resources** is installed, both ore forms share a native ore-generation entry.
Its graph estimates candidate placements from the bundled generation algorithm before terrain filtering; it is not a survey of your world's ore.
The native graph is labeled **Default estimate**. Hover it for the configured height bounds, attempts, vein size and tool/drop rules.
The client integration cannot read a remote server's unsynchronized world-generation data packs, so overrides may differ. Check the server's settings or use JER's own profiling for that world.
