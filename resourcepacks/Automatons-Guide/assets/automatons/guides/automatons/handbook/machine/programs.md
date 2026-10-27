---
navigation:
  title: "Programs and attachments"
  parent: "machine/index.md"
  position: 1
  icon: "automatons:program"
item_ids:
  - "automatons:program"
  - "automatons:blade_attachment"
  - "automatons:bore_attachment"
---

# Programs and attachments

Install the Blade for soft terrain, vegetation and natural trees. It refuses pickaxe blocks and earns normal eligible vegetation Lumina directly into the engine. The Bore cuts a three-wide, three-high pickaxe tunnel, leaves the floor and earns no Lumina. It requires three undamaged Diamond Pickaxes to craft.

<Recipe id="automatons:blade_attachment" />
<Recipe id="automatons:bore_attachment" />
<Recipe id="automatons:program" />

## Program language

Insert a Program in Controls, open Program, name it and edit numbered lines. Buttons insert instructions at your cursor. Check validates all instructions; Save stores them on the card. Save & Start Work records the current position and begins.

| Instruction | Meaning |
| --- | --- |
| N, S, E, W | Face north, south, east or west without moving |
| N10, E3 | Face the direction and move that many blocks |
| . | Wait one second; three dots wait three seconds |
| DROPOFF | Retrace the route to the saved start and unload |

Lowercase and compact text are accepted. Distances must be positive integers, at most 4,096. Errors identify a line and character. There are no loops. The route sketch includes the three-block width, but terrain can still block it.

After DROPOFF, the next move begins at the start. Place receiving chests beside the start and outside the movement footprint. Nearby chests within four blocks and recognized network docks within six are considered. Full chests cause a wait, not item loss.

Pause retains remaining distance and the instruction cursor. Resume continues; Start Work begins anew and Cancel clears continuation. The physical Program must remain unchanged and installed. Reloading parks with saved progress. After manually moving away, return to the saved work pose before resuming.

## Chunk mode

Select Chunk with the Blade installed, select the current chunk, and inspect its yellow outline. Start Work clears surface passes within that 16×16 boundary. It never harvests neighboring chunks, excavates a quarry, or selects another chunk automatically. Clear room around the edges may be needed for turns. A tree crossing the boundary remains blocked until resolved.

For automatic unloading and returning to work, assign an [Operatorton](../professions/operatorton.md). Unattended Machines request Runnerton collection when full.
