---
navigation:
  title: "The Machine"
  parent: "index.md"
  position: 8
  icon: "automatons:lumina_engine"
item_ids:
  - "automatons:packed_machine"
  - "automatons:machine_frame"
  - "automatons:machine_track"
  - "automatons:attachment_mount"
  - "automatons:operator_station"
  - "automatons:cargo_compartment"
  - "automatons:lumina_engine"
---

# The Machine

The Machine clears soft terrain with a Blade or bores a 3×3 tunnel with nine diamond drills. It has 108 cargo slots and one seat. Drive it yourself, run a Program unattended, or assign an Operatorton.

## Assemble both layers

Read front to rear, with left/right viewed from behind. Fill every cell.

| Bottom left | Center | Right |
| --- | --- | --- |
| Frame | Frame | Frame |
| Track | Frame | Track |
| Track | Frame | Track |
| Track | Frame | Track |

| Top left | Center | Right |
| --- | --- | --- |
| Frame | Mount | Frame |
| Cargo | Station | Cargo |
| Cargo | Tank | Cargo |
| Frame | Engine | Frame |

Totals: six Tracks, ten Frames, four Cargo Compartments, one Attachment Mount, one Operator Station, one existing Tank and one Lumina Engine. Right-click with the Wand in Assign mode. The 3×2×4 assembly becomes a parked 3×2×3 chassis, engine off.

## Controls and fuel

Right-click its body for Controls. Click any exposed face of the empty Operator Station, or use Board empty seat. While riding, **R** starts/stops the engine and **I** opens Controls. Your movement keys drive forward/reverse and turn 90 degrees. Your sneak key dismounts and parks; the driver HUD shows your actual bindings.

Right-click any part of the Machine with a full or partial Luminall Bottle, bucket, or filled Tank to transfer fuel in one interaction. The Controls refueling slot accepts the same containers. Only the amount that fits is transferred; excess stays in the container. Empty bottles return Glass Bottles, and a carried Tank stays a Tank. By default **250 mB = five Lumina**. The engine holds 10,000 Lumina. Starting alone never resumes a saved job; the HUD shows **Starting... Please wait** during its 30-tick startup. Holding R produces only one start/stop action per press.

The complete footprint must stay inside 48 blocks horizontally from its linked home Pedestal. Use Controls to choose another eligible home. It crosses full-width one-block steps and descents, checks turning space, and stops at fluids, gaps, protected work or unloaded terrain.

Pause to change attachments. Storage shows all 108 slots on two scrollable pages. Runningtons on the same network can collect cargo. Shift-right-click with a Wand in any mode to pack it as one carryable Machine. Dismount first. Right-click clear solid ground to reform it, with cargo, attachments, Program and fuel intact. It starts parked; moving it clears the old physical job route, so start a new job. Sneak-hit a parked, empty-seat Machine to dismantle it into components instead.

<SubPages icons={true} />

## Component recipes

<Recipe id="automatons:machine_frame" />
<Recipe id="automatons:machine_track" />
<Recipe id="automatons:attachment_mount" />
<Recipe id="automatons:operator_station" />
<Recipe id="automatons:cargo_compartment" />
<Recipe id="automatons:lumina_engine" />
