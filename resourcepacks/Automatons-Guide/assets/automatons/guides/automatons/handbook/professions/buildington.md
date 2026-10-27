---
navigation:
  title: "Buildington"
  parent: "professions/index.md"
  position: 4
  icon: "automatons:hammer"
categories:
  - "professions"
---

# Buildington

Equip a **Hammer**, choose Buildington and assign the worker to a **Builder** Totem. Your first builder constructs its own shed before taking other projects.
Use a reachable staging chest inside the shared range and outside the shed doorway. This bootstrap does not need a warehouse or courier.

Open **Build List** to see total, remaining, available and missing items. Include one extra chest item. The builder places a temporary supply chest outside the footprint, stages available supplies there, and begins building with a partial load. It uses what is available before fetching more; the full material list is not required to start. Runningtons can deliver materials directly there; tools, torches and saplings are fetched locally by the builder.
Missing materials cause a recoverable wait after inventory and local storage checks.

After its shed is ready, the Buildington accepts another worksite's construction project in the same Network. Its claim survives supply trips. When no project is available, it returns inside its completed shed and waits there without pacing. A new construction request sends it back out to work. Recall still brings it to the main Pedestal.
The builder chooses safe footing outside the building footprint and places the blueprint from there, including empty foundation cells and upper blocks. It faces the target and swings its Hammer. The builder walks between safe perimeter positions after small batches. Blocks can be placed every two ticks when placement rules allow. Each successful placement plays its block sound and consumes real material and Hammer wear.

It validates blueprint cells while building. Unrelated surrounding changes do not cancel construction.
Obstructing blocks in required blueprint cells are broken automatically, including hard blocks and containers. Contents are preserved; unbreakable blocks and protection rules still stop work. Extra workstations and containers in blueprint air cells remain. After building, the builder reclaims its temporary chest and returns it and leftover materials to its Builder site storage. Breaking the Totem frees the reserved zone and leaves stored items intact.

Completed buildings are not automatically rebuilt over your edits. Use **Repair** to schedule missing cells; different blocks at expected cells require review.


[Building and setup](../worksites/builders-shed.md).
