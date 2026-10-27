# Verification - 0.9.1, 25 October 2026

The full regression run covered 232 tests: 231 passed, and the distant-recall fixture failed because its last chunk was outside the test's ticking area. The chunk-coordinate bounds were corrected without adding production chunk loading. The final combined mining/Machine run passed **all 32 required tests**, including a new all-face/all-orientation Operator Station test. Across these runs, all 233 distinct tests passed. The focused run verifies actual 117-block partial-path recall, shaft startup without surface storage, all twenty rungs and the underground depot, old top-rung repair, hillside approach, recall during descent, full/partial bottle and bulk Tank/bucket refueling, proportional drinking and creative empty bottles, overflow/fraction persistence, packed Machine contents and blocked placement, temporary Tank spills, Drain conservation/full/incompatible tanks, and engine startup.

The exact installed JAR passed an isolated Forge client review using the user's mod set and a disposable Lab. It exercised held-R OS repeats through startup, the visible “Starting... Please wait” message, actual driving, physical operator boarding, filled Tank and Drain models, and a real player standing on/off the Drain. The Drain moved 750 mB for exactly 15 Lumina, then stopped after stepping off. Model screenshots were inspected. No player world was edited.

Asset checks pass 201 JSON files, 44 texture dimensions, native block UV density and zero overlapping visible Machine surface patches. Handbook checks pass 33 pages, 29 live recipes, 35 item associations and seven structure previews. Package checks compare all 254 authored resource files with the JAR, confirm ordinary Tank loot is empty and ensure handbook pages remain external.

Installed `automatons-0.9.1.jar` and the editable handbook in Rackworks Tester; prior files were backed up. SHA-256: `A2C08FEDD6F6B9B60BCE2400E1AEFFBC0BC663793E7E2D2682B8F3C3CE8C1E34`. Evidence: `F:/Minecraft/Automatons/verification/0.9.1/`. The manifest preserves the initial diagnostic failure and corrected final results.

# Verification - 0.9.0, 25 October 2026

All **219 required server GameTests passed** in the full-suite run. The final focused run passed **19 Machine tests**, adding unloaded-endpoint assignment cleanup after that full run. The full suite includes physical Runnington pickup/delivery and all prior construction, forestry, mining, farming, logistics and Luminall regression coverage.

Machine checks cover four assembly orientations and invalid-pattern atomicity; parser bounds; nine-block Bore cuts/floor preservation; attachment compatibility; exact pause/save/resume progress; full cargo and reversing; full-footprint range and turning sweeps; operator exclusivity, physical boarding and release; explicit DROPOFF and piloted unload/return; unexpected Program removal; real logistics inventory; bottle/container conversion and Tank energy persistence; selected-chunk edges; full-width steps/gaps; entity turning clearance; exact-once dismantling; whole natural trees; no Bore Lumina; and finishing the final paid step before fuel exhaustion.

Diagnostic runs caught a Minecraft protected text-field type, default pathfinding stopping short of the boarding point, and a test Pedestal falling inside the chunk-clearing route at some suite placements. The editor adapter, exact boarding endpoint, and alignment-independent fixture were corrected. The first full run passed 217/218; the subsequent full run passed all 219, including an additional last-fuel regression. Earlier logs remain archived.

Operator assignment is now recorded in the existing persistent worker ledger. Releasing an unloaded worker clears its saved link, and an unloaded Machine reconciles a subsequent profession change. A dedicated regression simulates unloading either endpoint and verifies both links clear on reload.

The first packaged Forge client review completed successfully using the installed mod set in an isolated copy of Automatons Lab. It checked model rendering, synchronized Program text and scrolling, both real cargo pages, GUI boarding, HUD and an actual seated operator. Review identified editor opening position, selection clarity and HUD overlap; these were refined before the final client review.

Asset validation passes **190 JSON assets and 44 PNG dimensions**. Handbook validation passes **33 pages, 28 live recipes, 33 unique item associations and seven structure previews**. Machine validation verifies native 16x16 material resolution, model extents, nine drill pivots, recipe yields and the Lumite palette edit. Exactly 50 pixels change in each ore interior; all other baseline pixels, including rock background and rim, remain unchanged. Package verification compares 241 authored resources with the JAR and confirms handbook pages remain external and Tank loot retains stored engine fuel.

The final exact-JAR client review completed with all scripted assertions: named pedestal selection, Program synchronization and scrolling from line 1, cargo rows 1–12, GUI boarding, actual forward-key movement with the revised HUD, and physical Operatorton boarding after manual driving. The final package was installed as `automatons-0.9.0.jar` in Rackworks Tester; the previous JAR was backed up and the external handbook was updated. Installed SHA-256: `FA9BF81647F90F6A32A6FF384B09518F97967B0661B45F4444E2F141F58D0AA9`.

The isolated client uses an offline test identity and never opens a play world. Unrelated installed-mod network/authentication and JER villager-registration warnings are recorded in its log. Those warnings do not invalidate the observed Machine test completion. No claim is made about every third-party shader or server protection plugin.

---

# Verification - 0.8.3, 25 October 2026

The final full isolated server run passed 199 of 200 required GameTests, including real fallen-log collision and exact-once payouts, manual removal/replacement, saved partial cargo, animation identity reconciliation, harvest deliveries, network ownership, tool tiers, helper claims and a Minington returning to its saved tunnel 20 blocks underground after helping forestry. Its one failure exposed the forestry reach check measuring elevated clearing targets from the worker's feet.

The corrected clearing path measures reach from the eyes, with the existing 4.5-block interaction limit, and retains line of sight. A focused final run passed all three tests: the previously failing elevated fall-clearance scenario, miner-assisted stone clearance followed by successful felling, and the new Collector's open basin / solid rim / pillar / bottle-stand collision. Existing mining and ordinary wood-processing reach are unchanged. The final Java 17 build completed successfully.

Earlier runs exposed outdated virtual-timber expectations, insufficient foliage in one test fixture, and the planner favoring elevated clearings. Fixtures now assert real logs, and the planner favors lower falls and reachable obstructions. It can use inventory-funded climbing supports. First, full-repeat and focused logs are retained rather than reporting an uninterrupted full-suite pass.

Asset validation passed 142 JSON assets and 44 texture dimensions; handbook validation passed 30 pages, 19 recipes, 24 associations and seven structures. Packaged resources match source. The Collector model is the only changed existing game resource compared with 0.8.1; PNGs, recipes, Tank model and worldgen remain identical. Its collision cuboids exactly match the model. The renderer places actual fluid in the open basin and the supplied bottle on its raised stand, with a wooden stopper and glass highlights. There are no copper references.

An offline preview rendered from the actual model and vanilla textures was shown before installation. No Minecraft client was launched. The server checks verify behavior and collision; the preview does not certify every shader or third-party renderer. The JAR and external handbook were installed in the closed Rackworks Tester instance, with the previous JAR backed up and local handbook edits preserved. Hashes, source archive, logs and the preview are in the versioned release library.

---

# Verification - 0.8.2, 25 October 2026

All 186 required isolated server GameTests passed, followed by normal server shutdown and BUILD SUCCESSFUL. Eight new tests cover shared ground pickup and idle Builder shed behavior. The original 178 tests continue to cover construction, forestry, mining, farming, logistics, doors, inventory conservation and Luminall.

Pickup checks run all five professions plus unassigned workers with normal ticking, both authorized and paused. They cover arbitrary nearby item types, useful-tool routing, spare tools without replacing equipped tools, named/damaged item metadata, partial stacks, full cargo, two competing workers, pickup resumption after freeing space, pickup delays, offline-owner protection, blocked line of sight and distant items left alone. Cleanup does not create a travel job. Existing Runnington tests now allow its subsequent loose-cargo trip to store a recovered hatch after completing the reserved ore delivery; recall and parcel-conservation assertions remain.

Four rotated Builder sheds verify physical entry through the door, a stable indoor waiting position and leaving for a newly created construction request. The four existing door-exit tests still cover both hinges, now with an explicit Recall so they continue to test returning to the main Pedestal. The first run passed 182/186: two courier checks assumed no extra loose cargo, and two idle-shed cases exposed a mismatch between navigation's stopping tolerance and the waiting-position tolerance. The latter is corrected to accept navigation's actual arrival distance. The repeated full run passed all 186.

The final offline Java 17 Gradle build, packaged-resource comparison, asset validation (142 JSON assets and 44 texture dimensions) and handbook validation (30 pages, 19 live recipes, 24 item associations and seven structures) passed. Every game texture, model, recipe, worldgen file and other resource remains byte-identical to 0.8.1; only worker behavior, tests and release metadata changed in the JAR. The built external handbook matches its editable source defaults.

No Minecraft client was launched for this behavior update. Physical movement and item transfers were verified on the isolated server; appearance remains the 0.8.1 version already reviewed in-game. The installer requires Rackworks Tester closed and preserves its previous JAR and customized handbook pages. Installation status, source/JAR hashes and test logs are recorded in the versioned release manifest.

---


# Verification - 0.8.1, 25 October 2026

The Java 17 offline Gradle build and asset validation passed (142 JSON assets, 44 texture dimensions and native block-face UV density). A byte comparison against the archived 0.8.0 JAR confirms that only version metadata and the Tank/Collector model JSON files changed. All Java classes, recipes, PNG textures and other resources remain identical; gameplay tests were not repeated for this appearance-only update.

Both models contain no copper texture references. Collector trim and break particles use vanilla stone; Tank framing and break particles use vanilla glass. Item models inherit the updated block appearances. Cuboid geometry is unchanged.

The offline preview was shown before launching Minecraft. An isolated packaged client with the installed mod set, GuideME, JEI and JER completed its visual harness and exited normally. Actual screenshots confirm stone Collector trim, glass Tank framing, visible fluid/bottles and updated inventory icons with no missing textures. The normal instance and player saves were not opened for testing. Logs, preview, screenshots and installation receipt are archived with 0.8.1.

---

# Verification - 0.8.0, 25 October 2026

The isolated Forge server completed **all 177 required GameTests** and exited with `BUILD SUCCESSFUL`. This includes 12 new Luminall/ore/Infuser checks and four Builder exit regressions, each exercising both door hinges in a rotated, completed Builder shed. Existing construction, mining, forestry, farming, logistics and conservation tests also pass. The partial-kit construction case now confirms actual construction before the full kit is available, followed by a recoverable supply wait with the same project claim.

Collector checks cover the exact 4x4 boundary and nine Y levels; planted flowers and mature crops; rooted trees and structural-log rejection; overlapping output sharing; full/capped tanks; normal server tick production and resumption after extraction; exact bucket/pipe/bottle quantities; partial bottle retrieval/reinsertion; real dropped-machine NBT; save/load; capacity reduction; and hunger-independent drinking with bank-capacity enforcement. Real bucket placement/source pickup and an enclosed two-source channel verify that flowing fluid cannot become an infinite source or yield a bucket. Both recipes are tested through RecipeManager, including rejection of a fluid-filled Tank as an ingredient.

The first full run passed 171/172 checks. Its failed bucket test passed an air block to LiquidBlock's pickup method. The corrected fixture uses an enclosed channel with an actual flowing-fluid block. The repeat full run passed 177/177, including the added automatic-tick and Builder door cases.

After adding main-Pedestal Build Lists, **all 26 worksite tests passed** against the final gameplay build (178 distinct server tests across the two runs). The new case verifies active/finished/paused project filtering, Network ownership, live cargo counts and unloaded-site reporting without chunk loading. The packaged client also opens the real Pedestal button, selects a project and scrolls its material list.

The late Hammer change raises capacity from 250 to 2,000 after the full server run. The final packaged client explicitly asserts the registered ItemStack maximum is 2,000. Its player mining speed, harvest tier and wear logic are unchanged; the server suite still verifies those behaviors. JER in the packaged client logs separate stone (-32..64, peak 16) and deepslate (-64..0, peak -32) entries. The terrain algorithm is sampled across 1,024 chunks in the server test; existing chunks are not regenerated.

Asset checks pass **142 JSON files and 44 texture dimensions**, including native block-face texel density. All six authoritative ore/shard/fuel PNG hashes remain unchanged. Handbook validation passes **30 pages, 19 live recipes, 24 unique item associations and seven structures**. All five guide-installer regression tests pass. The JAR contains no handbook content; pages remain in the editable resource pack, with user edits preserved by the installer.

An offline model preview was shown before launching Minecraft. The packaged release was then checked in a disposable copy of the installed mod set, with GuideME, JEI and JER. The visual harness checks the machine models, visible fluid and bottles, Wand boundary, raised shards and item models; screenshots and runtime logs accompany the release. This does not certify arbitrary third-party pipe behavior or multiplayer rendering. The normal instance and its saves were not used as the test world.

Versioned source/JAR hashes, validation output, screenshots and the installation receipt are retained in the release library. Installation status is recorded in the manifest.

---

# Verification - 0.7.5, 25 October 2026

The Java 17 offline Gradle build succeeds and creates the mod JAR plus a separate unpacked handbook resource pack. All 41 handbook files moved byte-for-byte from 0.7.4: 29 Markdown pages, four images, seven scene structures and their manifest. Together with `pack.mcmeta` and editing instructions, the pack has 43 files. The build-output pack matches the editable repository defaults exactly. The JAR contains no handbook content; only version metadata differs among its retained entries. Every Java class, gameplay resource, recipe, model and game texture matches 0.7.4 exactly.

Handbook validation passes all navigation, relative links/anchors, 17 live recipes, 20 unique item associations and seven vanilla structure scenes. Regenerating scene NBT leaves all handbook file bytes unchanged. The regular asset validator passes. Five installation regression tests cover enabling the pack without disturbing existing options, idempotent reinstallation, updates that retain custom text/deletions/extra pages, adopting existing user content, malformed-options preflight and rollback after a simulated write failure. Installer PowerShell syntax validation also passes.

GuideME 20.1.15's local `GuideReloadListener` source confirms pages are loaded from the resource manager on reload, including external packs; registration and live recipe rendering stay unchanged in the mod. No Minecraft client was launched and there was no new in-game visual acceptance session. Gameplay tests were not repeated for this packaging-only change; the byte-identical code passed the 0.7.4 tests below. The installer refuses to modify the active instance while Minecraft is running. Installation status and its final receipt are tracked in the release manifest.

---

# Verification - 0.7.4, 25 October 2026

The full isolated server regression suite passed **all 161 required GameTests**. It covers construction, item conservation and logistics, mining, forestry, farming, fuel and infuser collision. Its disposable server was stopped after the passing summary when world saving stalled; the Minecraft client was not launched.

Construction checks exercise real AI from an empty foundation and from workers on a chest, stonecutter or crafting table. They verify supported exterior positions, travel around multiple sides, two-tick action spacing, no pending movement during placement, correct upper structures, and preservation of extra interior workstations. The bootstrap builder physically stages its kit, completes the shed, reclaims the temporary chest, stores leftovers and returns home. A partial kit waits with its project claim and available materials staged. A courier delivers to the construction chest while leaving tools, torches and saplings untouched. Reservation removal is tested by breaking and replacing a Totem, checking that chest contents survive and the same site can be configured again. Waiting miners physically return to the main Pedestal.

After the final container-clearing adjustment, **all 21 worksite tests passed again** against the final build. Structural obstacles include a stonecutter, obsidian, a chest containing seven diamonds, and a barrel blocking the upper door. Protection and unbreakable blocks are preserved. Real block drops follow harvest-tool rules; container contents are retained in cargo or as world items.

The infuser model has zero same-facing coplanar overlaps and complete socket-top coverage. A server geometry test compares its solid model union with the actual collision shape. The exact supplied 16x16 coal and charcoal PNGs are packaged without resizing or palette changes. Native shard artwork, ore textures and ore models remain byte-identical to 0.7.3. Asset and handbook validators and packaged-resource checks accompany this release.

No new in-game visual acceptance session was performed, and arbitrary third-party inventory/protection compatibility is not certified. Logs, source archives, packaged and installed hashes, and the installation receipt are retained in the release library.

---

# Verification - 0.7.3, 25 October 2026

The supplied raw and infused shard PNGs are exactly 16x16. Their source, preserved reference, resource and packaged-JAR bytes match. SHA-256: raw `445d5201ebcc29fbe83cd6664d09a89e19965214506af707d8c4026e23743b82`; infused `f76c96c5c34ab128e444015770ce039e307ec320d1d96fb0c969c410934527f4`.

Only the two shard PNGs and version metadata differ in the JAR from 0.7.2. Java classes, block models, ore PNGs, other artwork and gameplay data are unchanged. The asset and handbook validators pass; the normal build succeeds. The unchanged gameplay classes passed 153 required GameTests in 0.7.0; those tests were not rerun for a direct artwork replacement.

Minecraft was not launched. The user-supplied native images are the selected artwork; no generation, resizing or palette edits were applied. Source/reference hashes and installed JAR hashes are recorded with this release, and the previous installed JAR is backed up.

---

# Verification - 0.7.2, 25 October 2026

The packaged change is limited to three native 16x16 PNGs and version metadata. Block models, Java classes, infused shard, and all gameplay data remain byte-identical to 0.7.1. The same gameplay code passed all 153 required GameTests in 0.7.0; that server suite was not repeated for this palette adjustment.

Lumite receives a 12% RGB increase only on visible purple mineral pixels. Dark recess pixels, stone, and the painted rim are unchanged. The raw shard's 28 interior pixels become very pale purple, while all edge pixels and alpha values match the previous sprite. The 18-pixel ore glow selection is unchanged. Asset validation and a normal build pass; packaged resource hashes match the selected artwork.

Native and enlarged previews were shown before any game launch. Minecraft was not opened for this patch. The visual review is of the PNGs, not a new in-game acceptance session. Original cube geometry and existing flat emissive treatment remain as verified for 0.7.1.

---

# Verification - 0.7.1, 25 October 2026

## Flat Lumite texture revision

This patch changes the two ore PNGs, matching flat emissive-pixel maps, and version metadata in the JAR. Every Java class and gameplay data file is byte-identical to 0.7.0, whose 153 required server GameTests passed. The server suite was not repeated for this artwork-only change.

The user rejected the experimental recessed mesh. The original 0.7.0 model generator and full cube are restored. Each ore has one original cube element and 108 flat glow faces (18 pixels on six sides), with the same tiny anti-flicker offset used previously. There are no geometric cavities, bevels or rim walls. Apparent depth and the chipped rim are painted pixels.

Both native textures are opaque 16x16 PNGs. Supplied backgrounds match Minecraft 1.20.1 stone/deepslate pixel-for-pixel. Each finished PNG retains 148 untouched outer background pixels, shades 49 existing rock texels into a chipped rim, and inserts 59 interior pixels. The 18-pixel glow mask covers only Lumite. Enlarged previews are exact nearest-neighbor copies. Asset/handbook validators pass, and the final build packages the checked images and models.

Minecraft was not launched again after restoring flat artwork. Final textures were reviewed as native and enlarged PNGs; earlier game captures show rejected geometry and are not acceptance evidence for this release. The user requires image previews before future Minecraft launches. No custom resource-pack or multiplayer review was performed for this texture patch. Alternate resource packs would need corresponding ore PNGs.

Versioned source/JAR hashes, native previews, build/asset reports and the installer receipt are retained with 0.7.1. Original ore art and the verified gameplay release remain archived under 0.7.0.

---

# Verification — 0.7.0, 24 October 2026



## Current run and builder fixes



Java 17, Minecraft 1.20.1 and Forge 47.4.10. `current-tests-3.log` passed **all 153 required server GameTests** and completed a successful build. The final packaged release contains 212 verified resources, including every guide asset. Asset validation passed 125 JSON files and 42 correctly sized game textures. No GuideME/JER/JEI classes are shaded into the JAR.



The four construction regressions complete a real shed from an empty foundation and from workers initially standing on a chest, stonecutter or crafting table. Placements occur with the worker outside the footprint, including upper walls/roof. These use actual supplies and assert blueprint completion. Existing construction ownership, partial-supply, protection and handover cases remain in the passing suite. A real route and supported outside footing are still needed; supply collection continues to use physical reach.



Fuel checks cover all four exact 8:1 conversions, separate coal/charcoal identities, mixed-input rejection, whole-coal tag isolation, and one-eighth parent fuel time. Six actual furnace/blast-furnace/smoker instances each smelt exactly one item from one piece. Felling tests cover the first three rooted trunk levels, one initiating axe wear, exact log totals including the lower stump, repeated event delivery, fourth-level/branch/placed-log rejection, and worker occupants during initiation/fall/landing. The broader suite retains grown sapling species (including 2x2 spruce/jungle), claims, protected trees, leaves, drops and multi-worker regressions. No new damage mechanics were added.



## Ore and saved worlds



The shared configured size changed from the verified baseline 6 to 8. Eight attempts, triangular Y=-32..64 placement and Overworld eligibility remain. Actual blocks from the same 12 chunk coordinates in each seed were compared in saved Anvil data:



| Seed | Size 6 ore | Size 8 ore | Size 6 clusters | Size 8 clusters |

| --- | ---: | ---: | ---: | ---: |

| 4217 | 299 | 382 | 123 | 91 |

| 8675309 | 285 | 431 | 106 | 95 |

| -60241024 | 327 | 479 | 132 | 115 |

| Total | 911 | 1,292 | 361 | 301 |



Clusters are face-connected within a sampled chunk; boundaries can split a cluster. These are observations, not an eight-block guarantee or fixed abundance multiplier. Both ore forms occur within the height bounds; sampled Nether/End chunks contain none.



A disposable copy of the original size-6 world loaded with pending legacy history under the final server code. All 12 sampled chunks retained exactly their block-state hashes and ore positions. The entire history NBT, including unknown fields, was preserved; no retrogen job ran or loading ticket was acquired. Automatons commands and suggestions stayed absent after startup and a normal reload, while vanilla give/reload remained. No player world was regenerated. Detailed evidence is in `current-comparison.json` and `current-server-final.log`.



## Packaged client and handbook



GuideME **20.1.15** is required on both sides. JER **1.4.0.247** and JEI **15.56.0.205** were tested together, and the packaged client also passed with JER absent. The full installed Rackworks mod set was used in isolated Lab copies; tests did not alter its live save or options.



The client validates **29 compiled pages, 17 live recipe displays, 20 unique item associations and seven vanilla structure scenes**, with no guide compiler errors. Four genuine GUI screenshots are bundled. Server-side book use opens the guide without consuming it. The test checks a single registered guide, page navigation, search for torches, a rebound H-key tooltip, GUI-scale settings 2/3/4 at the test window size, the real assembly recipe and scenes. A normal server resource reload followed by client reopening again validates all recipe layouts. Structures reproduce the five worksite blueprints; scene-only item-frame omissions are disclosed in the book.



JER resolves both stone and deepslate ore searches to exactly one shared native entry. The actual graph and its visible **Default estimate** label were inspected; its tooltip reports bounds, frequency, size, tier, Fortune and Silk Touch. The release's broken annotation scanner required a guarded public API singleton fallback. See `reference/current-run/jer-integration-notes.md` for the artifact/source discrepancy. A public JEI decorator labels the native graph; no separate guessed graph or reward path is added. Candidate placements come from the bundled generation definition and algorithm, before terrain filtering. Remote-server data-pack overrides are not synchronized by JER's startup API; its own selected DIY/profile mode takes precedence.



The supplied ore PNGs match their source files byte-for-byte. All 40 preexisting game PNGs also match the 0.6.0 archive; all 42 remain byte-identical after the current generator runs. The final sixteen-image Lumina client review includes daylight/zero-light ore, inventory rendering, the existing Hammer and four-corner infusion. Zero block light and zero sky light were recorded for the dark chamber; four completed shards consume exactly 100 Lumina in the existing animation review.



## Scope decisions and unavailable checks



Runningtons retain the user's explicit restriction against taking tools, torches or saplings from chests. The handbook describes that live behavior, despite the later brief's conflicting fetching-tools example. Builders stay outside when placing, as requested in the latest message. The collector remains a recorded design checkpoint with no invented rates, capacities, recipe or implementation.



Physical keyboard shortcut activation was not simulated: GuideME polls GLFW key state directly, so item indexing and the rebound tooltip were verified instead. GuideME 20.1.15 fixes `currentLightDarkMode()` to its light mode and exposes no dark-mode toggle; a second theme could not be tested. Server-side packet opening and synchronized recipes were exercised in an integrated client, and dedicated-server startup passed, but no separate multiplayer-client acceptance session was performed. Arbitrary third-party fuel machines and claim plugins are not certified. These limits are not reported as passing tests.



Versioned manifests record the exact JAR/source hashes, review directories, installation receipt and previous-JAR backup. Failed intermediate runs remain diagnostic evidence; the release uses the passing runs identified above.



---



# Historical 0.6.0 verification (superseded commands)





## Lumina milestone



The release targets Java 17, Minecraft 1.20.1 and Forge 47.4.10. The final dedicated-server regression run, `lumina-tests-verified.log`, passed **all 143 required GameTests** and finished with a successful build. The subsequent retrogen integration run verifies the final existing-chunk read-ticket correction. No retrogen command was invoked on a live user world.



The server checks cover exact fractional balances; one through four corners sharing the original throughput; 25-point shard costs and 100-point batches; independent donors and bystanders; range/disconnect/empty-balance pauses; physical corner interaction; full-inventory off-hand retrieval; persistent nonstackable partial items; normal completed-item stacking; break payout; legacy credit recovery and repeated migration; every inert upgrade family/tier and unused plate; rejected installation and zero GUI slots; actual tool enchantments; recipe bootstrap and exact shaped Core crafting; and dedicated-server loading without JEI.



Loot checks exercise both variants with 600 ordinary rolls each, Fortune, Silk Touch precedence, wrong-tier rejection and zero emitted block light. Real Minington work harvests both variants with Silk Touch/Fortune-equipped tools and asserts matching ore drops with one wear charge per block. Real survival player mining measured **23 ticks with a wooden pickaxe versus 90 with the Hammer** on the same stone. Hammer tests also cover Haste/Mining Fatigue, positive progress, one durability charge, 250 capacity, unbreakable blocks, and no item/XP drops from iron, deepslate iron and both Lumite variants.



### Normal ore generation



Three real generated worlds sampled twelve Overworld chunks apiece:



| Seed | Ore blocks | Ore blocks per sampled chunk |

| --- | ---: | ---: |

| 4217 | 299 | 24.92 |

| 8675309 | 285 | 23.75 |

| -60241024 | 327 | 27.25 |



Across **36 chunks**, the total was **911 blocks: 664 regular and 247 deepslate**, averaging **25.31** per chunk. Actual counts in the Y bands -32..-17, -16..-1, 0..15, 16..31, 32..47, 48..63 and 64 were **55, 171, 237, 291, 109, 48 and 0**. Every observed block was within the requested bounds. Fourteen biome labels were sampled at Y=64 across the terrain; that coordinate is not necessarily the surface. One Nether and one End chunk per seed contained no Lumite. These samples measure initial balance, not guaranteed discovery times. A separate 1,200-chunk deterministic candidate check validates clipping and distribution independently of available terrain.



### Existing-world retrogen



The disposable existing-world copy contained **3,402 saved chunk coordinates**, including 1,234 FULL chunks and 2,168 partially generated chunks. The final job result was: **Lumite regen: completed; 1232 processed, 2170 excluded, 3402 scoped**. Its 1,232 eligible FULL chunks include locations originally unloaded and beyond spawn loading. The two additional exclusions were the normal-generation marker and configured rectangle fixtures.



The job was explicitly paused after its first completed chunks, shut down, resumed, and later interrupted while running. The final process reopened the same job ID, reconciled durable chunk receipts, completed, and refused a second start both immediately and after another server restart. A previously mined completed-chunk location remained free of ore; water from the adjacent fixture flowed into it. Player-tracked stone, reservations, a container, existing ore, air and water remained protected. Scope and generation parameters survived interruption.



The offline region audit after final shutdown found **3,402 coordinates before and after, zero added or missing coordinates, and zero generation-stage changes**. This audit is stronger than observing only new FULL chunk events. An early diagnostic run exposed ordinary FULL tickets advancing neighboring terrain; that failed copy and its evidence are retained. A final assertion initially required that mined hole to remain air; offline inspection confirmed ordinary water flow from the adjacent protected source, and the assertion now accepts air or water while rejecting every solid refill. The corrected implementation requests only the saved chunk at EMPTY status and uses the exact status ticket level. A second diagnostic found that the weaker maximum ticket level stalled fully unloaded reads; the interrupted job was resumed successfully with the corrected level. Processing owns at most one ticket and considers at most 96 candidate positions per tick, with receipts flushed alongside block data before aggregate progress advances.



The retrogen server used Forge plus Automatons against a disposable copy of the prior packaged Lab world. Expected missing optional-mod registry/structure warnings are retained. This does not certify arbitrary third-party claims or every mod's saved data. Old untracked player stone cannot be universally distinguished from natural stone; the player guide documents backups and explicit exclusions.



### Packaged client and assets



The final reobfuscated JAR completed the isolated Rackworks Tester mod-set review with exit **0**, using `build/packaged-smoke/20261024-055823`. The original instance and gameplay saves were untouched during review. All **16 captures** were inspected: daylight adjacent ores; an enclosed zero-light culling scene; native and enlarged inventory items; the held and framed Hammer; one, two, three and four prongs; real paid transfer; out-of-range pause; green completion; the reflowed Totem; and JEI infusion. The client logged `blockLight=0 skyLight=0` and four completed infused shards with zero thirds remaining from exactly 100 Lumina.



The actual installed mod set includes JEI, Jade, JourneyMap, WorldEdit, Mekanism, Thermal, AE2, Rackworks and associated dependencies. No shader pack or optional renderer was required. Veins stay bright while rock remains shaded, with opaque foreground blocks hiding covered ore. Native 16×16 Hammer art and a nearest-neighbor enlarged preview were also inspected. The generator regression retained identical resources after rerunning the relevant authors. Asset validation passed **111 JSON resources and 40 textures**; `git diff --check` passed.



Reviewed JAR SHA-256: `295A47E9A3CCB0DD545966FAB91EE829BE62B702F74304E2F1BB5DFF90273CE1`. The versioned release manifest records the final installed hash, prior-release backup, complete source snapshot and evidence paths. All upgrades and the unused Upgrade Plate were removed from active progression; compatibility registrations remain inert.



### Reproduction and limits



Run `tools/test_release.ps1` for the server regression suite and build, `python tools/validate_assets.py` for resources, and `python tools/instance_smoke.py --isolated --lumina-review` for the packaged visual review. `tools/prepare_lumina_worlds.py` prepares disposable world scenarios; the `luminaWorld` / `luminaWorldMode` Gradle properties select them. `tools/audit_lumina_regions.py` performs the read-only region audit. Exact reports, failed diagnostics, successful logs and reviewed captures are archived with 0.6.0.



There was no multiple-human-client acceptance session, arbitrary claim-plugin certification, subjective audio review or power-loss/disk-failure durability certification. Multiplayer ownership and competing interaction rules are server exercised with test players. The interrupted retrogen process and subsequent restart were tested; that evidence is not a universal filesystem crash guarantee. Historical verification below describes superseded behavior and releases.



# Historical verification — through 0.5.0



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





## 0.3.0 Miningtons, names, timber and client verification



The existing 73-test baseline passed before this update. The final fresh dedicated-server run, `release-0.3.0-handoff-tests.log`, passed **all 96 required GameTests**, without JEI on the server. It includes the original regression coverage and 23 new checks for mining, names, Find and physical timber collision. The preceding 94-test run and build are retained as `release-0.3.0-final-tests.log`; the two final additions affect only test code.



Actual worker checks cover full arch/shaft/chamber construction, physical ladder descent and closed-trapdoor recall with cargo, two complete 32-column tunnel legs and their turn, second-lane return/resumption, an 18-layer quarry followed by return and resumption through layer 20, and mode switching while preserving both routes and one entrance. Two competing miners wait neutrally for one builder; after that builder unloads during construction and the mine journal is reloaded, its colleague completes the same entrance and descends to mine. A reachable damaged ladder is replaced using one carried ladder, and recall climbs the repaired access. Geometry checks include all four cardinal directions and negative coordinates.



Torch checks exercise shortage recovery from an alternate chest with only one torch available, actual block-light coverage, removed-light repair, and replacement-before-recovery of an excavation-conflicting torch across a mine-journal reload. Ore tests check a denied Forge break without loot or wear, then Silk Touch/Fortune results, one durability action, ordinary XP and no Magica. Measured unenchanted stone work is **15/10/10 work ticks** for stone/iron/diamond picks; travel and scheduling are separate. These tests observe normal entity movement and block/inventory changes, rather than advancing an abstract progress counter alone.



Names checks validate all **640 entries, 32 groups, 320 masculine selections and 320 feminine selections**, strict UTF-8 and canonical uniqueness. They exhaust all base names, allocate 25 additional unique compounds and exercise numeric exhaustion with a reduced pool, including repeated persistence. Tests cover preview/failed-transfer nonallocation, committed crafts at two homes, permanent old-name reservations, deterministic legacy conflicts, late legacy packed items, name-tag cost/rejection, packing and redeployment, job retention, owner-only menus, distinct home toggles on a mixed-job Totem, saved home/entity state and cross-dimension propagation on synchronization. No independent multi-human multiplayer client session was run.



The final isolated packaged-client review used `build/packaged-smoke/20261022-222731`, the designated Rackworks Tester mod set, Forge 47.4.10 and the project Lab save. The actual client loaded **0.3.0**, completed `AUTOMATONS_MINING_VISUAL_COMPLETE` and exited **0**. The helper restores its copied pre-test JAR afterward; the 0.2.4 JAR subsequently present inside that isolated directory is the restored copy, not the reviewed version. User saves and the live instance were not used for the review.



Reviewed captures show mixed-job personal-name rows, accented and long names, ellipsis in narrow rows, worker details/Find at GUI scales 3 and 4 and a 960×600 window, Mine/Forestry panels and supplies, the pedestal ON/OFF toggle, labels hidden while names remain in menus, amber mining outlines through terrain, Find glow, torch-lit mining, and the larger face with a visible mouth. `face-portrait` is the frontal art reference; the initial `wooden-face` capture caught the loading screen and is excluded from the release screenshots. The long overhead label uses ordinary vanilla clipping/occlusion when viewed through the arch, with no duplicate mod label.



The client collision probe reports **`collisions=2 blocked=true smoothSamples=106 finalProgress=80.0`**. It used actual player movement against landed Lumberton logs and checked monotonically advancing fall samples. Server collision checks also passed. This addresses the previously missing client multipart registration; it does not claim an FPS or network-latency benchmark.



Asset validation passed **102 JSON resources, 35 texture dimensions, the 26-unit rig and all block-face UV density checks**. `git diff --check` passed. The complete source, imported arch, bundled name catalog, editable art, logs and selected actual captures are archived with this release. Reproduce the suite with `tools/test_release.ps1` and the client with `python tools/instance_smoke.py --isolated --mining-review`.



Test limits: there was no exhaustive seed/mod/claim-handler sweep, multi-human network test, or physical restart at every possible mining sub-step. Save/load coverage uses the actual NBT readers/writers and isolated worker/mine fixtures. An unreachable broken access segment, blocked structure, unbreakable tread, unsafe fluid/falling material or denied placement can require player repair; no chunks are force-loaded. Additional miners do not multiply a single narrow frontier's throughput. Previously unindexed legacy items are reconciled when encountered, without scanning arbitrary unloaded or third-party inventories. See `MININGTON.md` and `LIMITS.md`.





## 0.3.0 deployment and archive



Installed **automatons-0.3.0.jar**, **495,867 bytes**, SHA-256 **BC02CAE4F446278C24FEA85A2FCFC6F270F72E3433EDE18C9A48E2AD2501BFEF** in Rackworks Tester after the installer verified that its game process was closed. Exactly one Automatons JAR is installed. The previous 0.2.4 JAR is preserved in `C:\Users\impsh\curseforge\minecraft\Instances\Rackworks Tester\.automatons-release-backups\20261022-224246-972` and its original hash was verified. Configuration and saved-world files were left intact.



The final release adds two passing GameTests after the client review. All **228 production classes/resources** are byte-identical to the reviewed build; only GameTest classes and the generated manifest build timestamp differ. The final server run and reobfuscated build succeeded in 2m23s. The release manifest identifies the base commit and a hash of the complete working-tree source archive, rather than claiming that uncommitted release changes belong to the baseline commit.



The distributable, complete source archive, imported structure data, bundled names, editable model/textures, documentation, final logs, install receipt and 23 selected actual client captures are preserved under `F:\Minecraft\Automatons`, with prior releases retained. See `releases/0.3.0-manifest.json`, `verification/0.3.0` and `screenshots/0.3.0` in that library.





## 0.3.1 server verification — 23 October 2026



`mining-0.3.1-final-tests.log` records **all 101 required GameTests passed**, followed by a successful reobfuscated release build. The prior 96 tests remain, with five focused additions; some existing mining fixtures now exercise the twenty-block platform. The final runtime uses protocol 4 on client and server.



The full-construction test initially supplies only three chests, verifies that no entrance is reserved, then adds the fourth. The real worker builds the arch, all twenty ladders, platform and four chest blocks, and reaches its mining face. Each chest pair exposes 54 usable slots. The hauling test fills the first double chest, fills the worker with 512 cobblestone, and verifies delivery to the second pair without changing the surface chest or climbing out. Mine depot identity/positions persist through the actual NBT reader/writer.



The falling-material test excavates four gravel blocks from outside a two-block horizontal exclusion around the column. It requires no health loss, exact gravel-plus-flint conservation and no remaining falling entity before the frontier advances. Separate water and lava fixtures leave the source and containing stone intact, observe the worker return to its platform, and require continued excavation in a different direction with the same mine identity. The rejected route and redirection state survive NBT reconstruction.



Timing tests compare the mining formula directly with Minecraft's grounded-player destroy-progress calculation for all six vanilla pickaxe tiers at Efficiency 0, 1 and 3. Actual worker measurements are **12, 8 and 6 work ticks** for unenchanted stone, iron and diamond picks breaking stone. Shared Speed I and Efficiency II combinations are asserted as well. Quarry fixtures physically descend from the twenty-block platform through multiple stair turns, recall to the surface after layer 18 and resume through layer 20. Legacy data without a shaft-depth key retains its five-block depth and enters depot construction instead of moving its existing mine.



The initial diagnostic run failed the obsolete 15/10/10 timing expectation. Subsequent gravel testing found that a frontier could advance before a newly unsupported block settled; the final code holds the same target until the falling column is clear. Navigation now chooses safe positions with a margin beyond the exclusion distance and centers the worker there. Final passing evidence is retained separately from these diagnostic runs. Asset validation passed 102 JSON resources, 35 textures, the 26-unit rig and proportional block-face UVs; `git diff --check` passed.



Coverage boundaries: these are isolated server fixtures, with actual movement, block changes, normal loot and inventory handlers. They do not certify arbitrary third-party protection/storage implementations or every possible terrain arrangement. A fluid in an unfinished shaft/platform, no reachable safe stance for falling material, all forward/side directions blocked, or unreachable damaged access can require player intervention. Existing mine depth is preserved deliberately. Runnington transport is not implemented in this release.





## 0.3.1 client and deployment



The isolated packaged client used `build/packaged-smoke/20261023-053818`, loaded the release JAR with SHA-256 **DD98A896FE71E65BC7BD4F20EEF95413E07CCA9D7E2425CEE15D32B5BD8B0C92**, completed the review and exited **0**. Its server-side checkpoint reports **`AUTOMATONS_DEPOT depth=20 built=true chests=4`**. The actual tick-540 capture shows both connected double chests in the side alcoves, the central ladder, clear space above the chest lids and the worker. The Mine panel shows the twenty-block platform and four-chest requirement with a scrollable information area. The later tick-620 camera was obstructed by the close worker; it is not the chest-layout evidence.



The same run retained the timber regression check: **`collisions=2 blocked=true smoothSamples=106 finalProgress=80.0`**. It also completed the existing names, Find and menu review. The helper used the project Lab save and a copied mod/config set, then restored that isolated copy's old JAR. The user’s live saves were not used for this test. The actual viewed captures and full client logs are archived in the versioned library folders.



Installed **automatons-0.3.1.jar**, **508,476 bytes**, with matching build/client-review/installed SHA-256 **DD98A896FE71E65BC7BD4F20EEF95413E07CCA9D7E2425CEE15D32B5BD8B0C92** after the installer confirmed the designated instance was closed. The previous 0.3.0 JAR is preserved in `C:\Users\impsh\curseforge\minecraft\Instances\Rackworks Tester\.automatons-release-backups\20261023-054228-924`, with its original hash verified. Exactly one Automatons JAR is installed; saved worlds and custom configuration files were not edited. The complete working-tree source archive, current guides, structure data, editable assets, final and diagnostic logs, installer and release manifest are preserved under `F:\Minecraft\Automatons`. Previous versioned releases remain available.





## 0.4.0 server and asset verification



`runnington-tests-16.log`: **all 113 required GameTests passed**, followed by a successful release build on Java 17 / Forge 47.4.10. This includes the prior 101 regressions and twelve Runnington cases: dynamic eighteen-slot inventory/capability and metadata-preserving shrink; imported real double chests/anchors; two shared builders with serialized construction and worker unload/reload; limited tool supply through a one-block L corridor and wooden door; canceled manual resupply returning its real tool; actual twenty-block depot descent/collection/recall/return; source and destination reservations; north-facing warehouse exit from the reproduced off-center stop; blocked/protected sites and owner/home isolation; all eighteen packed cargo drops and identity-preserving redeployment; full/changed warehouse storage with worker/network reload and partial recovery; and physical torch delivery followed by actual mining and shortage clearance.



The full-storage test starts with 128 real ore items, fills all eight warehouse doubles after pickup, reloads the carrying worker and queue, opens exactly one compatible slot unit, verifies only one item was delivered, then opens further space and verifies the remaining 127 arrive. The player's 63 preexisting ore plus the 128 collected items total 191, with the source emptied and cargo empty. No inventory is synthesized from telemetry.



Prior successful runs were `runnington-tests-8.log`, `runnington-tests-9.log`, `runnington-tests-10.log`, `runnington-tests-11.log`, `runnington-tests-14.log` and `runnington-tests-15.log`. A warehouse-door waypoint was added before longer surface delivery routes; the full suite passed again after this fix. Earlier diagnostic runs are preserved: failures exposed a Java test declaration error, mutable ItemStack transfer-count accounting, unsafe placement footing, resumed builders blocking construction, and premature handoff fallback despite forward travel. Those failures are not counted as passing checks.



`python tools/validate_assets.py`: **104 JSON assets, 36 texture dimensions, the 26-unit rig and proportional block UVs passed**. A full `author_assets.py` run in a separate staging directory reproduced all seven expression atlases, editable JSON/Blockbench geometry, packed/head/display assets, runtime rig (ignoring formatting), and translations. `import_buildings.py` reproduced both runtime templates from the archived reference schematics and verified both complete bills of materials. It can regenerate from the source archive without desktop attachment directories. Packaged blueprint/texture/language bytes match source resources.



Current-to-result dimensions: head **8×8×8 → 9×8×8**; torso **8×9×6 → 9×9×6**; open eye width **1→3 source pixels**; height **26 pixels / 1.625 blocks unchanged**; collision **0.7×1.625 blocks unchanged**; eye height **1.4 unchanged**. The source atlas grows 64×64→128×128 to fit gear, retaining one texel per model unit. There is no global entity shrink. Default cargo is eighteen plus three equipment slots for Runnington, nine plus three for other jobs.



Server physical passage/door and mine-ladder checks verify collision/navigation, not a guarantee that every animated limb or held tool remains within the collision box. Entity unload/reload fixtures use the normal UNLOADED_TO_CHUNK removal lifecycle and NBT reconstruction in a loaded test arena; arbitrary live region unload timing, multiple human clients, third-party claim/inventory implementations and operating-system crash atomicity are not certified by these checks.



A further packaged run exposed terrain-scan starvation with the Lab world’s older 512-slot terrain budget. Logistics now uses a separate configurable 512-slot budget per home reconciliation, rotates containers, and retains loaded snapshots between samples while rechecking real inventories at transfers. The reservation test explicitly exhausts the terrain budget before requesting warehouse stock. The existing custom terrain settings are retained.



Overlapping Totem ranges now resolve to one canonical container report per dimension, with local supply aliases preserved. Warehouse classification takes precedence over ordinary output discovery. Source quantities and destination capacity share the same physical-container reservations even when accessed through different Totems. A dedicated move controller centers runners in the free part of an open wooden doorway while preserving the 0.7-block collision width.



The final 113-test run also registers all eight warehouse doubles through a second upgraded Totem, verifies nine unique loaded reports including the external source chest, preserves warehouse classification and local aliases, and confirms the second Totem cannot bypass the one-item destination reservation.



A packaged north-door stop was reproduced in a focused server regression. Tracing showed that the short exit path was marked complete while the worker still occupied the door cell. The warehouse exit waypoint now allows enough distance for navigation arrival tolerance; nearby wooden warehouse doors open along the active path. Failed diagnostic runs 12 and 13 are retained, with the path index and movement target recorded.



The next client run exposed an additional feet/body boundary: the feet left the warehouse cell before the collision box fully cleared the doorway. The local exit route now remains active while the body intersects the door cell. The north-door regression starts at that observed partial-exit position and requires the local exit waypoint plus physical delivery.



Route diagnostics then showed a valid 41-node partial path ending one block short of the chosen handoff candidates. Couriers now follow a bounded partial route only when its endpoint makes measurable progress, and replan from there. Real handoff range and line of sight remain mandatory. The north-door regression constrains follow range to eight blocks to require partial-route progress before delivery. Other jobs retain their prior complete-path rule.



## 0.4.0 packaged client and installation



`runnington-client-9.log` and `instance-smoke.log`: the isolated packaged client using the Rackworks Tester mod set completed its visual and physical scenarios, saved normally and exited **0**. The final client directory is `build/packaged-smoke/20261023-085609`. The required marker records **door=true, corner=true, physicalHandoff=true**, with width **0.7** and height **1.625**. The runner collected a real axe from the warehouse, exited its north-facing doorway, traversed the narrow L passage and another wooden door, then put that axe into the recipient's tool inventory within interaction range. Stock reports remained nine unique containers rather than seventeen duplicated Totem reports.



Sixteen actual screenshots cover all four job appearances, the mouthless face, backpack, ground-level covered minehead, warehouse exterior/interior and chest access, the eighteen-slot cargo menu, Logistics panel, packed-item rendering, corridor door/corner and final handoff. Selected final captures and the preceding equivalent menu/geometry captures were visually inspected. The corridor camera has wall occlusion and is not a certification of every animated limb/tool pose; physical passage is additionally asserted by the runtime scenario and server tests. Multiple human clients and a hand-operated acceptance session were not exercised.



Installed **automatons-0.4.0.jar**, **622,688 bytes**, SHA-256 **A1BCBF36359B0B843979E13F059817C530C71B5D61FC19AB6518FEE09424692B**, in Rackworks Tester. The installed file matches the reviewed build. The previous 0.3.1 JAR is preserved at `C:\Users\impsh\curseforge\minecraft\Instances\Rackworks Tester\.automatons-release-backups\20261023-085915-330` and exactly one Automatons JAR remains in `mods`. No live saved-world or custom configuration data was edited. Source snapshot, editable assets, documentation, screenshots and successful/diagnostic logs are archived under `F:\Minecraft\Automatons`.





## 0.4.1 construction, mining continuation and replanting



The expanded suite has 119 tests. New coverage includes the replacement warehouse on grass, a supply chest inside its footprint, preservation of named leftover items, live material counts, legacy blueprint preservation, repeated 512-item underground unloading, return to the same face after a surface delivery, pause/resume without a surface trip, a second miner joining completed access, quarry starts outside the platform, and physical planting with exact consumption of a named sapling.



Regression runs exposed two mine-access bugs: a stale deferred-path flag prevented a fresh storage search when cargo was empty, and the worker's body overlapped the ladder plane at the open entrance. Resetting the search state and offsetting the climb center resolved the affected construction, continued mining and courier-depot tests. Replanting was moved after trunk processing so a sapling cannot obstruct the remaining fallen sections. Forestry gives renewed discovery a bounded wait before unloading when repeated terrain changes prevent a complete scan.



The packaged client uses a separate game directory and a copy of the designated mod set. Captures cover profession selection and confirmation, the build list, profession settings, Totem tabs, the lower pedestal Magica section, the corrected floating model texture, the new warehouse and yellow outline. Review moved the boundary outside the walls and excludes modal popups from JEI's item overlay. The courier scenario also checks a real wooden door, corner and physical tool handoff. Failed diagnostic logs remain with release evidence.





`construction-tests-7.log`: **all 119 required GameTests passed**, followed by successful build completion. `construction-build-final.log` records the subsequent rebuild after a change only to the opt-in visual test observer. `tools/validate_assets.py` passed **105 JSON resources, 36 textures, rig bounds and block UV checks**. No gameplay code changed after the passing server run.



`construction-client-3.log` and `instance-smoke.log`: packaged client exited **0**, with **door=true, corner=true, physicalHandoff=true** and `AUTOMATONS_RUNNING_VISUAL_COMPLETE`. The final client directory is `build/packaged-smoke/20261023-142440`. The earlier observer sampled client frames and missed a door transition; observing every server tick recorded the real lower-half `open=true` state. Popup, outline, profession list, pedestal and material-table captures were visually reviewed. This was an automated isolated client session, not a multiplayer or human acceptance session.



Installed **automatons-0.4.1.jar**, **648,918 bytes**, SHA-256 **6D982EF9B768DCABDA486E48FDEB875F00B302246531288D964D6A719275A3EE**. Rackworks Tester contains exactly this one Automatons JAR. Its previous 0.4.0 build is preserved under `.automatons-release-backups/20261023-142808-884`. The installer checked that no live instance was running; no gameplay save or custom configuration was edited. Versioned source, JAR, guides, screenshots and successful/diagnostic logs are archived under `F:/Minecraft/Automatons`.



## 0.4.2 forestry, independent tunnels and protected chest supplies



The expanded suite has 123 server tests. New scenarios require a real Lumberton to fetch and plant a sapling from storage, climb to a log nineteen blocks above its ground using carried dirt, survive a journal reload during the climb, descend and recover all 32 supplied dirt blocks. Courier checks preserve tools, all three torch types and saplings in both warehouse and pickup chests while requests remain visible. The underground pickup scenario now also covers a warehouse and mine sharing one Totem. Existing multi-miner checks require separate saved tunnel faces and progress after reload.



Failed diagnostic runs reproduced climbing reserve shortages, storage-path checks interrupting a pillar climb, expensive tree-scan restarts after each terrain mutation, and tunnel-return failures. Forestry now retains a full stack of suitable supports, preserves unrelated discoveries, and bounds heightmap-based skipping. Normal unloading still has a finite wait when discovery remains incomplete. A physical travel timeout previously treated any journey over eight seconds as blocked; it now measures lack of movement. Local light repairs also stay within the current tunnel corridor so a nearby light behind a wall cannot repeatedly pull a miner off its return route.



The unchanged player-tool timing check verifies block-breaking durations. In the deterministic deep-quarry fixture, reaching eighteen layers dropped from approximately 3500 to 2300 ticks across the recorded diagnostic runs, mainly by lighting persistent stairs rather than temporary floors. This is a fixture measurement, not a promise for every world or tool. Quarry geometry remains pinned when miners switch tunnel directions.



The packaged client scenario checks that an Unassigned worker applies its first job without a confirmation popup, while an existing job change still shows the warning. It captures the enlarged miner helmet, Logistics worker activity/requests and pedestal without its Recipes link. Its physical courier passage starts with an already-carried parcel: new chest withdrawals of tools are deliberately forbidden in this release.



`update042-tests-6.log`: **all 123 required server tests passed**, followed by **BUILD SUCCESSFUL**. `update042-assets.log`: **105 JSON resources, 36 texture dimensions, 27-pixel miner height / 26-pixel body and block UV checks passed**. No gameplay or resource edits followed this build.



`update042-client-1.log`: isolated packaged client exited **0**, with **door=true, corner=true, physicalHandoff=true**, the first-job-without-confirmation success marker and visual completion marker. The review directory is `build/packaged-smoke/20261023-161911` with **24 captures**. The miner, Logistics, existing-job warning, first-job and pedestal views were inspected. The first-job capture occurs on the update boundary; its rendered text still shows the preceding frame, while the synchronized menu assertion verifies Job=Lumberton and no popup. This remains an automated isolated client session, not a multiplayer or human acceptance session.



Installed **automatons-0.4.2.jar**, **668,873 bytes**, SHA-256 **F64E206D6620E60B4CD3421B60C4AA4CCE3927B378EFF0AACF33AB45891EE37F**, matching the reviewed build. Exactly one Automatons JAR remains in Rackworks Tester. The previous 0.4.1 JAR and configuration backups are in `.automatons-release-backups/20261023-162417-413`. The installer upgraded only `rootHeight=16` to **32** in the three existing New World server configurations, retaining backups and all other settings. Gameplay save data was not edited. The source archive, versioned JAR, editable assets, guides and successful/diagnostic evidence are preserved in `F:/Minecraft/Automatons`.





## 0.5.0 The Network, construction and shared farms



The milestone extends the existing world journal and worker Ledger rather than adding a parallel identity system. The new worksite suite covers exact five-template material lists, all four rotations (20 placed fixtures), preservation of the live Totem, usable empty double chests, supported doors and identification frames, and no mine hatch. All five edited Sponge exports were decoded and compared cell-for-cell, frame-for-frame, anchor-for-anchor and material-for-material with packaged JSON. Totals are Builder 257, Farm 243, Lumber 203, Warehouse 236 and Minehead 152 placed items, excluding the live Totem.



A real Buildington builds its first shed from physical stock and Hammers without a Runnington. A separate partial-kit run verifies progress without abandoning the project or entering a permanent door fault. Save/load tests retain the project identity and exclusive builder claim, and legitimate release advances ownership. Existing underground construction, mining continuation, independent tunnels, courier depot pickup and recall checks remain part of the complete suite.



Farm checks exercise existing irregular farmland and two physical Farmingtons preparing one 80-cell shared field. They verify one consumed water bucket and one retained empty bucket, 80 total hoe wear, physical planting, disabled bone meal remaining untouched, then enabled local bone-meal restocking and use. A stale courier parcel is rechecked after the field grows from 8 to 80 cells: only the 16 surplus carrots can leave a stock of 96. One-range checks cover every Totem purpose, inner cells, boundary cells, out-of-range rejection and both adding and removing a shared range upgrade.



Diagnostics exposed old fixture assumptions as well as production issues. Surface fixtures now preserve schematic-air ground and provide terrain beyond the wider warehouse approach. The downhill forestry fixture now represents a ready Lumber Yard. Chest discovery was changed to bounded loaded block-entity visits after enlarged ranges made air-volume scans too slow. Production fixes include partial door kits, fresh courier planting reserves, non-leaf attachments retaining their exact-once payout, couriers leaving the building without repeatedly treating its outside approach as indoors, and selecting a clear resting cell while preserving neighboring leaves. Diagnostic and interrupted logs are retained with the release evidence.



The large field fixture explicitly keeps its own chunks ticking because the GameTest runner's origin ticket does not cover its remote field. This is test-only; production workers never force-load chunks. Ordinary fixtures have 64-block spacing to isolate their enlarged work ranges; the larger worksite fixtures retain 96 blocks.



Asset validation checks 111 JSON resources, 37 texture dimensions, 28.5-unit farmer hat / 27-unit miner hat / 26-unit base body, and block/model UV density. The packaged-client observer captures all five structures, new outfits and held tools, purpose/facing setup, farm controls, build lists, profession selection, worker tabs, the Pedestal Network view and projection, and the single surface range. Its initial diagnostic capture exposed a renderer warm-up delay, so the final observer waits for fixture mesh rebuilding before capturing.



Template rotation fixtures do not constitute 20 complete worker-built structures. The dedicated server tests physical first-shed construction and farm work; the visual scenario places controlled structures to review their geometry and menus. Multiple human clients, arbitrary claim/inventory mods, every terrain layout and operating-system crash recovery are outside these executed checks.



`build/milestone-tests-8.log`: **all 133 required dedicated-server GameTests passed**. The tight warehouse-exit test physically completed its handoff after the porch/eave boundary correction. `build/milestone-assets.log`: **111 JSON resources and 37 texture dimensions passed**. No gameplay or resource changes followed this tested build.



The final isolated packaged client used `build/packaged-smoke/20261024-015706`, recorded `AUTOMATONS_WORKSITE_VISUAL_COMPLETE`, captured **17 screenshots**, saved normally and exited **0**. Final captures were reviewed for the five completed buildings, Buildington/Farmington equipment, farm controls, the Pedestal Network panel and the single Totem range. Setup, material lists, profession selection, worker tabs and projection were also reviewed across the packaged runs. The successful helper log is `build/milestone-client-2.log`; the final raw client and debug logs accompany the captures. These are automated isolated-client checks, not a multiplayer or human acceptance session.



After the user saved and closed Rackworks Tester, installed **automatons-0.5.0.jar**, **744,266 bytes**, SHA-256 **CDAFB9344B822AFA43A6E5AB2A6BDAE57FBFAA22A596D16FD73B8B8FAD5B0F87**. The installed file matches the tested and reviewed build, and exactly one Automatons JAR remains in `mods`. The previous 0.4.2 release is preserved in `C:\Users\impsh\curseforge\minecraft\Instances\Rackworks Tester\.automatons-release-backups\20261024-020313-576`. No saved-world data or server configuration was changed by this installation. The release library at `F:/Minecraft/Automatons` preserves the working-tree source snapshot, five adjusted schematics, assets, documentation and verification evidence.

