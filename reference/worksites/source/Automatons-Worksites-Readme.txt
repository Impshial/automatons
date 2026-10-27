AUTOMATONS WORKSITE SCHEMATICS

Minecraft Java 1.20.1 / Sponge Schematic v2

CONTENTS
- Warehouse: existing building preserved, adds chest item frame only.
- Mine entrance: existing blocks preserved, closes the rear bay around an exposed
  totem and adds a stone-pickaxe item frame. The totem is the new placement origin.
- Builder's Shed: compact oak building, shallow spruce roof, two front lanterns,
  four double chests and Hammer item frame.
- Lumber Yard: open-sided shelter with a single-slope roof, rear totem, two double chests,
  decorative log rack and stump, and stone-axe item frame.
- Farmer's Shed: oak building with a shallow hip roof, two double chests, porch,
  composter, seat, broad windows, rear totem and stone-hoe item frame.
  The crop field is separate and is not included in the shed schematic.
- Each structure has a .schem, design/placement notes, exported block manifest
  and preview. Manifests are companion integration data, not a required mod format.

IMPORTANT
The Builder's Shed depends on the NEW automatons:hammer item. Implement or map
that identifier before importing. The Hammer in the frame is additional to the
worker's usable Hammer. No Hammer recipe or durability value is finalized here.
All five structures require the existing Automatons totem block. Every building
uses its player-placed totem as the construction origin and leaves it exposed.
Future building templates must follow this rule. Set orientation and reserve
the footprint relative to the totem before placing any construction blocks.
Register all worksite structural logs, including the yard's rack and stump,
as excluded harvest targets so Lumbertons do not dismantle worksite buildings.
Item frames are entities. A blocks-only importer will omit them: import the
Entities list, and consume the frame and displayed item once per placement.
Never overwrite a live totem's block entity from a schematic. Preserve its data.
Building identification displays do not control totem purpose or worker jobs.
All material totals are finished placement items, not raw crafting ingredients.
For the warehouse, 17 chests means 16 storage chests plus 1 framed chest.
All files were checked programmatically; no in-game playtest was performed.
The totem-anchor rule applies to new construction. Do not automatically demolish
or relocate existing worksite structures or totems when migrating a save.

BUILDINGTON / FARMINGTON DISCUSSION
Every totem owns a required building. Several builders can share a Builder
worksite, with one durable assignment per builder and one claim per project.
The first builder must be able to build its own shed before its site is ready.
Farmington behavior is automatic: search for farmland outside the totem's
storage region but within its work region, and tend it if present. If there is
none, establish one shared 9 x 9 farm entirely within that non-storage region.
Additional Farmingtons help the same farm. They may apply supplied bone meal.
The totem should own the shared farm record and reserve its bounds before
preparation starts, avoiding duplicate farms when several workers arrive.
Buildington constructs the Farmer's Shed around its totem; Farmingtons handle
the fields. The accompanying Buildington/Farmington Codex prompt specifies
the farming workflow and initial balance defaults. The shed composter does
not add an automatic composting feature.

FORMAT REFERENCE
https://github.com/SpongePowered/Schematic-Specification/blob/master/versions/schematic-2.md
