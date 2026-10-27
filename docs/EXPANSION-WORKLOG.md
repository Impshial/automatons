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
