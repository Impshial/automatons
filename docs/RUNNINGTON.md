# Runnington guide — 0.5.0

Runnington is a job on the existing Automaton. Its modeled backpack automatically doubles the configured base cargo capacity: **18 cargo slots** at the default nine, plus the same **three retained tool slots**. No backpack item is required. Delivery tools belong in cargo; they do not equip themselves.

## Set up a warehouse

1. Configure a Totem as **Warehouse** in **Worksite Settings → Worksite**, choosing its facing before creating the project. The existing Totem remains the center of the rear wall.
2. A Buildington from a ready Builder site accepts the warehouse construction project. Give it a Hammer and provide real materials in the registered supply chests. Partial kits are allowed. The **Build List** shows exact total, remaining, available and missing quantities.
3. Assign Runningtons to the finished warehouse for physical supply and collection work. They never withdraw tools, Hammers, torches or saplings from any chest; those must be supplied locally to the worker that uses them.

The 7×6×9 schematic requires **81 oak planks, 38 oak logs, 56 oak slabs, 26 cobblestone, 12 glass, 17 chest items, 4 torches, one oak door and one item frame**: 236 items total. Sixteen chest items create eight double chests; the seventeenth is displayed in the frame. The existing Totem is excluded. Both door halves consume one item. The porch, frame and full building footprint follow the newest supplied schematic.

A glowing yellow boundary marks construction. The Buildington clears approved soft terrain and can safely evacuate a registered staging chest within the footprint, preserving its contents. It checks actual blueprint cells and remaining materials rather than unrelated surrounding changes. Finished chests must have usable lids before handover.

**Pause/Resume** control construction. **Repair** explicitly schedules missing warehouse blocks; a different block at an expected cell pauses for review. Finished warehouses are never automatically rebuilt over player changes. Chest removal, changed pairing or blocked lids makes that container unavailable until repaired.

## Supply and collection

Work Totems join the network through assigned workers from the same home pedestal, owner and dimension. Sharing a Totem across different homes is rejected. Runners receive stock reports and stable requests, but items remain in their actual inventories.

Deliveries prioritize permitted construction-material shortages. Tools, torches (including soul/redstone torches) and saplings stay in every chest, even when a worker reports a shortage. Supply those directly or let workers use their own local restocking. A runner walks to eligible surplus, picks up the reserved amount, then walks to a safe direct handoff. Parcels already carried can finish or return safely. If a worker cannot be reached, it can use a valid chest at that work Totem. Workers continue their existing local restocking without couriers.

Collection takes eligible output from work chests and registered mine depots, preserves working reserves, and brings it to available warehouse storage. Runners use the mine's saved open entrance, ladder and underground platform. They do not scan arbitrary distant chests, move items remotely or force-load routes. Warehouse stock is never ordinary collection output, preventing circular warehouse-to-warehouse hauling. A fairness turn gives collection opportunities during repeated shortages.

The worker menu shows the task, cargo and recipient. **Totem → Logistics** shows each worker's current activity, requests, warehouse stock and deliveries. Pickup requests are labeled as requests. **Worksite Settings → Network** contains construction controls. Existing names, Find glow and name-label toggle remain available.

## Interruptions and cargo

- A source change reduces pickup to the real accepted amount. Destination capacity is reserved across runners and rechecked before transfer.

- Canceled deliveries return to warehouse storage. If every usable chest is full or unavailable, cargo stays in the backpack and the runner retries. No automatic valuable-item spill is used to clear the queue.

- **Call All Home** suspends errands and returns with the actual parcel. Unreserved cargo can be deposited when storage is usable; reserved deliveries remain owned by that runner. **Release** reconciles and resumes work.

- Unloaded destinations defer work. Missing assignments release claims under the existing lifecycle rules. A missing home never causes automatic adoption by a different home.

- Every profession selection requires **Apply Job**. Changing an existing job requires confirmation that the current workload will be interrupted/reset. Shared buildings and actual inventory remain intact. To change away from Runnington, cargo first consolidates into normal capacity with metadata intact. If it cannot fit, the job remains Runnington and the menu says **Unload backpack before changing job**.

- Packing drops all actual cargo and tools once, following existing rules. The packed Automaton preserves job, identity, name, home and assignment; redeployment creates an empty eighteen-slot backpack. Ordinary death/removal handling does not manufacture a replacement parcel.

## Appearance and clearance

The current baseline was already **26 model pixels / 1.625 blocks** tall. That height is retained. Head width changes **8→9**, with height/depth **8×8** unchanged; torso width changes **8→9**, with height/depth **9×6** unchanged. Both are centered. Open eyes change **1→3 source pixels** wide with a one-pixel gap. Every face is mouthless, including happy/worried states and packed/display assets. The 128×128 entity atlas still maps one texel per model unit; block and item sprites remain 16×16.

Collision stays **0.7×1.625 blocks**. Base arms span fifteen model pixels before animation. The backpack is seven pixels wide and extends three pixels behind the torso, with a one-pixel parcel detail. Unassigned workers have no gear; Lumbertons have green work clothing/axe detail, Miningtons a helmet/lamp/apron, and Runningtons straps/backpack. Gear follows the synchronized job and torso/head animation.

Physical passage checks and visual review are recorded in [VERIFICATION.md](VERIFICATION.md). Held tools and animated limbs can extend beyond the collision box while turning or working; the model is not globally shrunk. The lamp is decorative and does not replace real torches.

Balance keys: `runnerWalkingAttribute` (0.215), `logisticsReconcileTicks` (40), and `runnerCollectionBatch` (256). Cargo capacity derives from twice `cargoSlots`; other jobs retain their configured base size. Existing custom configuration is preserved.

Stock reporting has its own `logisticsSlotsPerReconcile` budget (512 slots per home network per reconciliation, configurable 54–4096). Containers rotate through this bounded scan so low terrain-scan settings or larger networks do not permanently starve reports. Loaded snapshots remain cached between samples; every physical transfer rechecks the real inventory.

Overlapping Totem worksite ranges share one report and reservation for each physical container. Registered warehouse chests remain warehouse stock when another Totem discovers them.

Long trips can use successive safe route segments. Couriers replan as they advance and transfer items only after reaching the actual recipient or container.

The Minington helmet is now 10×3×9 model pixels, one pixel wider and taller than its previous 9×2×9 size. Only that gear extends the model to 27 pixels; body proportions and collision remain unchanged.
