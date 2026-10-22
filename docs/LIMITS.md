# Concrete limits and extension points

This is a first playable version, with deliberately conservative forestry.

- Definitions are supplied for oak, birch, spruce, jungle, acacia, dark oak, cherry, and mangrove. **Mangrove root formations are explicitly unsupported** in this version and are reported for manual harvesting. The other seven families have enabled definitions, including 2 × 2 spruce/jungle/dark-oak trunks and bounded branches. Automated end-to-end coverage currently uses ordinary oak; the other enabled species need broader natural-world testing.
- Trees over 128 recognized logs, 31 blocks above the root, or seven blocks of branch spread are rejected. Connected independent trunks, ambiguous forms, blocked landing space, and unsafe range/protection boundaries are skipped. The job transfers at most 384 exclusive crown leaves. Nearby leaves that might be supported by another tree are preserved.
- Direct player log placement is tracked going forward. Geometry cannot perfectly distinguish old untracked construction or arbitrary modded structures from a tree. This is not a general-purpose claim system.
- Claim integrations use Forge's standard break event with an owner-identified fake player for destructive checks. Specific third-party claim mods are not certified. No external claim mod was installed in the provided test instance.
- Workers do not force-load chunks, teleport around obstacles, break barriers, or work in unloaded dependencies. A remote home that is not loaded pauses the job. Some long or obstructed routes need player intervention.
- Normal saves retain the timber section ledger, staged loot, inventories, bindings, recall, and protection. Source transfer occurs in one server tick. This does **not** promise transactional recovery from an operating-system crash between separate world-file writes.
- Full storage and cargo, no tools, and no wood recover automatically. Inaccessible lids or paths are reported separately. A tree reserved by another worker is a waiting condition.
- Existing unfinished timber follows its original worker across reassignment only when the new range permits processing. Otherwise it remains available for player recovery or a later range/assignment change. There is no separate GUI to hand orphaned timber to a different worker.
- Vanilla single/double chests are supported. Other storage blocks must opt in through the `automatons:storage_chests` block tag and expose a Forge item handler. Arbitrary machine inventories are excluded.
- Compatible axes extend `AxeItem` or opt into `automatons:axes`. No inventory automation capability is exposed on the worker. Its menu, ground pickup, chest extraction, and held-item assignment use the same axe filter.
- Species are data-driven JSON files at `data/<namespace>/automatons_species/<name>.json`. Declare exact log and leaf block IDs, bounded height/spread/log counts, large-trunk support, and whether harvesting is enabled. Nether fungi and mushrooms are not inferred from broad tags.

Interactive hand-operated wand scrolling/cancellation, every expression transition, multiplayer latency, all neighboring-canopy shapes, and exhaustive modded storage/claim combinations remain manual acceptance checks. See `VERIFICATION.md` for what was actually executed.
