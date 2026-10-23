# Changelog

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
