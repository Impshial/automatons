# 0.2 redesign work log

Source of requested design: `reference/redesign-specification.txt` (25 sections). The direct user request adopts the redesign; existing 1.20.1 / Forge 47.4.10 / Java 17 target is retained. No unrelated edits were present at start (base commit 963552c).

## Implementation sequence

1. Independent Magica ledger, vanilla-orb-equivalent entity, successful-break rewards/provenance, HUD, infusing pedestal and transfer effects.
2. Full survival recipes and art; six-part transactional assembly and permanent tilted item display.
3. Generic job selection, item deployment/packing, stable identity ledger and legacy migration.
4. Shared totem roster, ordered dispatch, storage recovery, tree recognition, safe segmented fall fallback and chopping audio.
5. GUI/art review, focused GameTests, dedicated-server and client smoke, documentation, release/install.

## Inspected evidence

- Existing tree recognition rejects every log/leaf outside the root search square, rejects mangrove explicitly, and requires dirt immediately under the log.
- Existing fall planning only accepts a complete long line and tests an inflated sweep against all nearby objects, with no compact fallback.
- Existing worker clears progress and backs off when landing validation fails, so arrival alone cannot guarantee chopping.
- Existing dispatch iterates scan insertion order and starts before the first complete sweep.
- Existing unloading condition explicitly routes partial cargo after each finished tree, contrary to the new batching requirement.
- Totems use one `worker` UUID, and their alerts copy that single worker.
- Mapped Minecraft XP-orb bytecode inspected: gravity .03, water buoyancy .0005 capped .06, .98 drag, ground friction and -.9 bounce, 8-block attraction, scans every 20 ticks, equal-value/id-mod-40 merging, 6000-tick lifetime, two-tick pickup cadence. Magica must not subclass XP orbs because vanilla merges subclasses without checking resource identity.

## Historical status checkpoint

Implementation in progress. Do not treat this file or an intermediate build as acceptance of the whole redesign. Preserve the working installed 0.1.1 until the replacement passes validation.


## Implementation checkpoint — 22 October 2026

Progression, independent Magica/orbs, infuser transfer and visuals, six-part output transactions, portable generation-token identities, shared membership, GUI, recipes and 16px assets are implemented. The worker now has a totem-ordered central dispatcher, bounded adjacent-position path planning, a persistent timber processor and a service visit that deposits all accepted cargo before resuming. Ordinary tree completion does not trigger unloading. Home recall precedes placement pause and job checks. All source is still under verification, and no redesign release has been installed yet.

The first isolated dedicated GameTest run compiled and ran 22 tests. Fourteen passed. Five failing wand assertions expected obsolete single-worker fields and were migrated to roster assertions. Three end-to-end workflows exposed a path-completion/reach tolerance problem after processing part of a tree. Approach endpoints now have an additional margin and diagnostic output; an expanded 41-test run is in progress. This is an observed regression under test, not a claimed complete fix yet.

Added focused tests for infusion conservation, pause/reload/competing donors, interaction hands, XP and Mending independence, reward eligibility and completed break deduplication, concurrent/full-inventory assembly, packing and stale-link reconciliation, paused recall, compact fall, three workers, cross-tree cargo retention, and actual vanilla growth of eight species.

At that checkpoint, outstanding work was to resolve test findings, verify exact recipe runtime crafting and storage alternatives/nearest ordering, complete client visual/audio-event smoke checks, review lifecycle/performance, format, build/version/package, update player/design/verification docs, mirror editable sources and tested artifacts, and install only the validated build.


## Server milestone

`redesign-tests-10.log`: all 45 required tests passed. Generated-species checks now use actual worker movement and work state until committed felling. Fixes found during stronger verification included mutable root positions supplied by iterators, native navigation not being ready on an airborne tick, a reach tolerance at finished paths, natural jungle vines/mangrove attachments, and elevated mangrove trunks needing an actionable grounded root cut. Full conversion suppresses intermediate neighbor drops before publishing updates, avoiding a second physical payout for owned attachments. The ordered-dispatch fixture explicitly marks its NoAI test worker grounded; ordinary workers settle through normal movement.

A 0.2.0 build and development client visual run are in progress (`redesign-client.log`). The user's Rackworks Tester client was open (PID 67408); a request to save/quit or choose staging was sent asynchronously. Do not stop/kill that user process. The development Lab is separate. The instance smoke helper now temporarily backs up/replaces matching mod JARs and restores them afterward; it has not been run for this redesign. Final installation remains dependent on the instance being available.


## Release verification checkpoint

The packaged full client run completed in Rackworks Tester after the user confirmed Minecraft was closed. It used only Automatons Lab. Logged results: 50/50 core, 0 remaining infusion balance; 7 collected Magica with XP unchanged at 140; five oak logs delivered and empty cargo; 37 positional work/completion sound events. GUI, item hologram, donor effects and roster screenshots were inspected. The helper restores previous options and mod JARs before final deployment.

All 46 assertions passed in the diagnostic server run. Intermittent failures in preceding runs were traced further: the saved failed world had gravel at the wet-root positions and a normal-noise Overworld generator. Tests at Y=-60 were under an uncleared terrain roof. The test Gradle task now writes deterministic superflat settings and the test helper preserves the prior world before each run. Correctness assertions remain, including immediate post-conversion water preservation and exact two-tree delivery. A controlled-flat release run is the final check.


## Controlled release result

`redesign-tests-flat-final.log`: all 46 required assertions passed; release build succeeded. The flat-world fixture measured 1.99561677 blocks/second and all 50 budgeted totems progressed. The new waterlogged-root test verifies water immediately after conversion and after settlement. No production protection or occupancy checks were bypassed to resolve the test-world issue. All 91 resource JSON files and 32 texture dimensions/UVs passed validation. Full packaged client evidence shows five logs delivered, cargo empty and unchanged XP after Magica collection.


## Final deployment

The final short packaged run exited 0 and its worker GUI text was inspected. Version 0.2.0 was installed in Rackworks Tester after the user closed Minecraft; the installed hash matches the verified release. The old JAR is preserved in `.automatons-release-backups/20261022-161716-781` within the instance. Only the separate Lab save was used for smoke testing. Editable assets, recipes, the player guide, complete design reference, evidence and release/source archives are mirrored in `F:\Minecraft\Automatons`.

## Particle and JEI follow-up — 0.2.1

Completed the requested upper-chest particle bursts with random sizes, speeds, acceleration and curved approach angles. Added optional public-API JEI assembly and infusion categories, synchronized assembly recipe inputs, both pedestal catalysts and an assembly-screen Recipes link. The actual installed-mod-set Lab client verified both JEI output lookups and rendered pages, high chest emissions, moving donor and full core completion, then exited 0. The apparent crash was the harness's scheduled normal shutdown; no crash report was found. The side camera initially faced away and was corrected before the final screenshots.

The dedicated server loaded without JEI and all 46 regression tests passed. Version 0.2.1 was built, inspected and installed, replacing only the prior Automatons JAR after preserving it in `.automatons-release-backups/20261022-163429-237`. The release JAR and source, revised docs, editable renderer, actual screenshots and logs are archived in the asset library. JEI recipe viewing does not perform automatic ingredient transfer.


## 0.2.2 requested revisions

Implemented the recipe/art/HUD/chopping changes, then incorporated the revised axe timings, ordinary placed/loose log harvesting, and Show Work Area toggle. Area cells are texture-free translucent overlays in air above terrain, exactly one block high, with no collision and no shared interior faces. The server retains protection and range checks for destructive work.

The full suite now has 62 passing required tests, including actual uphill/downhill journeys and return delivery, Diamond/Netherite harvesting, and stone-or-better player felling accounting. The user's report of overlapping timber traced to the old intentional one-position segmented fallback. That fallback now assigns distinct grounded target cells and reserves them across timber jobs. No-space trees wait. Existing saved stacked jobs replan without losing inventory or consumed-section state.

The first packaged client review passed (exit 0); final obstructed-fall/player-felling evidence and installation are recorded below.

The final server suite (`revision-0.2.2-tests.log`) passes all 62 required tests. Added checks cover three small orbs equaling one whole point, saved fractional balances and whole-point spending, old large-orb compatibility, separate-size merging, exact 10/100 crop and 40/100 log probability branches, and dropped log collection with exact chest delivery while out-of-range items remain untouched. The release build and resource validation also pass.


## 0.2.2 final client and deployment

The final packaged client used the isolated `build/packaged-smoke/20261022-180643` copy of the designated mod set and exited normally with code 0. Actual captures show both Magica sizes, a 9⅓ balance after collecting a large 7-point and small 7/3-point orb, separated XP/Magica bars, shadow-free JEI text, new textures, lower translucent preview, seamless range cells, axe swings and damage cracks. Frames at review ticks 2012/2018/2026 show vertical grain before the midpoint and horizontal grain afterward while the logs are still airborne. Both JEI output lookups found one recipe. Core infusion finished at 50/50 with zero balance before the pickup review. XP remained 420.

The obstructed worker fall used five distinct landing cells. The connected player’s stone axe broke the base once with one durability damage and produced one timber entity containing four remaining logs with distinct targets. The worker then entered Collecting logs, picked up that dropped base log, and processed the player’s timber. At the review cutoff, the chest held the first tree’s five logs and the worker carried eight items from the second job (normal cross-job cargo retention); exact dropped-log chest delivery is separately asserted by the dedicated server test. There was no manual multiplayer acceptance session.

Installed **automatons-0.2.2.jar**, **354,864 bytes**, SHA-256 **7E425A2A0DB94F87814DF57201217E74CC576FD26B32C9076E9A9FC0EA96973C**. The previous release and changed stock-height configuration are preserved in `C:\Users\impsh\curseforge\minecraft\Instances\Rackworks Tester\.automatons-release-backups\20261022-181131-692`. The installer verified the hash and changed only the old default pedestal height of 1.75 to 1.4; custom heights are retained. Release/source archives, updated editable assets and all final captures are mirrored in `F:\Minecraft\Automatons`.


## 0.2.3 server verification — 22 October 2026

All **67 required GameTests** passed in `revision-0.2.3-tests-6.log`, followed by a successful reobfuscated release build. This includes the earlier terrain, axe-tier, recipe, Magica and collection checks; connected rigid-fall geometry across obstacles; completed player felling into ordinary log blocks with exact total loot; save/reload and blocked-landing recovery without duplicate foliage loot; routing around a thirteen-block virtual timber barrier; deferring obstructed timber while harvesting another reachable log; and delivering cargo while a full area scan remains incomplete. Representative generated trees from all eight supported families passed.

The new tests exposed incomplete outer-canopy discovery and base Entity loading resetting timber bounds to one block. Crown discovery now follows up to six leaf connections, and complete timber bounds are restored after loading. Route obstacle/reservation queries also include tree tips distant from their entity root. Path planning searches all suitable approach cells together, detects lack of physical movement, temporarily avoids stalled nodes and releases blocked job claims for retry while other work continues.

Resource validation passed for 91 JSON assets, 33 texture dimensions, the 24-unit creature rig, and partial-block UV density. Client review and installation results follow below.


## 0.2.3 final client and deployment

The final packaged client used `build/packaged-smoke/20261022-190056` and exited normally with code 0. Both JEI output lookups found one recipe; infusion completed at 50/50 with zero remaining balance. The HUD displayed **9** after the mixed-size pickup while exact thirds remained stored internally. XP stayed at 420. Reviewed fall frames show a connected rotating trunk rather than separated moving sections, and the landing capture shows ordinary horizontal log blocks.

The connected player's stone axe broke the base once with one durability damage. Four remaining sections had distinct targets, then became **four real log blocks with zero Timber entities**. The worker harvested those blocks and collected the dropped base log. By the final checkpoint the chest held **10 oak logs** from the two five-log trees and the worker's cargo was empty. The initial client check exposed cargo waiting for a long area scan; the final build unloads during that scan, and a new server test explicitly keeps discovery incomplete while requiring delivery.

Installed **automatons-0.2.3.jar**, **364,081 bytes**, SHA-256 **13C5B0B20E5F91EFBF4E7A3BFFBD803FD16F14752F7C7E345A2B9BCD5B6DBF32**. Previous release preserved in `C:\Users\impsh\curseforge\minecraft\Instances\Rackworks Tester\.automatons-release-backups\20261022-190611-720`. The installer verified the copied JAR. The source archive, updated assets, full history, release manifest, server logs and final captures are mirrored in `F:\Minecraft\Automatons`.


## 0.2.4 safe-landing recovery and deployment

The saved two-Lumberton setup contained no Timber entities: both workers were waiting before felling their next trees. Regression tests reproduced rejection by grass/flowers and shared natural leaves. Fall planning now permits safe corridor foliage removal with one-time normal drops, retaining other trunks, placed leaves and protection. Continuously blocked falls use a three-second wait followed by a shared twenty-second retry delay, allowing other work.

All 73 required server GameTests passed in `revision-0.2.4-tests-3.log`. The reported forest fixture restores both workers' pending roots and positions; both timber jobs finish and all six oak plus seven birch logs are accounted for. Fixture alignment and timing mistakes from earlier diagnostic runs were corrected and those logs are preserved. Asset validation and the release build passed. No new client session was needed for this server-only behavior change.

Installed 0.2.4 after the user confirmed the game was saved and closed. The 375,935-byte JAR has SHA-256 `F4A06588A7E920561E66817B09DCD74817C3229CE42A0492C0CD5270EA8E9088`. The previous JAR remains in `.automatons-release-backups/20261022-201837-334`. Saved-world entity and block data were not edited. Updated history, documentation, source archive, release manifest and diagnostic/final logs are preserved in `F:\Minecraft\Automatons`.


## 0.3.0 implementation and release checks

Implemented the adopted Minington and torch specifications, personal-name catalog and lifecycle, user-requested Find button, larger expressive heads and client timber collision/fall fixes. The existing worker identity, storage, home, Totem and permission systems remain shared across jobs. Imported the supplied entrance schematic and retained the user's character reference with editable model and atlas sources.

The dedicated server passed all **96 required GameTests**, including physical mining/return/resumption, two-miner partial-build takeover, a real-supply ladder repair, lighting conservation, canonical-name allocation/migration/exhaustion, home toggles and earlier regressions. The packaged Lab client completed and exited 0; final screenshots show actual menus, labels, mine work, the new face and landed timber. Its actual movement collision check passed with 106 monotonic fall samples. Assets and the release build passed. Details and limitations are recorded in `VERIFICATION.md`.

Development exposed and corrected closed-trapdoor ascent, route selection for uncompleted tunnel lanes, deep-quarry return/resumption, standing beneath headroom blocks with the larger model, lighting-update timing and torch relocation persistence. The name-message change also exposed an old assignment cancellation check tied to display text; cancellation now uses the actual selection context. Final visual fixtures corrected camera yaw and head/body orientation before taking the face portrait. Earlier diagnostic logs remain in the project; only passing final runs are described as release verification.

Installed **0.3.0** with matching build/instance SHA-256 `BC02CAE4F446278C24FEA85A2FCFC6F270F72E3433EDE18C9A48E2AD2501BFEF`. The previous 0.2.4 release is backed up in `C:\Users\impsh\curseforge\minecraft\Instances\Rackworks Tester\.automatons-release-backups\20261022-224246-972`. No saved-world or custom configuration data was edited. The release, working-tree source snapshot, editable assets and verification evidence are mirrored in `F:\Minecraft\Automatons`.


## 0.3.1 underground storage, hazard recovery and player tool timing

Implemented the user's follow-up: twenty-block new shafts, four-item chest gating, two ordinary underground double chests, underground cargo unloading, safe-distance falling-material excavation, platform-based fluid detours and normal player-equivalent break times. Saved mine depth preserves existing excavation. Route direction, rejected wet directions, depot ownership and installed chest cells are persisted. Surface storage stays available for real supply trips.

All 101 required server tests pass in `mining-0.3.1-final-tests.log`. Tests reproduced a falling-column timing race and now require exact gravel/flint accounting, no health loss and settling before frontier advancement. Other checks require no construction with only three chests, real twenty-block construction, second-pair hauling, both fluid types, vanilla tool/upgrade timing and deep quarry recall/resumption. The final asset checks and reobfuscated build passed. Client/deployment evidence follows in `VERIFICATION.md`.

The packaged client completed and exited 0, confirming the four-chest depot at depth 20 and both visible double chests. Installed **0.3.1**, SHA-256 `DD98A896FE71E65BC7BD4F20EEF95413E07CCA9D7E2425CEE15D32B5BD8B0C92`, with 0.3.0 backed up in `C:\Users\impsh\curseforge\minecraft\Instances\Rackworks Tester\.automatons-release-backups\20261023-054228-924`. The tested source, current documentation, editable assets and evidence are archived in `F:\Minecraft\Automatons`.

## 0.4.0 implementation

Imported both supplied Sponge v2 schematics and verified their palettes/materials. Added shared warehouse construction and saved journals, home-scoped telemetry/requests/reservations, physical couriers, eighteen-slot persistence/capability/menu/drop behavior, and expanded job appearances. The previous intentional 26-pixel baseline is retained. New tests exercise warehouse interruption, owner isolation, exact metadata, narrow routes, shortage resolution, depot recall and full-storage recovery. Failed test runs exposed and corrected placement footing, reload yielding, mutable-stack transfer counting and premature moving-recipient fallback. Final evidence follows in VERIFICATION.md.

Final verification: **113 required GameTests passed** in `runnerton-tests-16.log`; the packaged client completed the north warehouse exit, narrow wooden doorway/corner and real tool handoff, then exited 0 in `runnerton-client-9.log`. Installed **0.4.0**, SHA-256 `A1BCBF36359B0B843979E13F059817C530C71B5D61FC19AB6518FEE09424692B`, with 0.3.1 backed up at `C:\Users\impsh\curseforge\minecraft\Instances\Rackworks Tester\.automatons-release-backups\20261023-085915-330`. The release library includes both original schematics, imported templates, full editable assets and diagnostic evidence.


## 23 October 2026 — 0.4.1 construction and continued work

Imported the user's replacement warehouse, versioned older approved plans, reconciled only blueprint cells, increased construction cadence, cleared soft terrain and evacuated recognized staging chests. Added real material lists, placement sounds and outside-wall yellow boundaries. Removed the mine hatch, persisted entrance completion, defaulted new quarries to the platform edge and repaired unloading/restocking continuation and ladder clearance. Added inventory-backed sapling replanting after trunk processing, with bounded scan waiting so forestry neither unloads between nearby trees nor retains cargo indefinitely during an incomplete scan.

Reworked professions into a dropdown, Apply Job and confirmation, moved Totem work settings to a larger tabbed popup, made all chests input/output, removed Rebind and pedestal result text, lowered the Magica panel and corrected the floating model's UVs. Registered popup bounds with JEI to avoid overlay overlap.

All 119 server tests passed. Final packaged client completed its door/corner/handoff scenario and visual capture session. Installed 0.4.1 with the previous version backed up; see VERIFICATION.md and the versioned release manifest for evidence and hashes.

## 23 October 2026 — 0.4.2 forestry, mining and supply rules

Implemented the additional GUI cleanup, first-job confirmation exception, larger miner helmet and Logistics activity/request list. Expanded forestry discovery to a default 32-block vertical band, added inventory-backed climb/recovery journals and physical sapling restocking. Couriers leave worker supplies in chest inventories, including construction staging chests; mine depots remain eligible pickup points when a warehouse shares their Totem. Warehouse build lists exclude chest torches, which must be supplied in runner cargo.

Tunnel miners retain independent branch journals across unloading, pausing and reloads. Quarry work avoids repeated temporary-floor lighting and unnecessary face yielding. Regression investigation also corrected long-travel timeout handling and lighting detours through neighboring tunnel walls. Successful and diagnostic release evidence is recorded in VERIFICATION.md and the versioned manifest.

Final 0.4.2 verification: all **123 required GameTests passed**, asset checks passed, and the isolated packaged client exited **0** with its first-job and physical-passage assertions. Installed SHA-256 `F64E206D6620E60B4CD3421B60C4AA4CCE3927B378EFF0AACF33AB45891EE37F`; 0.4.1 and the three changed default forestry-height settings are backed up under `.automatons-release-backups/20261023-162417-413`.


## 24 October 2026 — 0.5.0 The Network

Implemented the approved five-worksite milestone using the existing identity, journal and logistics services. Added Buildington/Hammer and Farmington with shared fields, purpose/facing setup, real display frames, exact build lists, persistent construction claims and physical first-builder bootstrap. Imported the latest five-building bundle and Farmer's Shed notes; exported the requested mine-hatch removal and Builder chest adjustment.

Applied the user's overrides: one horizontal Totem range for work and chests, no inner exclusion, purpose-specific defaults, unchanged fast construction and Magica, and no Hammer/tool/torch/sapling courier withdrawals. Leaves owned by a felled tree resolve immediately with ordinary leaf loot; neighboring foliage and non-leaf attachment ownership remain protected. Exact verification results are recorded in VERIFICATION.md and the release manifest.

All **133 required server tests passed**, asset validation passed, and the final isolated packaged client exited **0** with 17 captures. Installed the verified 0.5.0 JAR in Rackworks Tester after the user closed the game; preserved 0.4.2 under `.automatons-release-backups/20261024-020313-576`. Installation changed no saved-world or server configuration data.


## 24 October 2026 — 0.6.0 Lumina and Lumite

Implemented the approved Lumina overhaul while retaining existing energy fractions, reward behavior and Network identities. Added both Lumite ores, deterministic normal generation, four physical infusion corners, persistent shard progress, exact costs and the revised crafting progression. Retired all ten upgrades and the unused plate, with one-time inventory-safe legacy extraction. Redrew the native Hammer and retained builder behavior while adding slower wooden-tier player harvesting.

The complete 143-test server suite passed. Three generated seeds yielded 911 ore blocks across 36 chunks. The disposable retrogen world completed its single interrupted/resumed pass over all eligible FULL chunks; a post-restart audit retained exactly 3,402 saved coordinates and every original generation stage. Earlier ticket-related failures are retained in diagnostic evidence. Sixteen final packaged-client captures were inspected, including zero-light emissive veins, paid four-corner infusion, Hammer views and the reflowed GUI. Release and installation hashes are in the 0.6.0 manifest; no live-world retrogen or configuration migration was performed.


## 0.7.0 current run

Completed fuel pieces, command removal with inert retained history, three-level rooted-trunk felling, size-8 new-chunk Lumite, optional JER and the required GuideME handbook. The latest builder fix moves construction to a reachable supported position outside the footprint, removing temporary access-hole demolition and per-block navigation. All 153 required server tests pass, with preserved old terrain/history and packaged client checks. See VERIFICATION.md for measurements, source/API discrepancies and the three unavailable checks. User-supplied art remains unchanged; the collector is a design checkpoint only.
