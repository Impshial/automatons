# Minington guide — 0.9.1

Use the same assembled Automaton item and home pedestal. Open a deployed worker, choose **Minington** from the list, click **Apply Job** (confirmation appears only when changing an existing job), and put ordinary pickaxes and shovels in its three tool slots. Previously stored axes remain stored but are inactive for mining. Assign it to a **Mine** Totem with the wand, or press **Start Work** for its existing valid assignment. A Buildington must finish that site’s surface entrance before mining starts. Different professions can share membership and local storage, but primary production requires the matching site purpose.

## Starting supplies

Put these in reachable Totem chests, or directly in worker cargo:

- **59 cobblestone, 33 ordinary oak logs, 28 cobblestone stairs, 10 cobblestone slabs, 16 oak planks, 4 lanterns, one item frame and one stone pickaxe for its display** for a Buildington to construct the supplied covered entrance. Configure this Totem as a Mine site first. These are placed items, not crafting ingredients.
- **20 ladders**, plus cobblestone if missing terrain must be replaced behind a ladder.
- **4 ordinary chests**. All four must be available across its cargo and recognized staging supplies before shaft excavation starts. They become two double chests in wall alcoves on either side of the bottom platform.
- A usable pickaxe; a shovel helps with dirt and gravel. Iron or better can harvest more ore types. Tools retain their real durability and enchantments.
- Ordinary **torches**. The worker aims to carry **16** in cargo, but can start with fewer. Redstone and soul torches are not substitutes.
- Additional cobblestone for damaged access or a quarry step over an air gap.

Open **Build List** on the worker for scrollable construction costs, remaining quantities, available stock and missing items. All recognized chests supply and store items.

A surface output chest is optional. A miner carrying its starting supplies digs the first ground block itself and keeps excavation drops in cargo until it builds its depot. Once the underground depot is built, miners empty full cargo into those **two underground double chests**, even though they are outside the Totem surface range. They try the second pair if the first is full. If both are full, obstructed or unavailable, they wait at the platform. These are ordinary player-accessible chests. Runningtons on the same home network can carry their eligible output to the warehouse through the real ladder route.

Surface chests remain usable for tools, torches and construction supplies. Miners can accept permitted material handoffs and restock from their registered underground depot. Runningtons leave tools and torches in chests, so supply those locally. On necessary supply trips, miners first unload harvested cargo underground, retaining bounded working supplies. They do not craft materials or use unrelated distant chests.

## One mine per Totem

The **Totem → Worksite Settings → Mine** panel shows the shared stage, mode, progress, supplies and reason for waiting. The Mine Totem records the Buildington’s completed entrance, and miners share the ladder passage. Entrance completion is shared. Later miners can descend through completed access without rebuilding or waiting for an absent builder. In Tunnel mode, each miner claims a different saved branch and continues it after unloading.

The base mining square extends **32 blocks** in each horizontal direction from the Totem (65×65). Mining Range I/II extend it to 48/64 blocks. The Mine Totem has one smaller surface range for its entrance and nearby chests (6 blocks by default); it has no separate storage radius. The surface entrance stays anchored to its configured Totem and facing. Underground work still requires safe, loaded terrain and an accessible route.

After a Buildington completes the supplied 7×7×7 covered entrance, Miningtons use its open access gap, install twenty backed ladder segments all the way to the top, and clear a **3×3×3 chamber**. The chamber's standing elevation is **20 blocks below the entrance**. Four real chest items become two connected double chests against the side walls, with clear space above their lids and a central walking route. Workers descend and climb through this access using physical movement.

**Tunnel (default):** independent 1-wide, 2-high, 32-block branches. Miners claim different lanes, three blocks apart, with a shared access passage along the platform edge. Each branch saves its own worker, column, row and access progress. Unloading, pausing and reloading preserve the assignment. A finished or relinquished branch can be replaced or reclaimed; an unloaded worker's unfinished branch remains assigned. Existing excavated tunnels and floors remain intact and are verified as access is reused. A fluid-blocked miner returns to the platform and selects a new direction without redirecting its colleagues.

Quarry selection is temporarily hidden. Saved quarry geometry remains in place; loaded plans resume in tunnel mode. **Pause** stops new excavation and holds the worker locally; **Resume** continues its saved work. Unloading and supply trips also retain progress. Ordinary waiting does not send a miner back to the Totem. Home **Call All Home** takes priority and uses the mine's real return path. A broken or obstructed route can leave a worker waiting safely for repair.

## Lighting and recovery

Miningtons use actual server **block light**, with a default minimum of **8** at managed standing spaces. They light chamber edges, tunnel corners, permanent quarry treads with ordinary wall or floor torches. Bare vertical shaft cells do not each need a light. Existing adequate player lighting is accepted. Skylight, shaders and held-item lighting do not satisfy these checks.

Each successful placement consumes one real torch. A pending light-engine update shows **Updating lighting**. Further excavation waits for newly exposed standing spaces to be lit. Managed routes are rechecked incrementally, including after a reload or removal of a light. A mine torch conflicting with excavation receives replacement lighting before its normal item is recovered.

**Needs torches** means no usable local torches are currently available. The worker travels for supplies only when eligible stock exists. Add torches to a valid chest; authorized work resumes automatically after physical restocking and lighting repair. **Lighting blocked** means there is no safe reachable placement, or permission was denied. **Access blocked** refers to the route. Torches are retained up to the configured stock target during unloading and drop once with real cargo when packing.

The light target is a conservative margin. It does not remove existing mobs, block creatures entering from caves, or cover every special spawning rule.

## Controls and boundaries

- **Totem → Workers → worker → Find** outlines a loaded owned worker for 30 seconds. It works for every profession.
- **Show Work Area** toggles the preview. Green marks the shared surface work/chest area; amber outlines also show declared mining geometry. There is no inner storage area. The Mine/Forestry tabs in Worksite Settings can toggle their layers. A glowing yellow perimeter marks planned entrance construction. Mining outlines can be seen through terrain but never reveal ores or scan unknown blocks.
- Mining uses actual block hardness, harvest tier, tool speed, Fortune/Silk Touch and actual tool enchantments. Block-breaking work matches an ordinary grounded player using the same vanilla tool and Efficiency enchantment: unenchanted stone takes 12/8/6 ticks with stone/iron/diamond picks. Work advances every tick, without the previous five-tick rounding. Travel, lighting, settling and unloading take additional time. Ordinary ore XP is emitted; mining produces no Lumina. Lumberton timing and walking speed are unchanged.
- Protected areas, other mines' infrastructure, containers, fluids, occupied work cells and unbreakable blocks are checked before commits. For sand/gravel and other falling blocks, the miner moves at least two blocks horizontally away, keeps normal reach and line of sight, and clears the column from that position. It waits for falling entities to settle before advancing. If no reachable safe stance exists, it pauses; it never mines directly underneath the falling column. Ordinary terrain is defined by `automatons:excavation_terrain`; natural Blocks You Need: Luna stones such as Jadestone and Shale are included. Arbitrary machines and building blocks remain excluded.
- No chunks are force-loaded, and workers do not teleport, excavate unrelated caves for lighting, or bypass claim hooks. An unloaded area is a waiting state.

Configuration keys are `miningRadius`, `mineEntranceHeight`, `miningInteractionReach`, `miningTorchStock` and `miningBlockLightTarget` in each world's `serverconfig/automatons-server.toml`. Defaults are 32, 16, 4.5, 16 and 8 respectively. Existing custom settings are preserved on installation.

## Infrastructure recovery

This milestone targets new worksite worlds. Started shafts, depots and branches are still saved normally within those worlds. A fluid discovered while constructing the only shaft/platform blocks that construction safely; alternate directions start from a completed underground platform. Unsafe footing or an unreachable damaged ladder can require player repair.
