# Changelog

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
