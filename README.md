# Automatons — Minecraft 1.20.1 / Forge


Version **0.9.0** adds [The Machine and Operatorton](docs/MACHINE.md): a rideable vehicle with Blade/Bore attachments, 108 cargo slots, physical operator boarding, programs, chunk surface clearing and courier collection. Iron frames use native Minecraft 16x16 material textures. Lumite interiors are brighter while their stone/deepslate backgrounds remain unchanged.

The main Pedestal now offers Build Lists for active network projects. Builders can begin with partial supplies, cross their shed door reliably, and use Hammers with 2,000 durability.

Wooden Automatons with Lumberton, Minington, Runnington, Buildington, Farmington and Operatorton professions. Version 0.8.3 turns all landed timber into real sideways log blocks, regularly clears stale animation state, clears permitted fall obstructions, checks jobs before travel and periodically delivers Lumberton output. Lumbertons request same-Network Miningtons for pickaxe obstacles instead of breaking stone themselves. The Collector now follows the supplied open stone-basin design, without copper. Version 0.8.2 gives every Automaton nearby item pickup and sends idle Builders inside their sheds. Version 0.8.1 uses stone trim on the Collector and glass framing on the Tank. Version 0.8.0 adds the passive Lumina Collector, Luminall fluid, craftable Tank and drinkable bottles; corrects Deepslate ore heights, raises Infuser shards, prevents their automatic offhand transfer and lets Builders start with partial materials. See [Luminall](docs/LUMINALL.md) for recipes and balance. Version 0.7.5 moves the handbook into a separately editable resource pack. Version 0.7.4 adds temporary construction supply chests with courier deliveries and cleanup, perimeter walking at half the previous placement speed, automatic obstruction removal, clearer material waits, corrected infuser surfaces/collision, and supplied small fuel-piece sprites. Workers awaiting construction return home; breaking a Totem releases its construction zone. Version 0.7.3 uses the supplied raw and infused shard PNGs exactly as provided. Version 0.7.2 slightly brightens the exposed Lumite and gives raw shards very pale purple interiors while retaining their edges. Version 0.7.1 gives Lumite a painted chipped rim and shaded interior using native 16x16 textures with the supplied vanilla stone/deepslate backgrounds. Its original cube geometry is retained. Version 0.7.0 added an in-game handbook, small fuel pieces, larger new-chunk Lumite clusters, optional JER information, three-level trunk felling and construction from outside the building footprint. The original five professions retain their base settings. Each Totem has one range for both work and nearby chests. Body collision dimensions remain 1.625 blocks tall and 0.7 blocks wide; profession accessories do not enlarge them.



Start with [The Network and worksite guide](docs/THE_NETWORK.md) for site setup, the first Buildington, farming and the shared range.



## Install



Use Java 17 and Forge 47.4.10 for Minecraft 1.20.1. Put exactly one Automatons JAR and GuideME 20.1.15 (Forge 1.20.1) in `mods` on both client and server. JER 1.4.0.247 and JEI 15.56.0.205 are optional client additions; no AE2 dependency is required. No animation-library dependency is required. Close Minecraft before replacing a JAR. `tools/install_test_instance.ps1` installs to the designated Rackworks Tester instance, checks for a running session, backs up the previous release, and verifies the copied hash. Existing energy, cores and installed upgrades have explicit compatibility handling; see [Lumina and existing saves](docs/LUMINA.md). The new network protocol requires matching mod versions.

The handbook is now separate from the JAR. Copy the unpacked `resourcepacks/Automatons-Guide` folder into the client instance's `resourcepacks` directory and enable it in **Options → Resource Packs**. The Rackworks installer does this automatically and preserves your edits on future installs. Edit `.md` files in `Automatons-Guide/assets/automatons/guides/automatons/handbook/`, save, press **F3+T**, and reopen the book. No mod rebuild is needed. See [handbook editing](docs/HANDBOOK-AUTHORING.md). Dedicated servers do not need the resource pack.



## First working Automaton

Craft an **Automaton Handbook** from one vanilla book and any plank, then right-click it for the in-game guide. It includes current recipes, worksite previews, controls and troubleshooting. Small fuel pieces split/recombine at eight pieces per coal or charcoal; one piece smelts one item in vanilla furnaces, blast furnaces or smokers.


1. Mine **Lumite Ore** with a stone pickaxe or better in new Overworld terrain, with stone ore between Y −32 and 64 (peak 16) and deepslate ore between Y −64 and 0 (peak −32). Both rock variants drop 1–3 raw shards before Fortune.

2. Craft an **Infusing Pedestal** using a diamond, and **Empty Cores** using raw Lumite Shards. Collect white/purple **Lumina** orbs from eligible vegetation; existing rewards, independent balances and XP behavior are unchanged.

3. Right-click a raised pedestal corner with one raw shard. Up to four shards float separately. Stay within four blocks to pay **25 Lumina per shard**. All corners share one point every four ticks: one shard takes five seconds, four take twenty at 20 TPS. Empty-hand use retrieves your selected shard; partial items retain exact paid thirds. Leaving range, disconnecting or exhausting energy pauses safely.

4. Craft one **Lumina Core** from four Infused Lumite Shards around one Empty Core. The home pedestal, Totem, Wand and six body parts retain their original core counts: a first complete setup uses six cores, now **600 Lumina**. Crafting completed shards into a core does not charge again.

5. Open the Automaton Pedestal and place the six components in the body-shaped slots. Take the result on the right to assemble one Automaton item. Shift-click works when inventory space exists. The display is a preview; assembly has no additional Lumina fee. This pedestal becomes the worker's home.

6. Place the item on clear, solid ground. It starts paused and Unassigned. For the first worksite, choose **Buildington** from its profession list, click **Apply Job**, and equip a **Hammer**.

7. Place a Totem, open **Worksite Settings → Worksite**, and configure **Builder** with the desired facing. Put construction stock in reachable chests inside its 8-block range, outside the shed footprint and entrance. Use **Build List** for exact quantities.

8. Use the wand in **Assign** mode to select the Buildington and right-click its Totem. It builds its own shed first. Then configure Mine, Lumber Yard, Farm or Warehouse Totems on the same Network for it to build. Assign production workers with appropriate tools to the completed sites. **Start Work** resumes a worker with an existing valid assignment. See [the worksite guide](docs/THE_NETWORK.md) for the full setup.



## JEI recipes



With JEI 15.56.0.205 or newer 15.x installed, press **R** over the Automaton item to see its six body parts, or **U** over the Automaton Pedestal to browse assembly recipes. Press **R** over an Infused Lumite Shard, or **U** over the Infusing Pedestal, for the 25-Lumina infusion recipe. A Lumina Core uses ordinary shaped crafting. Normal crafting recipes remain in JEI's crafting category. The assembly page follows the server's recipe data, including data packs. Place the ingredients in the pedestal manually; this release adds recipe viewing, not automatic ingredient transfer. JEI is optional and is not bundled.



## Lumina sources



Only completed player block breaks pay, once per logical plant action. Fortune does not multiply Lumina. Fake players, Automaton harvesting, indirect breaks and leaf decay do not pay player Lumina. The Machine's Blade explicitly credits eligible natural harvests to its engine once; the Bore never does. Direct placement followed by breaking does not pay; natural generation and subsequent plant growth qualify.



| Source | Large-orb value (inclusive points) |

| --- | ---: |

| Supported natural tree logs | 3–7 |

| Fully mature supported crops | 2–4 |

| Pumpkin or melon | 2–4 |

| Fully ripe sweet berry bush | 1–3 |

| Natural flowers | 1–2 |

| Sugar cane, cactus, bamboo | 0–2 |

| Natural leaves | 0–1 |

| Grass and ferns | 0–1 |



Each small orb is exactly one-third of the corresponding large value; fractional thirds persist internally; the HUD and pedestal readout show whole points only. Flowers and grass drop one small orb. Mature crops drop one orb, with a 10% chance of a large one. Logs drop two small orbs, plus a 40% chance of one large orb. Other source categories retain their earlier large rewards. The table preserves the existing large-orb values. A zero roll produces no orbs. Initial crop support is wheat, carrots, potatoes, beetroot and nether wart. Right-click berry picking is not a block break. Source tags and ranges are editable. Lumina survives death by default; a server setting controls this.



## Runningtons and shared logistics



Choose **Runnington** on a generic worker, then assign it with the wand to a warehouse Totem near its home pedestal. Supply building materials in cargo or nearby enabled supply chests; the builder can begin with a partial kit. A Buildington constructs the warehouse; runners sharing its anchor serve work Totems on the same home network. See the [Runnington guide](docs/RUNNINGTON.md) for the exact kit, setup, controls and recovery.



## Shared forestry



For mine construction, supplies, Tunnel/Quarry controls, torch lighting and recovery, see the [Minington guide](docs/MININGTON.md).



Lumberton-felled logs now have solid per-log collision after landing. The client initializes those collision parts when the tree spawns, and interpolates fall progress continuously.



A Totem accepts multiple workers belonging to the same owner and home pedestal. The queue orders trees by horizontal distance from the Totem, with stable coordinate ties; workers claim different jobs. Other workers' claimed trees produce a waiting state. They share base work settings and recognized storage.



Each Totem has one horizontal range for both profession work and nearby chests, with no inner exclusion. Defaults are 32 blocks for Lumber and Farm, 6 for Mine, and 8 for Builder and Warehouse. The Lumber Totem therefore covers a 65×65 square, with a separate ±32-block vertical root allowance. A rooted tree can extend its branches or canopy beyond the square. Normal oak, birch, spruce, jungle, dark oak, acacia, mangrove and cherry growth is supported, including appropriate 2×2 trunks. Loose, stripped and player-placed logs inside the range are now harvested individually, except structural logs reserved by registered worksite blueprints. Natural trees rotate as connected trunks and branches, preserving their original spacing throughout the fall. Protected destructive areas are respected across the whole tree.



A worker approaches beside the trunk, chops the base, fells the tree visibly, then processes grounded sections. The tree keeps its shape and tries four fall directions, resting on terrain or obstacles without scattering its logs. It can bridge lower terrain. Every section has a different landing coordinate, including across active timber jobs. If no safe connected layout fits, the standing tree waits. Older stacked timber jobs replan their landing without discarding wood. Unfinished timber owns its remaining wood and progress, so packing or unloading its worker does not erase the job. An eligible colleague can take over released timber; unavailable claimants have a bounded recovery delay. Players can right-click settled timber with an axe to process a nearby remaining section through the same consumption ledger.



After processing a felled tree, a Lumberton replants a sapling at its stump when the soil and space are valid. It fetches replacements from assigned storage when needed and retains a working stock of up to 16 per sapling type. It uses real inventory items and walks within reach; dark oak requires four saplings and a clear 2×2 planting space.



Every Automaton collects visible dropped items within one block of its body, including while idle, paused, unassigned or waiting for construction. Pickup runs every five ticks without changing its route or current project. Useful tools fill empty profession tool slots first; spare tools and other items use cargo. Full inventories leave the remainder on the ground. Pickup delays and item ownership are respected. Runningtons still leave tools, torches and saplings in chests.

Lumbertons also seek visible, reachable dropped log items resting on the ground inside their Totem’s logging range. They collect them into cargo and deliver them through the same storage system. Pickup delays, item ownership, height limits and cargo space are respected.



Workers keep cargo across trees while the next output fits. Actual stack compatibility matters: nine occupied slots can still accept matching stacks. Full cargo or another required return starts a service visit. The worker deposits everything accepted across reachable enabled chests, refills empty axe slots where possible, then resumes or waits for its remaining blocker. Rejected items stay in cargo. A routine capacity check does not send a partly loaded worker to a chest.



All recognized chests supply and store items by default. Double chests are one inventory, and both halves must be in range and usable. New/removed chests and available space are rechecked automatically. If all storage is confirmed gone, an authorized worker returns home but retains its work link so it can recover when storage returns.



The Totem's **Show Work Area** button toggles a local terrain-following preview: translucent green cubes and an amber boundary show the shared work/chest area. There is no separate storage overlay. Only air cells above blocks receive markers, at one block high. The preview follows the configured base range, remains visible after closing the GUI, and clears on logout or removing the totem. **Hide Work Area** turns it off.



Grass and flowers do not block movement. Workers can cut obstructing leaves near the target; solid terrain requires a reachable route. Chopping includes a continuous axe swing, target-facing head/body and progressive damage cracks. Workers move away from falling timber before it can overlap them.



In Survival, breaking the base of an eligible natural tree with a stone-or-better axe fells its remaining logs. The base drops once through normal breaking. When the remaining tree lands, it becomes ordinary, horizontally oriented log blocks that can be mined normally. Leaf loot is released once at landing. If landing cells become obstructed or occupied, it waits safely. Creative breaks retain vanilla behavior. Player-placed logs do not trigger whole-tree player felling, but Lumbertons can harvest them individually.



Default worker preparation/processing times: Stone **7/4 seconds**, Iron **5/3**, Diamond **3.5/2**, Netherite **3.25/1.75**. See the balance table for all materials, enchantments and timing rules.



## Signals and recovery



Open **Totem → Workers → a worker → Find** to outline that loaded Automaton for 30 seconds, including through terrain. Packed or unloaded workers cannot be highlighted. Find does not move or recall the worker.



Each completed Automaton receives a permanent random personal name from the supplied 640-name catalog. The name follows its identity through jobs, packing and redeployment; the Totem roster and packed item display it. Names are unique across the entire saved server, including other players and dimensions. Issued names are never recycled. After all base names are used, unique two-name compounds are issued, followed by numbered compounds only if those combinations are exhausted.



The home pedestal's **Show Automaton Names: ON/OFF** button controls its workers' overhead labels, across all jobs and every assigned Totem. It defaults to ON and persists for unloaded workers. It does not hide names in menus or items. Rebinding adopts the new home's setting; replacing a pedestal at the same coordinates does not take over the old workers. Owned name-tag renames use the same registry, reject reserved names without consuming the tag, and retire the previous name. Accent, case and spacing variants cannot bypass uniqueness.



The amber Totem beam combines independent tool, wood, storage and cargo alerts. The GUI shows affected workers and current reasons. Resolving one reason does not erase another worker's alert. Adding tools, growing trees, freeing storage or unloading cargo automatically restores eligible work. Discovery, unavailable chunks, claimed work and path replanning are waiting states, not proof of resource absence.



**Call All Home** on a home pedestal recalls only that home's workers, even if they share a Totem with colleagues from another home. Recall remains active across saves and takes priority over placement pause. **Resume Work** releases it. A destroyed home requires deliberate **Rebind** to a replacement pedestal. Unloaded anchors remain unverified; replacing a block at the same coordinates does not inherit the old ID.



## Wand and packing



Hold Shift and scroll to cycle **Assign → Protect → Revoke → Wrench**. Mode changes cancel pending selections. Sneak-use cancels an unfinished assignment. Either hand works; the main-hand wand determines the overlay when both hands hold one.



Protect/Revoke selects a chunk and asks Yes/No for that original chunk. Closing or cancelling changes nothing. Protection covers the dimension's full legal height; green/red fading curtains are only a visual band around your elevation. Shared edges are merged. Walking, unloading and other nondestructive actions remain allowed. The creator or an operator can revoke protection.



In **Wrench** mode, Shift-right-click your worker to pack it. Its actual tools and cargo drop once, separately from the single Automaton item. The item preserves identity, health, name, job and saved links; it contains no second inventory copy. A Packed roster entry does not stop colleagues. Unbinding that entry remains authoritative even if an old item carries stale links. Placement starts paused. Cross-dimension anchors remain stored but need local rebind/reassignment; no worker teleportation occurs.



## Design and editable sources



[Recipes and complete balance reference](docs/BALANCE.md) · [Verification](docs/VERIFICATION.md) · [Limits](docs/LIMITS.md) · [Editable art](docs/ART.md) · [Changelog](docs/CHANGELOG.md)



Source: `src/main/java/dev/automatons`. Recipes/tags: `src/main/resources/data/automatons`. Reproducible pixel art: `tools/author_assets.py --rig-only` and `tools/author_mining_assets.py`. Editable Blockbench model: `art/lumberton.bbmodel`. Java animation rig: `client/LumbertonModel.java`. Blocks/items use 16×16 textures; the 26-model-unit worker uses a 128×128 UV atlas at the same one-pixel-per-model-unit density.



## Build



Set `JAVA_HOME` to Java 17. Run `gradlew.bat build`. For a fresh regression run, use `tools/test_release.ps1`; it preserves the preceding isolated test world before running `runGameTestServer build`. GameTests use `build/gametest-redesign` and never a player save. Run `python tools/validate_assets.py` to validate dimensions, model density and resource JSON. `gradlew.bat runClient -PwithJei` includes JEI in development; ordinary development/server runs have no JEI runtime dependency. `gradlew.bat runClient -PvisualSmoke` enables the opt-in `Automatons Lab` visual scenario; normal launches never run it.



The full change history since the redesign is in [HISTORY-SINCE-REDESIGN.md](docs/HISTORY-SINCE-REDESIGN.md). Workers route around full fallen trunks, retry alternate approaches after stalls, and defer blocked jobs so other reachable logs can be harvested.



Falling trees can trim grass, flowers and natural leaves in their path. Cleared plants retain their normal drops; other trunks, protected blocks and placed leaves remain obstacles. Lumbertons share a retry delay for genuinely blocked falls and can take other reachable jobs.





Forestry searches 32 blocks above and below the Totem by default. For otherwise unreachable high logs, give the Lumberton dirt, cobblestone or planks: it jumps onto real blocks from cargo, then breaks and recovers its own pillar. The pillar journal survives reloads. It respects protected areas and waits if another entity occupies a support.



Runningtons leave tools, Hammers, torches and saplings in all chests. Supply those items locally for the worker that uses them. Buildingtons can evacuate registered staging chests within their build footprint. Underground mine double chests remain Runnington pickup points.



## Lumite in existing worlds



Lumite generates only in new eligible terrain. Automatons-owned commands and the retrogen scheduler are removed. Old job history remains inert and existing chunks are not refilled. See [Lumina and save handling](docs/LUMINA.md).



All ten upgrades and the unused Upgrade Plate are removed from active gameplay. Old items remain registered and inert; opening an owned Totem returns installed items, dropping overflow once. Base ranges, tool enchantments, backpacks and profession abilities remain.

