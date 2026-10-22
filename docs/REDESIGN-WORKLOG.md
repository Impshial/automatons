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
