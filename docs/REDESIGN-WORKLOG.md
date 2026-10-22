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
