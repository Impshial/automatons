# Automatons — Minecraft 1.20.1 / Forge

A wooden 1.5-block Automaton with a Lumberton forestry job. Version 0.2.1 includes independent Magica, core infusion, six-part assembly, portable workers, shared work totems, varied chest-to-core particles and optional JEI recipe pages.

## Install

Use Java 17 and Forge 47.4.10 for Minecraft 1.20.1. Put exactly one Automatons JAR in `mods` on both client and server. No animation-library dependency is required. Close Minecraft before replacing a JAR. `tools/install_test_instance.ps1` installs to the designated Rackworks Tester instance, checks for a running session, backs up the previous release, and verifies the copied hash. Keep a normal world backup before upgrading; 0.1.x worker identities, inventories, home links and upgrades migrate in place. The new network protocol requires matching mod versions.

## First working Automaton

1. Craft an **Infusing Pedestal** and **Empty Magica Cores** from vanilla ingredients. Neither requires a charged core.
2. Collect white/purple **Magica** orbs from successful player breaks of eligible vegetation. Magica has its own balance and bar below XP; it never grants XP or repairs Mending tools.
3. Right-click the infuser with an empty or partially filled core. Stay within four blocks with Magica available. Overlapping particles leave different points across the upper chest, vary in size and speed, and curve into the core from different angles. Infusion transfers one point every four ticks. Fifty points make one completed **Magica Core** in ten seconds at 20 TPS. A green effect and one chime signal completion. Use an empty hand to retrieve it. Walking away, logging out or running out of points pauses safely. Only the donor or an operator can retrieve the inserted core; breaking the pedestal drops it once.
4. Craft an **Automaton Pedestal**, **Totem**, **Wand**, one **Head**, one **Automaton Core**, two **Arms** and two **Legs**. The first complete setup consumes six charged cores, or 300 Magica, excluding optional upgrades.
5. Open the Automaton Pedestal and place the six components in the body-shaped slots. Take the result on the right to assemble one Automaton item. Shift-click works when inventory space exists. The display is a preview; assembly has no additional Magica fee. This pedestal becomes the worker's home.
6. Place the item on clear, solid ground. It starts paused and Unassigned. Open its GUI, select **Lumberton**, and insert up to three ordinary axes. It preserves the exact supplied tools and switches when one breaks.
7. Put ordinary single or double chests near the Totem. Use the wand in **Assign** mode to select the worker and then right-click your Totem. Assignment authorizes work. **Start Work** resumes a freshly placed worker with an existing valid link.

## JEI recipes

With JEI 15.56.0.205 or newer 15.x installed, press **R** over the Automaton item to see its six body parts, or **U** over the Automaton Pedestal to browse assembly recipes. Click **Recipes** in the pedestal screen for the same page. Press **R** over a Magica Core, or **U** over the Infusing Pedestal, for the 50-Magica world-infusion recipe. Normal crafting recipes remain in JEI's crafting category. The assembly page follows the server's recipe data, including data packs. Place the ingredients in the pedestal manually; this release adds recipe viewing, not automatic ingredient transfer. JEI is optional and is not bundled.

## Magica sources

Only completed player block breaks pay, once per logical plant action. Fortune does not multiply Magica. Fake players, machine/Automaton harvesting, indirect breaks and leaf decay do not pay. Direct placement followed by breaking does not pay; natural generation and subsequent plant growth qualify.

| Source | Inclusive points |
| --- | ---: |
| Supported natural tree logs | 3–7 |
| Fully mature supported crops | 2–4 |
| Pumpkin or melon | 2–4 |
| Fully ripe sweet berry bush | 1–3 |
| Natural flowers | 1–2 |
| Sugar cane, cactus, bamboo | 0–2 |
| Natural leaves | 0–1 |
| Grass and ferns | 0–1 |

Zero is intentional. Initial crop support is wheat, carrots, potatoes, beetroot and nether wart. Right-click berry picking is not a block break. Source tags and ranges are editable. Magica survives death by default; a server setting controls this.

## Shared forestry

A Totem accepts multiple same-owner workers. Each keeps its own home. The queue orders trees by horizontal distance from the Totem, with stable coordinate ties; workers claim different jobs. Other workers' claimed trees produce a waiting state. They share current totem upgrades and chest controls.

The base tree-root range is 16 blocks outward, a 33×33 square, with a separate ±16-block root-height allowance. A rooted tree can extend its branches or canopy beyond the square. Normal oak, birch, spruce, jungle, dark oak, acacia, mangrove and cherry growth is supported, including appropriate 2×2 trunks. Directly placed construction logs are excluded. Protected destructive areas are respected across the whole tree.

A worker approaches beside the trunk, chops the base, fells the tree visibly, then processes grounded sections. If the long fall will not fit, it tries a compact segmented fall. Unfinished timber owns its remaining wood and progress, so packing or unloading its worker does not erase the job. An eligible colleague can take over released timber; unavailable claimants have a bounded recovery delay. Players can right-click settled timber with an axe to process a nearby remaining section through the same consumption ledger.

Workers keep cargo across trees while the next output fits. Actual stack compatibility matters: nine occupied slots can still accept matching stacks. Full cargo or another required return starts a service visit. The worker deposits everything accepted across reachable enabled chests, refills empty axe slots where possible, then resumes or waits for its remaining blocker. Rejected items stay in cargo. A routine capacity check does not send a partly loaded worker to a chest.

Chest output and tool-supply toggles are independent. Double chests are one inventory, and both halves must be in range and usable. New/removed chests and available space are rechecked automatically. If all storage is confirmed gone, an authorized worker returns home but retains its work link so it can recover when storage returns.

## Signals and recovery

The amber Totem beam combines independent tool, wood, storage and cargo alerts. The GUI shows affected workers and current reasons. Resolving one reason does not erase another worker's alert. Adding tools, growing trees, freeing storage or unloading cargo automatically restores eligible work. Discovery, unavailable chunks, claimed work and path replanning are waiting states, not proof of resource absence.

**Call All Home** on a home pedestal recalls only that home's workers, even if they share a Totem with colleagues from another home. Recall remains active across saves and takes priority over placement pause. **Resume Work** releases it. A destroyed home requires deliberate **Rebind** to a replacement pedestal. Unloaded anchors remain unverified; replacing a block at the same coordinates does not inherit the old ID.

## Wand and packing

Hold Shift and scroll to cycle **Assign → Protect → Revoke → Wrench**. Mode changes cancel pending selections. Sneak-use cancels an unfinished assignment. Either hand works; the main-hand wand determines the overlay when both hands hold one.

Protect/Revoke selects a chunk and asks Yes/No for that original chunk. Closing or cancelling changes nothing. Protection covers the dimension's full legal height; green/red fading curtains are only a visual band around your elevation. Shared edges are merged. Walking, unloading and other nondestructive actions remain allowed. The creator or an operator can revoke protection.

In **Wrench** mode, Shift-right-click your worker to pack it. Its actual tools and cargo drop once, separately from the single Automaton item. The item preserves identity, health, name, job and saved links; it contains no second inventory copy. A Packed roster entry does not stop colleagues. Unbinding that entry remains authoritative even if an old item carries stale links. Placement starts paused. Cross-dimension anchors remain stored but need local rebind/reassignment; no worker teleportation occurs.

## Design and editable sources

[Recipes and complete balance reference](docs/BALANCE.md) · [Verification](docs/VERIFICATION.md) · [Limits](docs/LIMITS.md) · [Editable art](docs/ART.md) · [Changelog](docs/CHANGELOG.md)

Source: `src/main/java/dev/automatons`. Recipes/tags: `src/main/resources/data/automatons`. Reproducible pixel art: `tools/author_assets.py`. Editable Blockbench model: `art/lumberton.bbmodel`. Java animation rig: `client/LumbertonModel.java`. Blocks/items use 16×16 textures; the 24-model-unit worker uses a 64×64 UV atlas at the same one-pixel-per-model-unit density.

## Build

Set `JAVA_HOME` to Java 17. Run `gradlew.bat build`. For a fresh regression run, use `tools/test_release.ps1`; it preserves the preceding isolated test world before running `runGameTestServer build`. GameTests use `build/gametest-redesign` and never a player save. Run `python tools/validate_assets.py` to validate dimensions, model density and resource JSON. `gradlew.bat runClient -PwithJei` includes JEI in development; ordinary development/server runs have no JEI runtime dependency. `gradlew.bat runClient -PvisualSmoke` enables the opt-in `Automatons Lab` visual scenario; normal launches never run it.
