AUTOMATONS WORKSITE SCHEMATICS

Ten actual Sponge v2 .schem files, ten matching native Java .nbt structure files,
and their coordinate/material manifests. Target: Minecraft 1.20.1, DataVersion 3465.

CONTENTS

schematics/   Gzip-compressed Sponge v2 files.
structures/   Gzip-compressed native Minecraft structure templates.
manifests/    Exact placement/clearing cells, anchors, entities, site metadata and costs.
previews/     Isometric geometry previews and labeled block plans.
PREVIEW.html  Open locally to browse the previews.
MATERIALS.csv Per-plan construction items, terrain work and fluid operations.
catalog.json  Dimensions, anchors and plan types.
tools/        Reproducible exporter, preview renderer and independent validator.
VALIDATION.json  Results of the completed offline checks.
CODEX-START-HERE.txt  Import/integration instructions.
Automatons-Full-Implementation-Prompt.txt  Complete gameplay specification.

FILES

crafterton_workshop          7 x 8 x 7
hunterton_shed               7 x 5 x 7
rancherton_ranch            23 x 9 x 17
quarryton_surface           15 x 8 x 25
luminariton_tower_courtyard  25 x 14 x 33
fisherton_hut_dock          11 x 9 x 17
farmington_field             9 x 4 x 9
minington_strip_level       53 x 5 x 32
minington_landing            5 x 5 x 7
minington_shaft_step         1 x 4 x 2

Dimensions are X width, Y height, Z depth of the entire exported box. A normal
shed still has the approved 7x7 footprint. Larger boxes include the associated
site, a roof overhang, or underground access. Empty/preserved bounding cells do
not represent extra construction.

COORDINATES AND ANCHORS

Generator and worksite metadata use logical coordinates: east is +X, south is
+Z, and walking height is Y=0. Floor blocks usually occupy Y=-1. Letters map
A=0, B=1, etc.; row 1 maps to Z=0. Most doors face south. The ranch's open front
and gate face east, as in the approved landscape plan.

Each manifest includes logical_min and origin_local. Convert a logical point
to file-local with local = logical - logical_min. All block entries include
both forms. worksite/features use LOGICAL coordinates; block entries, entities,
origin_local and native NBT use FILE-LOCAL coordinates. Convert metadata when
adapting to the mod's runtime schema.

The anchor is the totem block for complete worksites. The farm component uses
its south-center walking position; mining components use the ladder/access
position. Do not create an extra totem for a field, landing, or shaft component.
The .schem files include WorldEdit WEOffset metadata so the anchor, rather than
the minimum corner, aligns with the paste position. Native NBT placement must
subtract origin_local from the desired anchor position.

PRESERVE, CLEAR AND PLACE

PRESERVE: unspecified cells. Encoded as minecraft:structure_void in .schem and
omitted from .nbt. They must not be placed, cleared, billed or excavated.
CLEAR: explicit minecraft:air cells. Perform only the relevant authorized site
clearing or progressive mining operation, never overwrite protected containers.
PLACE: a concrete block, including its facing and other state properties.

For an optional WorldEdit preview in a disposable test world with Automatons
and a compatible WorldEdit installation, load the .schem and exclude structure
void from the paste. Use //help paste to verify your installed flags; supported
WorldEdit 7 builds offer the source mask form:

  //schem load <filename>
  //paste -e -m !minecraft:structure_void

Do not add -a when you need the explicit interior/shaft air cells. Never import
these as legacy .schematic files. This package was not pasted in WorldEdit here.
Native .nbt files should likewise retain omitted cells and include entities when
the structure is placed. Runtime construction must charge for their entities.

ITEMS AND MATERIALS

construction_items is the exact bill for placed structure blocks and entities,
excluding an already-placed totem. Double chests cost two chest items. Doors cost
one item for both halves. Armor stand, item frame and framed shears are counted.
Stacked chests are not extra-large custom inventories. All inventories are empty.

terrain_fill_if_not_reused lists the maximum dirt fill implied by soil cells.
Reuse existing suitable soil, grass, or farmland where valid. Grass spreading
and tilling are terrain operations, not free grass blocks or farmland items.
For a ranch, choose a grassy site; dirt replacement alone is not immediately
grazable grass. Do not require unobtainable farmland items or silently provide
Silk Touch grass. Required hoe/tilling labor is not a consumed building item.

fluid_operations counts real source placement operations. The farm needs one
water-source placement and a returned empty bucket. The Fisherton site needs
70 existing source-water cells; its construction bill contains no water supply.
Material bills are per file. Do not sum the full mining level and its landing/
shaft components as if they were independent buildings; those files overlap.

REBUILD

Exporting binary structures and manifests uses Python 3's standard library:
  python tools/build_schematics.py
  python tools/build_schematics.py --registry-map registry-map.json

Preview rendering requires Pillow:
  python tools/render_previews.py

Independent decoding requires nbtlib (validated with 2.0.4):
  python tools/validate_schematics.py
  python tools/validate_schematics.py --block-report <1.20.1 reports/blocks.json>

render_previews.py rebuilds from the generator before rendering. If remapping
IDs, pass the same --registry-map registry-map.json argument to that script.

CHECKS COMPLETED

Independent nbtlib decoding; Sponge v2 palette/varint/volume checks; equality of
schematic, native NBT and manifest cells; exact material totals; empty storage;
entity positions; chest pair facing/clearance; ladder backing and door halves;
all used vanilla IDs/properties checked against reports generated by Mojang's
official 1.20.1 server; DataVersion verified from that server's version.json;
approved dock, courtyard, quarry, crop strips and mining passage dimensions.

LIMITS

These are real geometry files, not a built mod. No actual Forge repository or
client was supplied in this workspace. Custom block registrations/defaults,
ownership/job initialization, AI navigation, runtime building, rotation effects,
light propagation and in-game rendering still need checks in the repository.
The previews use simplified colors and shapes and are not Minecraft screenshots.

FORMAT REFERENCES

Sponge v2 specification:
https://github.com/SpongePowered/Schematic-Specification/blob/master/versions/schematic-2.md
WorldEdit clipboard and source masks:
https://worldedit.enginehub.org/en/latest/usage/clipboard/
Official Minecraft version metadata used for validation:
https://piston-meta.mojang.com/mc/game/version_manifest_v2.json
