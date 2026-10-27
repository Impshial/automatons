# Full expansion implementation

Authoritative request: `reference/schematics-2026-10-27/Automatons-Schematics/Automatons-Full-Implementation-Prompt.txt`, with supplied geometry and integration notes. Prior transcript is historical context. Existing 0.9.1 source/assets/tools/docs are preserved in `build/expansion-baseline-0.9.1.zip` before edits. No AGENTS.md was found in the repository or its ancestors.

This is an active implementation record, not a completion report.

Build workflow: reuse `tools/test_release.ps1 -Expansion` and the repository Gradle wrapper. The exact test command with output in `build/expansion-tests.log` has a saved approval. Do not change shell launchers or invent new build wrappers. The managed sandbox requires escalated access to the existing user Gradle cache; use the saved command, without another conversational permission request. Installed `Rackworks Tester/mods/automatons-0.9.1.jar` was verified unchanged, last modified October 25 at 23:22.

User clarifications: reuse the existing six professions and add only the seven missing jobs. Dead Plant and Dead Crop disappear on breaking with no drops, superseding the handoff's dead-item drop rule. GitHub pushes are authorized; use standalone Git commands to match saved approvals. The exact pre-expansion 0.9.1 baseline was committed and pushed to `https://github.com/Impshial/automatons` master as `a70c39c1e4b1ae6cf3eeb20c65e47cdbc86ffb1d`; expansion work is on `codex/full-expansion`.

The user chose to continue in this task after verifying the changes, rather than reverting or returning to the old task. The original task, **Draft Minington automaton prompt**, ID `01a1660b-d50d-7b93-aaf2-725339a2a66f`, was read through the task tools. It used the SAME bundled PowerShell runtime and repository scripts. The earlier explanation attributing the approvals to a different runtime was incorrect; current sandbox restrictions are observed, but the old task's effective permission policy was not established. Do not repeat that explanation. No app security settings were changed.

- [ ] Shared professions, six-worker assignment, body compatibility and equipment preservation.
- [ ] Operating charge, service reserve, exact fluid conversion, depletion and dead vegetation.
- [ ] Threat behavior, safe retreat, grave recovery and armored assembly.
- [ ] Import all ten supplied layouts, metadata, rotations and exact material accounting.
- [ ] Fixed strip mining, independent quarrying and three farming strips.
- [ ] Hunterton, Crafterton, Fisherton, Rancherton, Luminariton and Guardington jobs.
- [ ] Shared opportunistic flowers and complete network supplies.
- [ ] Finished contextual totem and worker interfaces.
- [ ] Thirteen profession models, textures, powered-down and fishing presentation.
- [ ] Regression suite, focused behavior tests, asset/package validation, client review.
- [ ] Current documentation, packaged JAR and release evidence.

## Evidence

The ZIP was extracted and CODEX-START-HERE.txt read before other package documents. All ten manifests were inspected. Four supplied custom block IDs match current registrations: totem, tank, lumina_collector, infusing_pedestal. The baseline Tank already accepts arbitrary registered fluids, and the existing collector/source recognition and construction transactions will be extended.

## Foundation checkpoint scope

Implemented in this working branch: profession IDs and server assignment checks, distinct guard entity/body recipes, operating/service energy storage, fixed fluid ratio, quarter-rate collector production with persisted depletion, non-dropping dead vegetation, supplied layout import/rotation adapters, field strips and server-side ghost selections, water/glass supply actions, deferred ordinary-chest grave recovery, and initial exact-type hostile targeting/retreat/shared combat code.

These are foundations, not a finished expansion. Fixed mining/quarry replacement, fishing/crafting loops, full logistics supply routing, GUI replacement, all thirteen appearances, release documentation and complete regression/client validation remain unfinished. The new fields and ranch settings have server controls but the replacement UI has not yet been connected. Do not install or describe this branch as a release.

## Behavior checkpoint after verification

## Fishing, crafting and supplies checkpoint

Pushed as `80ddae4` on `codex/full-expansion`.

The focused suite now passes **23 required GameTests**, including a real vanilla fishing bite/loot/rod-durability cycle, all six exclusive dock slots and source-water validation, two concurrent couriers physically delivering a named sword and arrows without consuming their personal recharge bottles, transactional crafting/remainders, and a complete authored-workshop sequence from three raw iron plus one coal and two sticks to one pickaxe. The workshop test caught and fixed a fuel deposit/fetch loop. The hostile-acquisition test now permits vanilla's bounded randomized acquisition interval instead of assuming acquisition at exactly tick 30.

Fishing uses a dedicated hook with vanilla bite timing and loot context, an isolated fake fishing actor, stable dock poses and a cosmetic chair. Crafting searches bounded RecipeManager dependencies, uses actual table and furnace inventories, preserves intermediate material/fuel reserves, and simulates container remainders before any commit. Without a courier it can physically fetch and deliver; that complete network route still needs a dedicated integration test. With a courier it stays in its workshop. Cargo supplies and per-site reserve checks now cover the new jobs. Remaining multi-crafter contention, modded recipes, GUI, appearance, mining/quarry, full regressions and client review are still outstanding. This remains an implementation checkpoint, not an installable release.

## Interface work in progress

`ProfessionScreen` replaces the old worker/totem screen through the menu factory; the pedestal retains its existing screen. Workers have Status/Inventory tabs with a read-only profession, guard patrol controls and separate mage reserve. Totems have a chooser followed by Overview/Workers/Settings, crop ghost slots, ranch targets, crafting orders, naming and separate production/guard lists. Menu-scoped `WorkActions` validates ownership, menu identity, distance and current claims. Confirming a profession change preserves world blocks and pauses the replacement plan. A focused server test verifies those invariants, bringing the passing suite to **24 tests**. New ranch lease cleanup and its 25th test are not yet verified.

Client layout verification is still in progress. The first isolated review (`build/packaged-smoke/20261027-024636`) failed its harness assertion because the menu was not open before its first capture. The revised `ExpansionVisualReview` waits for world startup and separates fixture placement from menu opening. It uses the existing `--worksite-review` flag and runs in a disposable copy only. The exact command below now has saved approval; keep it unchanged for future retries:

```powershell
& 'C:\Users\impsh\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' tools/instance_smoke.py --isolated --worksite-review
```

Do not mark the GUI complete yet: quarry depth/acknowledgement and fixed mining status await their behavior implementation; full worker/guard roster interaction and client screenshots still require review. Existing older smoke classes referring directly to WorkScreen need reconciliation when running their old scenarios.

Guard protection now has a separate persisted roster, circuit/assigned-site patrols, recall perimeter, surface exclusions and equipment restocking. Hunting uses exclusive animal claims, real weapons/ammunition, ordinary drops, a fixed 96-block totem radius, and protection rechecks at both attack and damage time. Entity-persisted livestock protection remains after escape/reload. Shared flower pickup respects cargo capacity and persistent collector-bed geometry.

Luminariton service now includes separately funded healing with actual-health fixed-point costs, safe ground-clearing visits, real 250mB bottling, bucket transport to tanks, stock-funded shard infusion and suitable non-tree planting in registered beds. Ranching includes persisted targets/toggles and birth reservations, real shears/food/buckets/leads, Forge's registered milk fluid, a 24,000 elapsed-tick per-cow milk cooldown, pen registration and adult shed-handling logic. These new task loops still need end-to-end movement/client verification; compilation and transaction tests do not establish that every ladder, dock, gate, combat or delivery route works.

Current focused suite: **18 required GameTests passed**, with build successful. Added coverage includes production limits/body invariants, guard protection persistence, exact hostile targeting, protected animal eligibility/claims, flower capacity/bed protection, proportional healing and ownership, blocked/exact bottling, suitable/tall planting, shared breeding reservations and milk conservation/cooldown. Full legacy suite remains outstanding. Operating recharge now returns to the supply point before consuming a carried bottle.

Checks so far: compileJava passed; `validate_assets.py` passed 231 JSON assets and 50 textures; `validate_handbook.py` passed its existing 33-page checks; import validated every explicit block against all ten supplied manifests. Nine focused server GameTests passed, covering dead vegetation loot/clearing, persistent exhaustion/tree exemption, three-strip field persistence, whole-bottle energy and fractional drain interval, exact death inventory recovery and duplicate-callback prevention, blocked/lava grave recovery, tall source exhaustion, and all four collector-bed rotations. Full pre-existing regression suite and client review have NOT passed for the expansion yet.

Reuse the saved test command verbatim (normal installed Gradle cache access is required):

```powershell
.\tools\test_release.ps1 -Expansion *> build/expansion-tests.log; Get-Content build/expansion-tests.log -Tail 50; exit $LASTEXITCODE
```

Latest interface evidence: all 25 required expansion GameTests passed (03:01:34), including idempotent ranch unassignment returning exactly one lead. The second disposable client review, build/packaged-smoke/20261027-025206, completed with exit 0 and all 27 screenshots. Chooser, crop strips, crafting, ranch, inventory, guard and mage screens were visually inspected. Minor crafting spacing and unloaded-worker health-label corrections followed; production pause now also applies before the farmer dispatch. Roster interaction review and mining/quarry settings remain outstanding. No live installation was changed.

Fixed mining implementation (uncommitted): StripMine reads all twenty supplied branch definitions, persists each floor/corridor/branch and hazard reason, creates four double chests per 5x5 landing, extends access by four blocks and retains earlier depots. The suite reached 29 passing tests with real construction, full-cargo unloading, returning to the same branch, finishing its full 24-block length and constructing the second landing from eight more chests/four ladders. The integration test caught and fixed depot waypoints, old shaft-floor support exclusion, and the need to step back before extending the shaft. New separate QuarryPlan and Mining.fixedQuarry are now under test; 15x15 cell claims, dimension bottom, safe filler, cave acknowledgements, lighting and a shed-facing surface exit are implemented but not yet verified. Quarry UI depth controls were added after the prior client review. All of this remains an unfinished expansion, not a release.

## Mining, quarry and appearance checkpoint

At 04:46 on October 27, all **33 required expansion GameTests passed**. The quarry integration test now excavates two complete 15x15 layers with actual water/lava plugs, paid torches, relocation of lights before mining their supports, tool wear, a full-cargo surface unload and final shed-facing exit. The runtime fixes included safe ledge/footing choices, reclaiming only recorded safe filler, approaching the ladder on solid ground, and preserving an unfinished unload trip through storage shortages. The final storage failure was a fixture error: its pasted chests lacked completed-construction records; the fixture now supplies those records. Separate tests verify four rotations, partial progress, expiring worker claims and persistent cave acknowledgements with fresh alerts for new openings. No waiting state is treated as completed excavation.

Crafter final-output claims are now shared within the owned network, expire on unavailable workers, and count other crafters' actual completed cargo. A two-crafter test waits after fulfillment and verifies exactly one pickaxe plus untouched extra ingredients despite enough stock for a second tool. The single-crafter ore/fuel test remains.

All thirteen appearances are authored through the existing native cuboid/UV generator, with a 256x256 atlas at one texel per model unit, ordinary 26-unit bodies and a separate 40-unit armored body. Powered-down eyes/poses and the seated fishing chair are included. `validate_assets.py` passes 231 JSON assets, 51 textures and 126 non-overlapping cuboids. Asset publication uses atomic file replacement to avoid Windows memory-mapped preview/indexer failures.

Disposable client review `build/packaged-smoke/20261027-041437` completed all 35 interface captures, including roster pagination, naming, confirmation/cancel, and paused replacement construction. `20261027-043314` also completed with 37 captures including the appearance/state sheets. The review caught and fixed roster clipping and the chooser footer overlap. Goggles and farmer clothing were refined afterward; `20261027-044705` is reviewing the latest assets. Full regression checks, the mage's raised-workshop access route, remaining movement integration, current handbook/documentation and final packaging are still outstanding. This checkpoint is not ready for player testing or live installation.

## Integration and documentation checkpoint — 08:06
The pre-expansion GitHub baseline remains master at a70c39c. Expansion work stays on codex/full-expansion; Rackworks Tester still has 0.9.1 and no player worlds were modified.

The full 268-test run passed 260; all eight corrections subsequently passed in the 270-test run. That run passed 267, with failures in two forestry cases and the new physical ranch route. The forestry cases both pass in the expanded focused group. The latest focused run passed 42/43, including physical guard spacing/recall/restoration, all four mage tower rotations, linked-chest reassignment atomicity and remote crafter fetching/smelting/delivery with a conserved lava-bucket remainder.

Ranch integration exposed broad interaction reach, premature gate closure and diagonal lead pulling. Close interaction and gate staging are corrected; the final straight waypoint is still being verified. Tracking-unload cleanup now preserves a ranch handling leash/route and releases fishing slots without mutating the entity list during Minecraft chunk iteration. The next focused run shut down cleanly; the earlier diagnostic shutdown exception is retained in its log.

Version metadata is now 0.10.0. Current README, network/mining/courier/fluid/limits/art guides and forty-page handbook describe the expansion. Asset, machine/infuser geometry, handbook links/recipes, five handbook installer checks, ten native structure imports and packaged-resource comparison pass. A new disposable packaged client review is running in build/packaged-smoke/20261027-080335, including actual guide compilation and new pages. No completed installation or release is claimed yet.

## Final regression corrections — 10:36

The first 276-test run passed 274. Offline inspection of its saved test world confirmed seven logs in the netherite test's chest, rather than the five expected: the rotated-layout gallery extended beyond its 64-block test footprint and exposed neighboring wood. Rotated structures now reuse and clear one bounded footprint, and the axe fixture centers its entire work radius inside its own bounds. Its exact five-log assertion is retained.

The other failure exposed a real ranch bug: the dispatched animal's lead could bounce beyond passive pickup range. Ranchers now save a short collection trip and physically approach real lead/product entities; no new item is synthesized. A dedicated reload-and-collection regression supplements the complete lead/gate/dispatch test. Focused verification now contains 44 tests. The corresponding packaged client review is build/packaged-smoke/20261027-103656. Final counts and installation are pending.
