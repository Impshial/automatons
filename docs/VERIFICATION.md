# Verification — 0.2.0 redesign, 22 October 2026

## Executed server checks

The dedicated Forge GameTest server exercises server loading without client rendering classes. The redesign suite covers the original survival and creative wand regression, plus Magica, infusion, assembly, portable identities, shared dispatch, recovery and generated tree families. Final release results and hashes are recorded with the release artifacts.

The 45-test milestone passed in `redesign-tests-10.log`. A waterlogged-root case was then added. The first expanded run passed that case but failed two older checks in a reused GameTest world: occupied dark-oak fall space and a crown-payout precondition. That world was preserved under `build/test-world-backups` and the expanded release suite rerun in a fresh world. The fresh run resolved both failures. Its two-tree workflow was still traversing the second timber at the original 1600-tick fixture deadline, so that test now allows 3400 ticks for two complete trees and shared-budget empty-area discovery; its no-intermediate-unload and exact ten-log assertions are unchanged. A subsequent fresh world exposed intermittent water/fall failures. Inspection of its saved region found gravel replacing expected water; level data and server.properties confirmed a normal noise world. These fixtures run at Y=-60 and only clear their template volume, leaving an overhead terrain roof. The test task now explicitly generates a deterministic superflat world. The diagnostic normal-world run passed all 46 tests; final results below also cover the controlled flat fixture. Failed logs are retained as evidence, not omitted.

Coverage includes:

- Real `Player.interactOn` / `ServerPlayerGameMode.useItemOn` selection and assignment in creative/survival, either hand, including the prior creative held-stack bug.
- Inclusive Magica reward ranges and zero outcomes, category precedence, completed-break deduplication, cancellation and fake-player exclusion. Magica orb collection leaves XP and Mending unchanged.
- Exact 50-point infusion: 49 points after 199 active ticks, full at 200. Partial persistence, range pause, missing donor, competing infusers, off-hand priority, repeated insertion, owner retrieval, stacking and single break payout.
- Six-component output previews and transactional pickup: full inventories and simultaneous viewers cannot spend twice or lose components. No second Magica payment. All ordinary recipes execute through the runtime recipe manager; partial cores are rejected, including Tier II's exact predecessor/core requirements.
- Portable UUID/generation identity, unchanged health, exact tool/cargo drops, stale membership reconciliation, duplicate deployment rejection and placement pause yielding to persistent recall. Legacy single-worker roster/inventory migration.
- Three workers taking distinct jobs; nearest-to-totem ordering independent of worker position/insertion; partial stacks and alternative chests; restocking; two trees before unloading; no-wood return delivering all ten logs.
- Generated oak, birch, 2×2 spruce, 2×2 jungle, dark oak, acacia, mangrove and cherry. Every species check requires a real worker to approach and commit felling, with its recognized wood count preserved.
- Compact fall with obstructed long layouts, protection during committed timber, one authoritative crown payout, save/load of staged payouts, tool exhaustion, independent alerts and automatic full-storage/no-wood recovery.
- Natural waterlogged mangrove attachments retain their fluid, settle without water being treated as a solid obstruction, and emit no duplicate ordinary attachment drops.
- Measured level-ground movement ranged from 1.68 to 2.00 blocks/second across normal-world runs; the final diagnostic run measured 1.9956. The target remains approximately two. Fifty loaded totems all make scan progress under the shared budget. This checks fairness, not unlimited throughput or a multiplayer performance certification.

## Actual diagnoses and changes

Code inspection found the old root search also restricted canopy blocks to the work square, explicitly rejected mangroves, assumed dirt immediately below every trunk, accepted only a long whole-tree fall and unloaded partial cargo after every tree. The old totem held one worker UUID and dispatch used discovery insertion order.

Stronger gameplay regressions exposed additional failures: a path could finish within Minecraft's navigation tolerance but outside actual chopping reach; a native path query on an airborne tick could falsely skip the nearer tree; a mutable `BlockPos` supplied by iteration changed a stored canonical root; natural jungle vines and elevated mangrove root arches blocked otherwise valid approaches/falls. These are observed failing transitions, rather than speculative diagnoses.

The implementation now plans adjacent standing positions using the worker's actual box and reach, leaves deferred path work neutral, uses immutable canonical roots, recognizes bounded natural attachments and permits a grounded root cut for elevated mangroves. It tries four fall orientations followed by compact staged timber. Source/crown conversion suppresses intermediate ordinary neighbor drops, publishes updates after the group mutation and preserves water. Standing claims are transient leases; committed timber owns persistent sections and pending loot.

Dispatch waits for sufficiently close discovery before choosing stable totem-distance order. Every worker gets its own claim, alerts and home. Capacity checks query actual relevant item stacks across eligible handlers and finish a fresh discovery pass before confirming fullness. Actual transfers recheck handlers and preserve remainders. Required totem visits unload all accepted cargo and restock before resuming; ordinary tree completion does not create a chest trip.

Optional `logWorkTransitions` emits worker identity, position, state/flags and skipped approach reasons, limited to one message per second per worker.

## Client evidence

Two Forge development clients ran the opt-in scenario in the separate `Automatons Lab` save. The second completed normally (`redesign-client-2.log`). Screenshots came from the running Minecraft renderer, not mockups.

- Worker, six-component assembly and shared-roster screens rendered. Assembly was inspected at 320×240 scaled GUI size. Review found and corrected worker buttons overlapping the inventory label.
- The permanent item display was visible with empty inputs and zero Magica. Review corrected its initial brown appearance to the intended translucent white/purple palette while retaining the actual item's silhouette, tilt and rotation.
- The half-height claw infuser rendered the moving-donor stream, pulsing core and green completion. Logged final charge was 50 with balance 0 from 50 available points.
- HUD, 1.5-block model, equipped axes, work animations and green/red protection curtains rendered. Forestry logs reached travelling, chopping, felling and grounded processing; server end-to-end tests additionally assert actual delivery totals.
- The second run received 37 positional work/completion sound events in the client sound system. No claim is made of human listening, stereo assessment or remote multiplayer listener testing.

After the user closed Minecraft, the reobfuscated 0.2.0 JAR completed a full run in **Rackworks Tester**, with its actual installed mod set and Forge libraries. It used only the separate **Automatons Lab** save and an offline test identity. Launcher credentials and the user's gameplay save were not read. The helper restored the prior JAR and options after the smoke, before final installation.

`redesign-instance-full.log` records a full core with balance 0, collection of 7 new Magica while XP stayed at 140, 37 work/completion sound events, and final **five oak logs in the chest with zero cargo items**. The flow reached chopping, felling, processing, unloading and no-wood waiting. The three-entry roster and actual-mod-set GUI screenshots were inspected. A final short packaged pass exited normally and verified the finished build's readable GUI text, full infusion, and independent collection after text-encoding cleanup. The optional `--isolated` helper mode can copy the mod/config set for future checks while the user's session remains open; it was not needed for this completed instance run.

## Reproduction and practical limits

Java 17, Minecraft 1.20.1 and Forge 47.4.10 are the tested target. Run `python tools/validate_assets.py` for JSON/PNG/model/UV checks. Run `tools/test_release.ps1` for a preserved prior test world, fresh dedicated-server regressions and release build. Use `gradlew.bat runClient -PvisualSmoke` for the opt-in development scenario, or `python tools/instance_smoke.py --isolated` for the packaged mod set. These harnesses reset only their separate Lab/test worlds.

There was no hand-operated mouse/keyboard acceptance session, dedicated multiplayer latency test, every-seed tree-shape sweep, arbitrary claim-plugin certification or hard-crash transaction test. Native orb physics and pickup are server tested and the renderer is client exercised; subjective similarity of pickup feel is not certified. Ordinary serialization tests are not an operating-system crash guarantee. See `LIMITS.md` for bounded tree sizes, unloaded anchors, storage integration and extension points.

The offline smoke identity can produce expected Mojang/Realms authentication messages. Those are not evidence of an Automatons failure. Existing third-party warnings are retained in the packaged-client log and assessed separately.

## Release result

- `redesign-tests-flat-final.log`: **all 46 required tests passed**, followed by successful reobfuscation/build in 1m29s. The controlled-flat run measured **1.99561677 blocks/second** and confirmed fair progress for all 50 totems.
- `tools/validate_assets.py`: **91 JSON resources, 32 textures, the 24-unit rig and proportional block UVs passed**. The distributable's required classes, version, JSON, revised recipe set and UTF-8 strings were checked directly.
- Full packaged instance run exited normally and delivered the five-log tree with empty cargo. The final short client check also exited 0; the verified JAR was installed in Rackworks Tester, with its prior release backed up outside mods.
- Distributable: `automatons-0.2.0.jar`, **302,640 bytes**. SHA-256: `A85EFD46E5C76B250E8277146B62690890D35A5DB80741C56D662F3E696D0EA0`.

Logs and actual screenshots are organized under `F:\Minecraft\Automatons\verification\0.2.0` and `screenshots/0.2.0`. The release folder holds the JAR, source archive, install helper and hash manifest.

Installed path: `C:\Users\impsh\curseforge\minecraft\Instances\Rackworks Tester\mods\automatons-0.2.0.jar`. Previous JAR backup: `C:\Users\impsh\curseforge\minecraft\Instances\Rackworks Tester\.automatons-release-backups\20261022-161716-781`. Exactly one Automatons JAR is installed. The installed SHA-256 matches the distributable above.
