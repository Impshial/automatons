# The Machine and Operatorton

The Machine is a Lumina-powered vehicle for Minecraft 1.20.1. It has a three-block-wide chassis, 108 cargo slots, a single seat, and physical Blade or Bore attachments. Assemble its components, form it with the Automaton Wand, link a home Pedestal, and refuel before driving or starting a job.

## Assembly

Build both layers below, reading front to rear. Left and right are viewed from behind the vehicle. All 24 positions must be occupied.

| Bottom left | Bottom center | Bottom right |
| --- | --- | --- |
| Frame | Frame | Frame |
| Track | Frame | Track |
| Track | Frame | Track |
| Track | Frame | Track |

| Top left | Top center | Top right |
| --- | --- | --- |
| Frame | Attachment Mount | Frame |
| Cargo Compartment | Operator Station | Cargo Compartment |
| Cargo Compartment | Tank | Cargo Compartment |
| Frame | Lumina Engine | Frame |

Use **six Tracks, ten Frames, four Cargo Compartments, one Mount, one Station, one Tank, and one Engine**. The Tank is the existing Collector Tank, including any stored fluid. Right-click a component with the Wand in Assign mode. Invalid patterns remain intact. The chassis compacts from 3×2×4 to 3×2×3, leaving its front aligned with the original front. It starts parked, with the engine off and no attachment.

Craft components using JEI or the handbook's live recipes. The Bore consumes three **undamaged** Diamond Pickaxes. Program cards use two Paper, Redstone, and an Infused Lumite Shard.

## Fuel and controls

Right-click the body for Controls, Program, and Storage tabs. Right-click the empty central seat to board, or use **Board empty seat** in Controls. An occupied seat is never forcibly emptied. The **I** key opens Controls while riding; **R** starts/stops the engine. Forward/back and left/right use your configured movement keys. Turning requests a cardinal 90-degree turn. Your configured sneak key dismounts and parks. Space has no Machine action.

Put a full or partial **Luminall Bottle**, bucket or filled Tank into the refueling slot. The fixed conversion is **250 mB = 125 Lumina**. Only fluid that fits is transferred; excess stays in its container. Empty bottles return glass. Tank contents transfer on assembly. Stopped engines consume no fuel. Starting the engine alone never resumes a saved job.

The initial balance in `MachineRules` is: 10,000 Lumina capacity; 30-tick startup; 12-tick shutdown, movement and turning; four ticks per cutting transaction; one third of a Lumina per block moved, quarter-turn, or block broken. These are simulation ticks, so movement takes 0.6 seconds at 20 TPS. Breaking costs are applied to each harvested block, including recognized trees.

All modes stay within a **48-block horizontal radius** of the linked home Pedestal, checking the whole vehicle and attachment. Controls lists eligible Pedestals by position and available operators by name. No terrain is force-loaded. Obstructions, protection, cargo capacity, fluids and unloaded terrain produce specific status messages.

The Machine crosses supported full-width one-block steps and descents. It does not bridge gaps, climb isolated narrow steps, or drop down cliffs. Turns check their swept space and do not break blocks. Reverse is available even with full cargo; an attachment does not work behind the vehicle.

## Attachments and cargo

Install one attachment through Controls while deliberately parked or paused. Attachments cannot be toggled or swapped during active work, including the brief stop between grid steps.

- **Blade:** a broad three-wide, two-high soft-material cutter. It clears vegetation and shovel/axe materials, recognizes whole natural trees, and refuses pickaxe blocks. Eligible natural harvests credit the existing Lumina rewards once, directly to the engine. Placed vegetation follows the existing eligibility rules.
- **Bore:** nine diamond-tipped drills in a 3×3 bank. It cuts pickaxe materials at Diamond Pickaxe capability, leaves the floor, and earns no Lumina from mining. It does not clear dirt or other soft blocks.

Storage has **108 real slots**, displayed as two scrollable pages of six rows. Stack merging and item stack limits determine capacity. A cut is refused if all its output cannot fit. Runnertons on the same network can collect real cargo through their existing delivery requests; their normal exclusions for tools, torches, and saplings still apply.

## Programs

Insert a **Program** card and use the Program tab. Name it, edit numbered lines, insert instructions at the cursor, and use Check or Save. **Save & Start Work** saves the card and starts from the Machine's current position. The route sketch includes its three-block width; terrain can still obstruct that route.

| Instruction | Action |
| --- | --- |
| `N`, `S`, `E`, `W` | Face that absolute direction without moving |
| `N10`, `E3`, etc. | Face that direction, then move that many blocks |
| `.` | Wait one second; `...` waits three seconds |
| `DROPOFF` | Retrace the recorded route to the job's starting point and unload |

Lowercase, compact text, spaces and newlines work. Distances are positive integers up to 4,096. Invalid text reports a source line/character before any work begins. Text is limited to 8,192 characters and 2,048 instructions. There are no loops or implicit reverse instructions.

Example:

```text
S10
...
DROPOFF
E3
```

After `DROPOFF`, `E3` begins at the saved starting point. Put receiving chests beside that starting point, outside the vehicle's movement/turning footprint. Nearby accessible chests within four blocks of the start, and recognized network dock chests within six, are considered. Insufficient space waits for storage or courier collection.

Pause retains the cursor, remaining distance, waits, start, and return route. Resume continues them; Start Work begins a new job, and Cancel discards continuation. The physical Program must remain installed and unchanged. Driving away manually requires returning to the saved work pose before Resume. Saving/reloading parks with continuation retained; use Resume when ready. Fuel exhaustion also retains progress.

## Chunk surface clearing

Install the Blade, choose Chunk mode, and select the current chunk. A yellow outline previews the 16×16 area. Start Work follows overlapping three-wide surface passes and stops after that chunk. It does not become a quarry or automatically select another chunk. Turns may need clear space beyond the boundary; neighboring blocks are never harvested. A boundary-crossing tree or obstructed maneuver remains visibly blocked until resolved.

## Operatorton and delivery

Assign an eligible ordinary worker from Machine Controls, or select it with the Wand and use the Wand on the Machine. Assignment automatically selects **Operatorton**. Its identity, owner, energy and cargo remain intact; incompatible equipment is safely relocated or assignment is rejected if no space exists. Worker and Machine must belong to the same owner and home network. Each can have only one assignment.

The worker walks to the Machine and physically boards. Assignment/boarding alone does not start work. Once seated, Start Work runs the chosen Program or Chunk job. If cargo fills, the pilot reverses/retraces the recorded route to the start, unloads, then returns to the exact saved work position and heading. Work progress is preserved. Unattended Machines instead request a Runnerton and retry when space is available.

Release pauses the job, dismounts the worker, and clears both assignments. Driver loss, job changes, recall, or invalid ownership pause operation; no unattended takeover happens. Shift-right-click any part of the Machine with the Wand, in any mode, to pack it as one carryable Machine. Dismount first; an assigned Operatorton is released. Cargo, attachments, Program and fuel are preserved. Right-click clear solid ground to reform it with the engine off. A new location clears the old physical route but keeps the Program. To dismantle into components instead, sneak-hit the parked Machine with its seat empty; unused fuel is retained in the returned Tank.

## Artwork

Machine faces use existing Minecraft 16×16 material textures, tiled at native block density. Frames are iron with small copper rivets. The Collector retains its separate copper-free stone design. Both 16×16 Lumite textures have brighter purple interiors, with the existing rock background and chipped rim unchanged.
