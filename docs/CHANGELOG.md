# Changelog

## 0.9.1 - Mine access, portable Machines and fluid handling

- Miningtons open the first shaft block and build twenty ladder rungs through the surface, without requiring an existing output chest.
- Ladder travel requires a physical approach, shares its passage lease during recall, and stops at the exit instead of inheriting vanilla climbing acceleration into the roof.
- Existing mines repair their omitted top rung with a real ladder. Distant Lumbertons can follow partial routes home.
- Tunnel is the default; quarry controls are temporarily hidden. Added optional excavation support for Blocks You Need: Luna natural stones.
- Machine and component models omit hidden and coplanar face sections; native 16×16 texture mapping is retained.


- Full/partial Luminall bottles and carried Tanks refuel the Machine directly on right-click, preserving overflow and fractional energy. Drinking partial bottles gives proportional energy and returns Glass Bottles in survival and creative.
- Shift-right-click with a Wand packs the complete Machine, retaining cargo, equipment, Program and fuel; placing it reforms a parked Machine. Relocation clears the old physical route.
- Engine R is edge-triggered and startup displays "Starting... Please wait" until ready.
- Add a craftable Lumina Drain atop Tanks. Standing on it transfers personal Lumina to stored Luminall without rounding loss.
- Tank faces use the complete native glass texture. Wand packing preserves fluid; ordinary breaking spills it into a temporary, uncollectable puddle that evaporates in three seconds.

## 0.9.0 - The Machine, Operatorton and brighter Lumite

- Form the exact 24-block assembly in any cardinal orientation; compact to a three-block chassis while retaining Tank contents and 108 cargo slots.
- Add iron-framed native Minecraft models, animated tracks, a physical Blade or nine-drill Bore, a recessed seat, engine transitions and driver-only controls/HUD.
- Share one server controller across manual driving, bounded single-chunk surface clearing and editable Program cards. Preserve exact progress, waits and recorded return routes through pauses and saves.
- Add survival Luminall refueling, container returns, full-footprint 48-block range, full-width one-block steps, protected harvest transactions, real cargo capacity and conserved dismantling drops/fuel.
- Add the Operatorton profession with exclusive network assignments, physical boarding and automatic unload/return trips. Unattended cargo requests use Runnington deliveries.
- Add Controls, numbered/scrolling Program editor and 108-slot Storage tabs, plus external handbook pages and all component recipes.
- Brighten 50 dark purple interior pixels in each 16×16 Lumite ore texture. Existing stone/deepslate backgrounds, chipped rims, transparency, shard sprites and block geometry remain unchanged.

## 0.8.3 - Real fallen logs, forestry help and stone Collector

- Recreate the supplied Collector reference as a stone pedestal with an open basin, raised bottle stand, visible lavender fluid and glass bottle with a wooden stopper. Collision follows the basin and pillar. No copper; recipes and capacities remain unchanged.
- All completed falls place ordinary sideways log blocks. Lumbertons retain the exact job positions and chop those real blocks; the hidden job record preserves partial cargo across reloads. Manual harvesting and player replacements retire the old work without duplicate drops.
- Replace multipart log collision with vanilla block collision and targeting. Preserve full animation bounds through position updates. Check loaded timber jobs and reconcile client animations against server identities every five seconds, in bounded batches.
- Check fall plans before travelling to a tree. When needed, clear permitted obstructions with chopping animation and recovered drops. Choose a limited clearing plan; respect protected worksite structures, storage, player structural logs, fluids and unbreakable blocks.
- Lumbertons never break pickaxe-tagged blocks. They queue persisted obstacle requests on their own network, visible under Logistics / Requests, and try other trees. A capable, active Minington claims the work, collects the drops and returns to its saved mine workload. Dirt and planks replace cobblestone for new climbing supports.
- Keep failed trees deferred for 20 seconds and seek other work without unnecessary servicing trips.
- Deliver harvested cargo between trees after two stacks of output or a minute carrying a smaller load. Keep the existing replanting and climbing reserves.

## 0.8.2 - Nearby pickup and Builder shed waiting

- All professions and unassigned workers collect nearby visible item drops while working, waiting, idle or paused. Pickup does not start a new travel job; delays, ownership and inventory capacity remain respected.
- Appropriate tools fill free tool slots; extra or unrelated tools can be carried as cargo. Partial pickups preserve the remaining stack and item metadata. Farmers share the pickup ownership checks; Miningtons use the common pickup loop.
- Idle Builders return inside their completed shed and stay at a stable, supported spot until a construction request arrives. Incomplete sheds retain the main-Pedestal waiting fallback, and Recall still sends Builders to the Pedestal.

## 0.8.1 - Collector and Tank materials

- Replace all Collector metal trim with vanilla stone and all Tank metal framing with vanilla glass, including break particles and inherited item models. Recipes and gameplay are unchanged.

## 0.8.0 - Luminall, Collector and Tank

- Raise Hammer durability to 2,000; apply door-gap alignment to Builders and other professions as well as Runningtons.
- Add Main Pedestal Build Lists for active network projects, with status, location and scrollable material availability/shortages.

- Add registered Luminall source/flowing fluid and bucket, passive Lumina Collectors, craftable fluid Tanks, visible partially fillable bottles and full-bottle drinking through the existing Lumina bank.
- Reuse producer tags/tree recognition; include planted gardens and mature crops. Scan a 4×4 footprint, share producer output between overlapping Collectors, respect rate/capacity limits and loaded chunks. Show the exact boundary with the Wand.
- Expose standard Forge fluid capabilities. Retain fluid/bottles in saved block entities and dropped machine items. Refuse using a filled Tank in the Collector recipe.
- Add the exact requested recipes and editable handbook chapter. See LUMINALL.md for all configurable defaults and balance.
- Give Deepslate Lumite its own −64..0 range with triangular peak −32 and a separate JER estimate. Center cluster sampling on block centers.
- Stop automatic completed-shard offhand retrieval and raise Infuser shards by three pixels.
- Let Builders begin with partial materials and replenish later, retaining their project claim and temporary construction chest.

## 0.7.5 - Editable handbook resource pack

- Move all handbook Markdown, screenshots and scene structures into the unpacked `Automatons-Guide` resource pack. Guide content is excluded from the mod JAR.
- Edit pages directly and reload resources with F3+T. The old authoring script now exports only scene structures; it cannot overwrite page text.
- Install and enable the pack alongside the mod. Future installations preserve customized pages/assets, extra files and deliberate deletions while updating untouched defaults. Back up changed defaults and Minecraft options.
- Keep all gameplay, recipes, models and game textures unchanged from 0.7.4.

## 0.7.4 - Construction staging, movement and infuser surfaces

- Halve construction speed to one action every two ticks. Use the exact supplied small coal and charcoal piece sprites.
- Stage the complete building kit in a paid temporary chest outside the footprint. Runningtons can deliver materials there. Builders refill locally, then reclaim the chest and return leftovers to their home storage after construction. Missing stock explicitly reports "Waiting for materials".
- Buildingtons walk clockwise between supported positions outside the structure after small batches of work. Clear pending movement when stopping and reserve their construction look direction from idle looking.
- Leave extra workstations and containers in blueprint air cells intact. Automatically break obstructing structural blocks while preserving container contents. Protected and unbreakable blocks remain blocked with explicit status.
- Send workers awaiting construction to the main Pedestal. Breaking a Totem releases its construction reservation, cancels deliveries safely and preserves its supply chest contents.
- Remove overlapping infuser socket top faces and match collision to the visible lower trim. Preserve supplied shard PNGs and ore textures/models.

## 0.7.3 - Supplied shard artwork

- Use the supplied raw and infused shard PNGs byte-for-byte at their native 16x16 size.
- Preserve those files through asset generation. Ore artwork, block models and gameplay are unchanged.

## 0.7.2 - Lighter Lumite and raw shards

- Slightly brighten the visible Lumite while preserving the stone, painted rim, shadows, and existing glow mask.
- Give raw shards very pale purple interiors, preserving every edge pixel and the original silhouette. Infused shards and block geometry are unchanged.

## 0.7.1 — Crumbled Lumite textures

- Paint a chipped stone rim and shaded crystal interior into two opaque 16x16 textures. Preserve exact supplied vanilla stone/deepslate background pixels outside the artwork.
- Retain the original cube geometry and align its existing flat emissive mask with the new Lumite pixels. Keep zero world-light emission and all gameplay behavior from 0.7.0.
- Preserve original supplied ore images in the reference archive. Asset generation validates the new authoritative files. Show previews before launching Minecraft for future texture reviews.

## 0.7.0 — Handbook, fuel pieces and perimeter construction

- Buildingtons choose a reachable supported position outside their footprint, face their targets and swing while placing every level. Empty foundation cells no longer require a path into the hole. Correct foundation blocks are no longer removed to create a temporary access route. Construction proceeds from the foundation upward, retaining material costs, tool wear, permissions and placement sounds.
- Add Piece of Coal and Piece of Charcoal with four reversible shapeless 8:1 conversions and one-eighth of the parent's fuel time. Pieces cannot substitute for whole coal in unrelated recipes.
- Remove every Automatons command and all retrogen execution. Preserve saved history and existing ore without restarting pending jobs or acquiring loading tickets.
- Allow felling from the rooted trunk's lowest three log levels, including the lower stump once. Players and workers in the fall path do not veto initiation, falling or landing; block/protection and ownership rules remain.
- Increase new-chunk Lumite's configured size from 6 to 8, retaining eight attempts, Y=-32..64 and the triangular distribution. Use the supplied stone and deepslate texture files byte-for-byte; resource generation preserves all current PNGs.
- Add optional native JER 1.4.0.247 information for both ore variants, using the same default generation definition and explicitly labeling the candidate-placement estimate.
- Add the survival-craftable Automaton Handbook, 29 pages, live recipes, seven actual structure scenes, screenshots and item navigation. GuideME 20.1.15 is required on both sides; JEI/JER remain optional. Collector mechanics remain a design checkpoint.

## 0.6.0 — Lumina and Lumite


- Rename visible energy to Lumina while preserving exact saved points/thirds, reward rules, independent XP behavior and existing core identities.

- Add stone/deepslate Lumite Ore, raw and infused shards, stone-tier harvesting, Fortune/Silk Touch loot and emissive veins with zero world light.

- Replace core infusion with four physical shard corners. Each shard costs 25 Lumina; all corners share the old transfer rate. Preserve owner-specific payments and item-bound partial progress.

- Craft Lumina Cores from four infused shards around an Empty Core. Empty Cores use raw shards; the infuser uses a diamond. Remove amethyst from active recipes.

- Retire all ten upgrades and the unused Upgrade Plate. Remove acquisition, slots and effects; preserve inert old items and return installed contents once.

- Preserve legacy partial-core credit with donor-aware recovery and an explicit operator path for unknown ownership.

- Add the temporary operator/console one-world-one-pass retrogen job, persistent scope/receipts, pause/resume, exclusions, and loading of saved chunks without promoting neighboring terrain. It never runs automatically.

- Redraw the native Hammer and give it slow positive player mining with wooden-pickaxe harvest eligibility; retain 250 durability and builder timing.





## 0.5.0 — The Network and purpose-specific worksites



- One Totem surface radius covers work and nearby chests. No inner exclusion or separate storage overlay. Defaults: Lumber/Farm 32, Mine 6, Builder/Warehouse 8.

- Add Buildingtons, Hammers, Farmingtons and shared field boards; retain existing fast construction cadence and Magica balance.

- Import all five supplied buildings with real identification frames, exact placed-item lists, empty containers, fixed live Totem anchors and four-direction rotation. Remove the mine hatch and move Builder's Shed chests within four blocks of the anchor.

- Persist exclusive construction claims through restocking and unloading. Bootstrap the first Builder's Shed without a courier. Miners begin shared underground infrastructure after surface handover.

- Adopt existing farmland or reserve one reachable 9×9 field. Use real water buckets, hoes, planting stock and optional bone meal. Preserve one full replant reserve across local stocks and courier reservations.

- Add purpose/facing setup, farm controls, build progress and Network summaries using the existing menus. Add builder/farmer clothing and action-held farm supplies.

- Remove owned felled-tree leaves immediately with normal decay-style drops; preserve placed and shared foliage and avoid axe enchantments or extra Magica.

- Target new worlds for this milestone; retain normal project, field, worker and parcel persistence.



## 0.4.2 — independent mining and forestry access



- Remove the pedestal Recipes link; Unassigned workers apply their first profession without a confirmation. Existing job changes still warn about interrupting/resetting work.



- Minington helmet grows one model pixel wider and taller; body collision dimensions stay unchanged.



- Logistics lists worker activity and pickup/supply requests. Registered mine depots remain pickup points when their Totem also hosts a warehouse.



- Runningtons never withdraw tools, torches or saplings from chests. Old parcels already in cargo can finish safely. Builders leave reserved supplies in staging chests and request that they be moved before chest removal; construction torches must be carried.



- Lumbertons fetch saplings from storage and retain a planting reserve. Forestry's default vertical range grows to ±32. High wood can be reached with inventory-funded dirt/cobblestone/plank pillars, recovered after work and journaled across reloads.



- Keep unrelated tree discoveries on terrain changes and skip heightmap-guaranteed empty space within bounded scan work.



- Long return trips time out only when movement stops, so unloading does not strand miners halfway back to distant work.

- Each tunnel miner has its own saved branch and resumes it after unloading or pausing. New branches connect through the platform-side access passage; existing excavated blocks remain in place.



- Quarry work lights persistent stairs, avoids remote lighting detours and unnecessary single-miner yielding; normal player/tool block-breaking times remain unchanged.



## 0.4.1 — construction and continuous work



- New 7×6×8 warehouse schematic: 81 planks, 38 logs, 56 slabs, 26 cobblestone, 12 glass, 16 chests, 4 torches and one door. Existing approved buildings keep their original blueprint; untouched plans use the new one.



- Faster construction decisions, player-style placement sounds, yellow site outlines, live scrollable build lists, soft terrain clearing, and safe evacuation/removal of recognized supply chests in the footprint. Verification follows blueprint cells; surrounding block changes do not halt building.



- Mine entrances have open access without trapdoors. Existing hatches are recovered as item drops. Saved completion is shared by miners, and working miners take turns at the active face.



- Mining resumes after unloading at the existing work position; pauses hold position. Supply checks precede trips, and stale deferred-path state no longer stalls restocking.



- New mines default to quarries beginning at the platform's outer edge. Existing mine modes and excavated routes remain saved.



- Profession dropdown with Apply Job and explicit interruption confirmation; no Rebind button. Totem tabs and a larger profession-settings popup replace chest controls and cramped work panels. All Totem chests supply and store items.



- Pedestal preview uses the current entity texture. Its screen has a lower Magica section and only worker names/work-enabled status below it.



- Lumbertons replant carried saplings at cleared stumps after felling; dark oak requires four saplings and a suitable 2×2 patch. Planting consumes real items and respects placement protection.



## 0.4.0 — Runningtons and shared logistics



Added the Runnington job, one persistent doubled cargo inventory, physical courier requests/reservations, underground collection, shared schematic warehouse construction and recoverable full-storage handling. Imported the supplied closed-roof minehead for new mines; preserved existing entrances and depths. Added mouthless widened faces, visible job gear, dynamic packed-item rendering and Logistics menus. See RUNNINGTON.md for the exact materials and controls.



Courier routing now handles partial-path progress and full-body doorway clearance. Overlapping Totem ranges share canonical chest reports and reservations; logistics scans have a separate bounded budget. Verified with 113 server GameTests and the packaged courier scenario.



## 0.3.1



- New mines descend 20 blocks. Existing excavated mines preserve their original depth and progress.



- Require four real chest items before new mine construction and install two ordinary double chests against the underground platform walls. Miners unload there, try the other pair when one is full, and retain surface supply trips.



- Mine falling material from safe footing at least two horizontal blocks away, wait for the column to settle, and conserve normal block loot and tool wear.



- Preserve water/lava boundaries, return to the underground platform, and choose an unused forward/side direction. Save blocked routes and keep the original shaft, depot and passages.



- Match normal grounded-player tool break duration without upgrades, using one-tick work updates. Stone now takes 12/8/6 ticks with unenchanted stone/iron/diamond picks. Shared Speed/Efficiency upgrades still apply.



- Show platform depth, underground storage and route direction in the Mine panel. Client/server protocol 4 requires matching release versions.



## 0.3.0



- Added the Minington job, real pickaxe/shovel work, one persistent shared mine per Totem, supplied arch/ladder/chamber construction, serpentine Tunnel mode and a winding-stair Quarry mode.



- Added actual torch supplies, server block-light checks, bounded stock, shortage recovery, missing-light repair and replacement-before-recovery of mine torches that obstruct excavation. Mining emits normal ore XP and no Magica.



- Added independent mining range upgrades, gray beveled menus, Mine/Forestry panels, declared-geometry amber outlines and a 30-second Find glow on each Totem worker detail page.



- Fixed client collision registration for Lumberton-felled logs, with one solid collision part per landed log. Smoothed synchronized falling progress between client ticks.



- Enlarged the wooden head to 8×8×8 model units, with violet eyes and visible expressive mouths guided by the supplied image. Total standing height is now 1.625 blocks.



- Added the complete 640-name catalog, permanent server-wide name reservations, Unicode canonicalization, deterministic legacy conflict migration and compound/numeric exhaustion fallbacks. Successful assembly allocates once; previews do not allocate.



- Names persist through packing, deployment, jobs and links. Name tags use the same uniqueness registry. Totem rows, worker details, item names and selection messages use personal names while internal actions keep UUIDs.



- Added the per-home **Show Automaton Names: ON/OFF** toggle, default ON. It controls the normal overhead label, caches the setting for unloaded homes, and respects the saved pedestal identity across dimensions and rebinds.



## 0.2.4



- Fixed fall plans rejecting grass, flowers, crops and shared natural leaves as solid obstructions. Only foliage intersecting the fall corridor is trimmed beyond the tree's own crown; neighboring trunks, placed leaves and protected blocks remain intact.



- Preserve cleared foliage drops in the existing timber ledger, removing both halves of tall plants and paying their item only once. Already removed canopy no longer enlarges the moving trunk's obstruction area.



- A tree that still cannot fit is deferred after three seconds, with a shared twenty-second retry delay so other Lumbertons can take reachable work. Revalidation and commit failures use the same recovery path.



- Added regression coverage for the reported two-worker forest layout, ground cover, shared leaves, protected foliage and impossible-fall recovery.



## 0.2.3



- Magica HUD and pedestal readouts show whole points only, retaining exact fractional accounting.



- Player-felled trees become real log blocks on landing, with normal block breaking, preserved wood type/orientation, and one-time foliage loot. Obstruction and reload checks preserve the pending tree.



- Replaced scattered-log fallback and separate log motion with connected trunk/branch rotation. Falls preserve shape and can rest across uneven terrain or blocks, with distinct occupied coordinates.



- Lumbertons plan around the full fallen tree, search multiple approach sides together, and replan after a movement stall. Blocked timber claims are released temporarily so reachable work can continue.



- Workers unload collected cargo while area discovery is incomplete, avoiding idle waits during a long rescan.



- Restored full timber bounds after save/load and included distant tree tips in collision/reservation searches. Expanded natural canopy recognition to the full six-leaf support distance.



- Added a complete history since the full redesign prompt in `HISTORY-SINCE-REDESIGN.md`.



## 0.2.2



- Lumbertons seek reachable dropped log items on the ground within their Totem’s range and collect them into cargo for storage.



- Added half-size small Magica orbs worth exactly one-third of the unchanged large value, persistent fractional balances, 10% large crop chance, and two small log orbs plus a 40% large chance. Flowers/grass yield small orbs.



- Falling logs turn horizontal halfway through the animation.



- Removed overlapping stacked timber fallback: every log reserves a distinct landing cell. Unsafe/no-space trees wait; older stacked jobs migrate with their remaining wood intact.



- Taller purple/white Magica bar below a raised XP bar; health/food also reserve space. JEI infusion cost text has no shadow.



- New translated arm/leg and full 3×3 head/torso recipes. Automaton Core display name becomes Automaton Torso; the old registry ID remains for saved items.



- Refreshed symmetrical pedestal and Magica-tipped wand textures; lowered, more transparent pedestal preview.



- Continuous chopping swings, corrected hand transform, target gaze and standing/fallen-log damage cracks. Workers navigate away from fallen timber and clear obstructing leaves.



- Stone-or-better player felling after a completed Survival base-log break; conserved remaining-log ledger.



- Lumbertons now harvest loose, stripped and placed logs as well as natural trees. Grass and flowers remain passable. Totem collision is explicitly blocked in navigation so workers route around its thin base.



- Revised tool times and five-tick rounding keep Diamond and Netherite measurably faster. Show Work Area toggles one-block-high green/blue air-cell range markers following terrain.



## 0.2.1 — 22 October 2026



- Infusion emits overlapping bursts of 2–4 differently sized particles from varied upper-chest positions. Each follows its own curved path, travel speed and approach angle into the core.



- Added optional JEI API integration for server-data-driven six-part Automaton assembly and 50-Magica core infusion, with pedestal catalysts and a Recipes link in the assembly screen.



- Added opt-in side-camera particle captures and actual JEI recipe lookup/display checks to the Lab scenario.



## 0.2.0 — 22 October 2026



- Added independent Magica collection, HUD, source eligibility and placement provenance; world infusion spends exactly 50 points per full core.



- Added the complete survival recipe chain, six-component atomic output assembly, permanent item hologram, 16-pixel component art and a half-height black/white infuser with animated donor transfer.



- Added generic Unassigned/Lumberton jobs, placement pause, generation-checked portable identities, health-preserving Wrench packing and authoritative saved-link reconciliation.



- Replaced the single-worker totem with shared paginated rosters, independent homes/alerts and shared upgrades/storage. Preserved existing worker inventories, IDs and bindings.



- Added nearest-to-totem fair dispatch, adjacent collision-aware routes, transient path deferral, all eight normal Overworld tree families, natural attachment cleanup and compact grounded felling fallback.



- Kept cargo between trees while the next output fits. Required totem visits unload all accepted cargo across alternatives and restock empty axe slots; blockers recover independently.



- Added persistent timber processing progress, released-job recovery, positional work impacts and the corrected 20%/40% speed formula.



- Expanded server regression coverage to 46 tests, including generated-tree approach/felling, exact recipes, independent Magica, infusion transactions, assembly, shared dispatch, packing, migration, multi-chest recovery and waterlogged mangrove preservation.



## 0.1.1 - 22 October 2026



- Fixed creative-mode wand selection being lost between clicking a Lumberton and clicking its totem. Minecraft supplies a disposable item-stack copy to the entity callback in creative mode; the mod now saves the selection on the actual wand in the interacting hand.



- The same correction allows creative spawn-egg workers to be claimed and bound to a pedestal.



- Added game tests that exercise `Player.interactOn` followed by `ServerPlayerGameMode.useItemOn`, covering creative and survival assignment in both hands and creative worker claim/home/assignment. The creative tests reproduced the lost selection before the fix.



- Existing workers, wands, inventories, and worlds remain compatible. Restart Minecraft after installing the update, then select the worker again.



## 0.1.0 - 22 October 2026



First playable Lumberton with custom model and animation, pedestal creation, totem forestry, persistent inventories and bindings, tree protection, and upgrades.

