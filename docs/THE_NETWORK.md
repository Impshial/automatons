# The Network

The home Pedestal is the Network controller. It owns worker identities, membership, names, recall and the overview. A Totem owns one local worksite, its purpose, building project, discovered work and nearby stock. Workers execute physical actions for those sites.

## One Totem range

Each Totem has **one horizontal range for both work and nearby chests**, with no inner exclusion.

| Purpose | Default range | Surface footprint |
| --- | ---: | --- |
| Lumber Yard | 32 | 65 × 65 |
| Farm | 32 | 65 × 65 |
| Mine | 6 | 13 × 13 |
| Builder | 8 | 17 × 17 |
| Warehouse | 8 | 17 × 17 |

Installed upgrade bonuses are disabled; these are the current base ranges. Chest height and tree height are separate vertical allowances, not inner or outer surface areas. A Mine Totem's underground excavation extent and registered underground depot are infrastructure outside its small surface entrance area.

**Show Work Area** displays the shared surface coverage. There is no blue storage region. Double chests require both halves within range and usable lids. Discovery samples loaded block entities with a fixed budget, and actual transfers check current range and access again. No chunks are force-loaded.

## Set up a worksite

Place a Totem where the building's rear-wall anchor should remain. Open **Worksite Settings → Worksite**, select a purpose and facing, then **Create Worksite**. The glowing yellow outline shows the reserved building footprint. The Totem stays in place and retains its identity and contents. Breaking it immediately releases the construction reservation and removes its yellow outline; existing buildings and stored items remain.

The purpose belongs to the site; a profession belongs to a worker. Choosing a profession uses the worker's list and **Apply Job**. Changing an existing job asks for confirmation because it interrupts that worker's current workload. Assigning its first job does not need that confirmation. A Totem cannot be shared between different home Networks.

Open **Build List** for total, remaining, available and missing materials. Quantities are placed items: a door costs one item, a double slab costs two slabs, and each chest block costs one chest. Identification frames and their displayed items are separate costs. Schematic containers start empty.

## Buildingtons

Make a Hammer with the shaped recipe `C I C / . S . / . S .`, where C is cobblestone, I is an iron ingot and S is a stick. A Hammer has 2,000 durability. Equip it in a Buildington's three tool slots, or leave a spare in its assigned worksite chest for the worker to fetch.

To bootstrap the first builder, configure a Builder Totem and put its supplies in a reachable chest inside the shared range but outside the shed footprint and entrance approach. Assign a Buildington. It builds its own shed before accepting other construction requests; no Runnington or finished warehouse is needed.

After that shed is ready, the builder accepts a construction project from the same Network. Its project claim persists through shortages and restocking. Another builder can accept a different project, but cannot duplicate that claim. Completed blocks and the claim are saved. A temporarily unloaded builder retains its claim; recall, packing, death, unassignment or a profession change releases it for another builder to resume.

Builders spend one extra chest item to place a temporary supply chest outside the footprint. Build List includes that chest. They stage available materials there and begin with a partial kit, using their assigned Builder site or the destination site's storage to replenish only when needed. Runningtons can deliver eligible supplies directly to the temporary chest, even before their own warehouse is ready. Tools, torches and saplings remain local-worker supplies. During building, refills come from this chest. After handover, the builder empties and removes its temporary chest, returns the chest item and leftovers to its Builder site storage, and waits inside its completed Builder shed. If the Totem is broken, the temporary chest and its contents remain as ordinary storage. Missing supplies cause a recoverable wait and remain visible in the Build List. The builder checks the schematic cells while building; unrelated changes outside the build do not invalidate it. The builder chooses safe footing outside the footprint, faces each target and swings its Hammer. It builds empty foundation cells and upper levels from outside, walking clockwise to another safe position after each small batch. If a side is unreachable, it can continue from its existing safe footing. Placement can occur every two ticks when survival and attachment rules permit it and the placement succeeds. Failed placement consumes no material or Hammer durability.

Builders automatically break obstructing blocks in required schematic cells, including hard blocks and containers. Container contents remain real items; block drops follow tool harvest rules. Protected or unbreakable blocks stop construction with a specific status. Extra workstations and containers in blueprint air cells are preserved. Structural logs in planned and completed blueprints are excluded from forestry discovery and cutting.

Production workers waiting for their worksite to be constructed return to the main Pedestal. Buildingtons with a completed shed return inside it when no construction request is available; while their shed is unfinished they wait at the main Pedestal. Recall still returns them to the Pedestal. Runningtons can still handle construction deliveries while awaiting their warehouse.

The surface minehead has no trapdoor. Once its Buildington finishes, the Mine Totem records that handover and Miningtons share the existing shaft, platform and underground storage work. The 20-block descent, 19 ladders and four underground chest blocks remain mining infrastructure rather than a second surface-building project.

## Farmingtons

Assign Farmingtons with ordinary hoes to a completed Farm site. Supply wheat seeds, carrots, potatoes or beetroot seeds through their cargo or registered local chests. Existing irregular farmland inside the shared range is adopted. When no farmland exists in the fully loaded survey area, the Totem reserves one reachable, level 9 × 9 plot with 80 crop cells and one central water cell. Multiple farmers share that board instead of creating one field per worker.

A new field requires a real water bucket. Irrigation leaves one empty bucket. Farmers collect field drops, harvest mature crops, replant harvested cells, then prepare and plant other cells. Hoes wear when tilling; normal crop growth and drops apply. Workers do not trample farmland.

**Worksite Settings → Farm** controls crop preference and optional bone meal. Bone meal is used only when enabled and available; its absence is not a fault. Supplies and tools are fetched physically, and full or blocked storage preserves cargo. Protected or unloaded cells are not modified.

The Network retains a full field's planting stock across local chests and farmer cargo. Carrots and potatoes count as both produce and planting stock. Runningtons collect only the surplus, and recheck reserves before withdrawing a parcel. Delivery reservations retain ownership through travel and unloading.

## Runningtons and recovery

Runningtons carry supplies and collect outputs between registered sites, including the two double chests at the bottom of a completed mine. They never withdraw tools, Hammers, torches or saplings from chests. Those items must be supplied locally for the worker that uses them. Shared stock reports do not move items: every extraction and insertion happens at a reachable inventory.

The Pedestal's **Call All Home** takes priority over work. Unloaded destinations remain unavailable, rather than being reported empty or destroyed. Packed workers drop their actual cargo and tools once, while shared projects and fields remain at their Totems. The existing owner checks and Forge placement/break hooks apply to worksite actions.

The authoritative records remain the existing worker Ledger and world journal. Buildings generalize the earlier warehouse journal; fields are keyed by their Totem identity. Discovery is shared by the site, and stock reports and parcel reservations remain in the existing Network service. Workers do not independently scan every Network inventory each tick.

Adjusted schematics and manifests are in [`reference/worksites`](../reference/worksites). The source bundle remains under `source`; `tools/import_worksites.py` reproduces the runtime blueprints and edited exports. The mine hatch is removed, and the Builder's Shed chest pairs are moved within four blocks of its anchor.
