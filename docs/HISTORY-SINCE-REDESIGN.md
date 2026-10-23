# Automatons — history since the last full redesign prompt

Scope: from your “ok, big redesign” message and `Automatons-Lumberton-Codex-Prompt (1).txt`, through the latest whole-number Magica display, connected/real-log felling, and path/stall recovery changes. Work before that prompt, such as the initial mod and creative-wand selection fix, is excluded except where it was regression-tested. This is a record of implemented changes and observed checks, not a claim that every possible terrain or mod combination was tested.

## 1. Full redesign — version 0.2.0

### Project and progression

- Kept the established Minecraft 1.20.1, Forge 47.4.10 and Java 17 target, using `automatons` as the namespace.
- Implemented the redesigned survival progression: collect Magica, infuse cores, craft body parts, assemble an Automaton item, deploy it, choose Lumberton, supply axes and assign a work Totem.
- Added the complete recipe chain for empty cores, infuser, home pedestal, Totem, wand, body parts, upgrade plate, and both tiers of Storage, Lumber, Speed and Efficiency upgrades.
- Kept Magica Core (the charged ingredient) separate from the torso component, which was originally named Automaton Core and later renamed Automaton Torso.
- Assembly uses the two charged cores embedded in the torso recipe; it does not charge the player a second time. Workers have no ongoing Magica fuel requirement.

### Magica and infusion

- Added a separate saved player Magica balance, independent of XP, enchanting and Mending.
- Implemented white/purple collection orbs with XP-like scatter, attraction, gravity, bounce, water behavior, pickup cadence, merging and lifetime, while keeping their identity and accounting separate from XP.
- Added rewards for eligible natural/grown logs, mature crops, fruit, ripe berries, flowers, stems, leaves and grass. Reward ranges and source tags are configurable.
- Added completed-break validation, cancellation checks, tall-plant deduplication and placement/growth tracking to prevent repeat placement farming and duplicate rewards. Machine, Automaton and indirect harvesting do not award Magica.
- Added the black/white half-height Infusing Pedestal with four claws and a floating core, operated in the world without a GUI.
- Infusion spends exactly 50 Magica, one point per four ticks, from the bound donor within four blocks. It pauses when the donor leaves, logs out, dies or runs out of points.
- Preserved partial charge, donor identity, retrieval permissions, safe stacking and single-drop block destruction. Two infusers cannot spend the same balance twice.
- Added chest-to-core transfer effects, increasing core glow, green completion particles and a single completion chime.
- Added a separate Magica HUD and a balance panel in the assembly screen; later revisions refined their layout and numeric display.

### Assembly, workers and portability

- Replaced direct creature creation with six body slots and a separate result slot: head; arm/torso/arm; two legs.
- Assembly commits only when a valid output is taken, including shift-click, destination-capacity checks and simultaneous-viewer protection. Previews do not create free workers or extra inventories.
- Added a permanently visible, tilted, rotating white/purple Automaton item preview above the home pedestal, independent of its inventory or the player's balance.
- Added Unassigned/Lumberton job selection, initial placement pause and explicit work authorization.
- Workers retain three real tool slots and initially nine cargo slots, using the actual supplied axes and switching tools when needed.
- Added Wrench packing/deployment with persistent worker identity, health, name, job and links. Real tools and cargo drop separately once when packed.
- Added identity/generation checks and saved-link reconciliation to prevent stale packed items from reviving invalid assignments.
- Added home recall/resume, explicit missing-home rebinding and preservation of unloaded or cross-dimension links without teleportation.

### Shared Totems, storage and forestry

- Replaced the single-worker Totem with a paginated roster of multiple same-owner workers, each retaining its own home.
- Shared Totem upgrades and chest settings across assigned workers; retained independent worker inventories and alerts.
- Added nearest-to-Totem dispatch, stable ordering, per-tree claims, fair scheduling and waiting states for claimed or temporarily unavailable work.
- Base logging coverage is a 33×33 square with ±16-block root-height allowance. Base storage coverage is 9×9 with ±4-block vertical allowance; upgrades expand horizontal ranges.
- Kept compatible cargo between trees, with required service visits unloading all accepted cargo across usable chests and restocking empty axe slots.
- Added chest alternatives, single/double-chest validation, independent input/output toggles and recovery after storage changes.
- Implemented recognition and worker approach/felling for oak, birch, spruce, jungle, dark oak, acacia, mangrove and cherry, including supported 2×2 trunks.
- Added natural mangrove/jungle attachment handling, shared-canopy preservation and water preservation when waterlogged attachments are removed.
- Added persistent fallen-timber inventories, consumed sections, partial loot, work progress, abandoned-job recovery and positional chopping sounds.
- Added bounded scanning, pathfinding and conversion budgets without force-loading chunks.

### Bugs diagnosed during the redesign

- Fixed mutable root coordinates, path endpoint/reach tolerances, navigation readiness and elevated-root handling.
- Prevented duplicate attachment drops during multi-block tree conversion by suppressing intermediate neighbor destruction.
- Corrected upgrade timing to 20%/40% Speed reductions and preserved separate fall-animation timing.
- Reworked the automated test environment to deterministic superflat terrain after old terrain/gravel caused misleading failures.
- Passed the final 46-test server suite and packaged client checks, then installed and archived 0.2.0.

## 2. Infusion appearance, crash investigation and JEI — version 0.2.1

- Replaced the low, uniform stream you disliked with overlapping bursts of 2–4 differently sized motes emitted from varied upper-chest/shoulder positions.
- Added independent travel times, speeds, acceleration, curved trajectories and approach angles into the core. Moving the donor does not drag previously emitted particles.
- Kept visual particle counts bounded and separate from collectible Magica or resource spending.
- Investigated the reported apparent crash. The available run exited normally during the harness's scheduled shutdown; its logs did not show a crash in that test window.
- Integrated the public JEI API for six-part Automaton assembly and 50-Magica world infusion, including pedestal catalysts and a Recipes link in the assembly screen.
- Made assembly recipe display follow synchronized server recipe data. JEI remains optional, with no automatic ingredient-transfer feature.
- Verified actual JEI output lookups and rendered pages, moving-donor particles and completed infusion. All 46 server tests still passed without JEI on the server.
- Installed and archived 0.2.1 with its source, editable assets, logs and captures.

## 3. HUD, crafting, forestry and balance revisions — version 0.2.2

### HUD, art and recipes

- Made the Magica bar taller and XP-like, using purple/white, below a raised XP bar and above the hotbar. Health/food reserve the extra space.
- Removed the drop shadow from JEI's “50 Magica” text.
- Redrew the 16×16 wand with a bright Magica-core tip and the pedestal with a quieter symmetrical pattern.
- Lowered the pedestal preview and reduced its opacity. Existing stock display-height settings migrate from 1.75 to 1.4 with a backup; custom settings stay intact.
- Kept block/item textures at 16×16. The 1.5-block creature retains its 24-unit rig and 64×64 atlas at the same pixel density, with ordinary plank rows on its torso.
- Changed the arm to a vertical Log / Iron Ingot recipe, supporting all six placements.
- Changed the leg to vertical Log / Planks / Iron Ingot, supporting all three columns.
- Changed the head to: Log–Planks–Log / Iron Nugget–Redstone–Iron Nugget / Log–Iron Ingot–Log.
- Renamed Automaton Core to Automaton Torso, retaining its internal ID for saved-item compatibility. Its recipe is Log–Iron Ingot–Log / Full Magica Core–Redstone–Full Magica Core / Log–Iron Ingot–Log.

### Lumbertons and player felling

- Added continuous chopping swings, target-facing head/body motion, corrected axe orientation and progressive cracks on standing and fallen logs.
- Added movement away from falling timber and leaf clearing when leaves block access to a trunk.
- Confirmed Stone axes work and Diamond/Netherite axes work faster, including complete harvesting and delivery tests.
- Increased the requested timing table: Stone/Iron gained one second; Diamond/Netherite gained half a second. Work durations align with the five-tick worker clock.
- Added end-to-end uphill/downhill tests across three one-block terraces, including delivery back at the storage elevation.
- Fixed navigation around thin Totem bases and widened escape destinations to account for pathfinding's stopping tolerance.
- Allowed Lumbertons to harvest loose, stripped and player-placed logs, including construction logs, inside the Totem area. Grass and flowers remain passable; solid walls still require a route.
- Added stone-or-better Survival player felling after a completed natural-tree base break. Wood/gold and Creative do not trigger this whole-tree behavior. The base drops once and costs its normal durability.
- Added collection of visible, reachable dropped log items resting on the ground inside the Totem range, using normal cargo and storage delivery. Ownership, pickup delay, height, range and capacity checks remain active.

### Range display and timber placement

- Added **Show Work Area / Hide Work Area** to the Totem.
- Green indicates logging coverage; blue indicates storage coverage. Markers are seamless, solid-colored translucent surfaces without textures, one block high, only in air above terrain.
- The display creates no physical blocks or collision, follows upgrades/terrain, remains visible after closing the screen and clears on logout/removal.
- Replaced the old overlapping stacked fallback with unique landing coordinates and reservations across timber jobs; no-space trees wait safely.
- Added migration of saved stacked jobs without losing their remaining wood.
- Initially made logs switch horizontal halfway through falling. The later 0.2.3 change below supersedes that separate-log motion with a continuously rotating connected tree.

### Two Magica sizes

- Kept the existing large-orb reward values and added a visually smaller orb worth exactly one-third of the corresponding large value.
- Flowers/grass produce small orbs. Mature crops have a 10% chance of a large orb instead.
- Logs produce two small orbs, plus a 40% chance of one large orb.
- Kept exact fractional accounting through pickups, spending and saves; old saved orbs retain their large value and different sizes cannot merge incorrectly.
- Initially displayed thirds beside the bar. Your later request removes those fraction characters without deleting any earned Magica.

### Validation and release

- Expanded the suite from 46 to 62 passing tests, covering translated crafting recipes, axe tiers, terrain traversal, leaves, loose logs, range/protection, distinct landings, old timber migration, exact Magica thirds and dropped-log delivery.
- Validated 91 resource JSON files, 33 texture dimensions and model/UV density.
- Inspected actual packaged-game captures for HUD, recipes/JEI, textures, effects, range display, axe swings, cracks and felling. The final isolated client exited normally.
- Installed 0.2.2 in Rackworks Tester, preserved the prior JAR and changed config, and archived source/assets/evidence. Source commit: `17ae391473f53fd2a809b69334fce749a67f7d37`.

## 4. Latest follow-up — version 0.2.3

- Removed fraction characters from the Magica HUD and pedestal balance readout. For example, an internally stored 9⅓ shows **9**; the remainder still contributes to the bar and future pickups.
- Changed newly player-felled trees to become real log blocks when they land, preserving wood type and final orientation. Players can break these with normal block mining. The base is still paid only once, and foliage loot is released once at landing.
- Added saved player-fall state and checks that wait for clear, permitted, unoccupied landing cells. Lumbertons can harvest the resulting ordinary logs afterward.
- Replaced scattered-log fallback and independent per-log flight with a rigid rotation of the original trunk and branches. The whole shape keeps its spacing and length, rotates continuously into its final orientation, and can rest across terrain or obstacles.
- Preserved distinct target cells and obstruction checks. Four directions are tried at the lowest safe common resting height; a tree waits if a connected fall cannot fit. This is not a bending/fracture simulation.
- Fixed saved timber reverting to a one-block collision box after loading. Navigation and landing reservations now account for the whole tree, including tips far from its root.
- Added alternate-side path searches, temporary avoidance of stalled route nodes, and release/retry of blocked timber claims. A blocked job no longer prevents a worker taking other reachable work.
- Workers with collected cargo now unload while a long area rescan is still running, instead of standing idle until discovery finishes. The packaged client exposed this additional wait.
- Expanded crown discovery to include naturally supported outer leaves, fixing connected falls for broad spruce, jungle and cherry canopies while retaining shared/placed-foliage safeguards.
- Added this consolidated history document, expanded the server suite to **67 passing tests**, and completed the release build and asset checks.
- Verified the packaged client: whole-number HUD, both JEI categories, infusion, connected rotation, four real landed logs, and exact ten-log delivery from the two test trees with no cargo left. The client exited normally.
- Installed **0.2.3** in Rackworks Tester, backed up 0.2.2, and archived matching source, assets, captures and verification evidence. See `VERIFICATION.md` for the JAR hash and backup location.

## 5. Safe-landing stall fix — version 0.2.4

- Inspected a read-only snapshot of the reported setup: both workers had new pending trees, zero chopping progress, and no remaining Timber entities. This ruled out a leftover falling-tree entity in that snapshot.
- Reproduced fall rejection caused by grass/flowers and shared natural leaves. The planner now permits those plants to be cleared along the fall corridor, while retaining neighboring logs, placed leaves and protection checks.
- Preserve plant loot once, including two-block flowers, and exclude already removed canopy from the trunk's collision sweep.
- Added a shared retry delay for genuinely impossible falls so two workers do not repeatedly take the same blocked tree.
- Added a regression fixture containing only natural block states from the reported forest layout, with no player or entity data. Test workers restore the reported positions and pending roots; both finish their trees with all six oak and seven birch logs accounted for.
- All **73 required server tests passed**. Built and installed **0.2.4**, preserved the previous JAR, and archived the matching source and evidence. No saved-world entity or block data was edited.

## Current default chopping times

At 20 TPS, without enchantments/upgrades; excludes travel and the 1.5-second fall. Loose logs use the processing column directly.

| Axe | Preparation per base log | Fallen/loose log processing |
| --- | ---: | ---: |
| Wood | 12 s | 6 s |
| Stone | 7 s | 4 s |
| Iron | 5 s | 3 s |
| Diamond | 3.5 s | 2 s |
| Netherite | 3.25 s | 1.75 s |
| Gold | 2 s | 1 s |

## Files, backups and scope of evidence

Development stays in `F:\Development\Codex\Automatons`; assets, documentation, versioned releases, source archives and screenshots are organized in `F:\Minecraft\Automatons`. Rackworks Tester is the designated installation target. Previous release JARs are backed up before replacement, and hashes are compared after copying.

Server tests and opt-in Lab client scenarios provide the recorded evidence. No broad remote-multiplayer certification, every-seed terrain sweep or arbitrary third-party claim-mod certification was performed. Historical logs and captures remain in the versioned verification folders. See `VERIFICATION.md` and `LIMITS.md` for precise checks and boundaries.
