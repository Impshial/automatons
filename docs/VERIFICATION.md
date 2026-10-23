# Verification — through 0.2.4, 22 October 2026

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

## 0.2.0 release result (superseded by 0.2.1 below)

- `redesign-tests-flat-final.log`: **all 46 required tests passed**, followed by successful reobfuscation/build in 1m29s. The controlled-flat run measured **1.99561677 blocks/second** and confirmed fair progress for all 50 totems.
- `tools/validate_assets.py`: **91 JSON resources, 32 textures, the 24-unit rig and proportional block UVs passed**. The distributable's required classes, version, JSON, revised recipe set and UTF-8 strings were checked directly.
- Full packaged instance run exited normally and delivered the five-log tree with empty cargo. The final short client check also exited 0; the verified JAR was installed in Rackworks Tester, with its prior release backed up outside mods.
- Distributable: `automatons-0.2.0.jar`, **302,640 bytes**. SHA-256: `A85EFD46E5C76B250E8277146B62690890D35A5DB80741C56D662F3E696D0EA0`.

Logs and actual screenshots are organized under `F:\Minecraft\Automatons\verification\0.2.0` and `screenshots/0.2.0`. The release folder holds the JAR, source archive, install helper and hash manifest.

At the 0.2.0 release, installed path: `C:\Users\impsh\curseforge\minecraft\Instances\Rackworks Tester\mods\automatons-0.2.0.jar`. Previous JAR backup: `C:\Users\impsh\curseforge\minecraft\Instances\Rackworks Tester\.automatons-release-backups\20261022-161716-781`. Exactly one Automatons JAR is installed. The installed SHA-256 matches the distributable above.

## 0.2.1 particle and JEI update

The user requested simultaneous chest emissions with varied sizes, speeds and arrival angles, then requested JEI recipe GUI integration. The particle renderer now emits 2–4 motes per burst across the upper chest/shoulders, with independent cubic curves, lifetimes, acceleration and sizes. Existing server accounting is unchanged.

The JEI plugin compiles against the public common/Forge 15.56.0.205 APIs and is discovered only by JEI. It registers assembly recipes from the synchronized server recipe manager, plus the world-infusion display, input/output slots, pedestal catalysts and a conditional Recipes link in the assembly screen. Worker/totem screens do not receive that link. No JEI implementation classes or runtime JAR are bundled. The optional client dependency declares the tested API floor and the 15.x major version. Setup follows the [official JEI ForgeGradle documentation](https://github.com/mezz/JustEnoughItems/wiki/Getting-Started-%5BMinecraft-1.18.2-to-1.20.1%5D).

Executed checks:

- `jei-infusion-0.2.1-build.log`: packaged build succeeded. The final optional-dependency metadata was then included in the dedicated-server build below.
- `jei-infusion-0.2.1-server-tests.log`: a fresh superflat dedicated server **without JEI** loaded successfully; **all 46 required tests passed**, and the final build succeeded in 1m41s.
- `instance-smoke.log`: the actual Rackworks Tester mod set, including JEI 15.56.0.205, completed the opt-in Lab scenario and exited **0**. API output lookups found one assembly recipe and one infusion recipe. The pedestal click-area handler opened the assembly page; an output-item focus opened infusion. Both actual JEI screens, the pedestal Recipes label and three side-view chest-particle captures were visually inspected. The first observer attempt faced away from the scene; its camera heading was corrected before these captures.
- The same client run completed a 50-point core with balance 0, then collected 7 Magica while XP remained 560. Particle checks included a donor position change. Screenshots show the visual spread and size variation; there was no hand-operated acceptance session or remote multiplayer check.
- Asset validation still passed 91 JSON resources, 32 textures, the 24-unit rig and proportional UVs. The release JAR was inspected for the new plugin/renderer classes, version, optional JEI metadata and translations, and verified to contain no bundled `mezz/jei` classes.

The reported apparent crash matched the automated test's scheduled shutdown: its process exited 0, the log recorded orderly saves, and the designated instance had no crash-report directory. The test helper restores its temporary JAR/options after exit. This conclusion applies to the observed test window and logs.

Reproduce the packaged visual checks with `python tools/instance_smoke.py --quick --infusion-review --jei-review` while the designated instance is closed; it opens only Automatons Lab and exits automatically. The optional camera exists only in the client review and is not added to the world. Use `-PwithJei` for a development client with JEI; ordinary server runs retain no JEI runtime dependency.

Final release: **automatons-0.2.1.jar**, **318,358 bytes**, SHA-256 **1087C00189AC0864604D14EF63A510C22047B73CCFD063629D0BF50F3DFFB337**. Installed in `C:\Users\impsh\curseforge\minecraft\Instances\Rackworks Tester\mods`, with 0.2.0 backed up in `.automatons-release-backups/20261022-163429-237`. Exactly one Automatons JAR is installed. Evidence and captures are mirrored under `verification/0.2.1` and `screenshots/0.2.1` in the asset library; the release manifest records the matching source commit.


## 0.2.2 revision validation — 22 October 2026

The fresh dedicated-server suite passed **all 62 required GameTests**, with JEI absent from the server runtime. `revision-0.2.2-tests.log` contains the run and successful reobfuscated build. This expands the previous 46 tests with all six vanilla axe acceptance/timing checks; all six arm/two-high placements and three leg columns; complete Diamond/Netherite harvesting and exact log delivery; Stone-axe travel up and down three one-block terraces, harvesting, and delivery back at the storage elevation; clearing low leaf cover; completed player-break accounting; placed/stripped log collection through grass and flowers; loose-log range/protection checks; unique fallback landing cells; occupied-cell/duplicate-layout rejection; no-space waiting; and saved stacked-timber migration without losing sections.

The terrain tests exposed thin totem bases being treated as walkable nodes and an escape path ending just short of clearing a fallen log. Totems now report blocked path nodes, and escape destinations allow for navigation's stopping tolerance. The final terrain tests require actual movement to the other elevation and exact five-log chest delivery back at the original elevation.

The axe timings are representable by the five-tick worker clock. Stone, Iron, Diamond and Netherite preparation/processing durations are 140/80, 100/60, 70/40 and 65/35 ticks respectively. Wood and Gold retain their earlier defaults. Loose logs use processing time directly. These timings exclude navigation and the 30-tick fall animation.

Asset validation passed: **91 JSON files, 33 correctly sized textures, a 24-unit creature rig, and correct partial-block UV density**. The old `automaton_core` registry ID remains; its display name is Automaton Torso, preserving existing saved items.

The first packaged client run, using a fresh isolated copy of Rackworks Tester's mod set and config, exited normally with code 0. Reviewed captures show separate XP/Magica bars, the new wand and pedestal, lowered translucent preview, shadow-free 50-Magica JEI text, a seamless terrain-following area preview, chopping poses, and fallen-log cracks. Both JEI output lookups returned one category recipe. The worker delivered all five original logs and emptied its cargo. The final distinct-cell fallback and player-felling client result is recorded below.

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


## 0.2.4 safe-landing recovery — 22 October 2026

A read-only inspection of the reported saved forest found **zero Timber entities**. Both workers had a new pending root, empty cargo and zero preparation progress. The blocked roots were an oak at 99,64,-70 and a birch at 95,64,-70. This agrees with the report that no tree was falling: the workers were rejecting plans for their next trees.

Isolated regression tests reproduced two obstruction mistakes in the old implementation: grass/flowers were treated as solid fall obstacles, and shared natural canopy could reject every fall direction. The update trims permitted foliage in the actual fall corridor, keeps neighboring trunks/placed leaves/protected blocks, and records ordinary plant drops once. Cleared crown cells no longer enlarge the moving trunk's obstruction sweep. A continuously blocked plan is released after 60 ticks and deferred for 400 ticks across the Totem's workers, allowing other reachable work.

`revision-0.2.4-tests-3.log` records **all 73 required GameTests passed** and a successful reobfuscated build in 1m49s. The suite includes the previous 67 tests plus ground cover with two-block flower loot, nearby-worker evacuation, shared-leaf corridor trimming, protected foliage, impossible-fall deferral, and the reported two-worker forest. That last fixture contains only natural block states copied from the saved area; newly created test workers restore the reported positions and pending roots. Both target timber jobs finish, with **6 oak logs and 7 birch logs** accounted for across storage and worker cargo. It does not require those workers to unload while more forest jobs remain.

Earlier diagnostic logs are retained. The old-code ground-cover/shared-leaf tests failed as expected. The forest fixture initially had a template-origin offset, then an overly early unload assertion; the impossible-fall fixture initially overwrote its own Totem. Those fixture mistakes were corrected before the final passing run. The nearby-worker test already passed before this patch and is retained as coverage, not evidence of a changed evacuation algorithm.

Asset validation passed 91 JSON resources, 33 textures, the 24-unit rig and proportional UV checks. No new client graphics or manual gameplay acceptance session was run for this server-behavior patch; the 0.2.3 client evidence above remains historical.

Installed **automatons-0.2.4.jar**, **375,935 bytes**, SHA-256 **F4A06588A7E920561E66817B09DCD74817C3229CE42A0492C0CD5270EA8E9088**, after the user confirmed Minecraft was saved and closed. The previous JAR is preserved in `C:\Users\impsh\curseforge\minecraft\Instances\Rackworks Tester\.automatons-release-backups\20261022-201837-334`. No saved-world entity or block data was edited. Existing pending jobs resume through the corrected planner after loading. Versioned source, logs, manifest and documentation are archived in the asset library.
