# Recipes and provisional balance — 0.6.0

For the 0.9.0 Machine, see [Machine assembly and balance](MACHINE.md). `MachineRules` keeps its 48-block whole-footprint radius, 108 cargo slots, 10,000-Lumina capacity, 30-tick startup, 12-tick moves/turns/shutdown, four-tick cutting cadence and one-third-point action costs together. Refueling uses existing Luminall bottle volume/reward settings rather than equating mB with Lumina. Blade harvests use existing eligibility and rewards; Bore work pays no Lumina.

Server settings live in `serverconfig/automatons-server.toml` inside each world. Current data files are authoritative. See the [before/after recipe table](RECIPE-CHANGES-0.6.md).

## Survival recipes

All outputs are one item. Dots are empty cells; slashes separate rows. Logs retain the existing Overworld log/wood tag.

| Output | Shaped layout | Key |
| --- | --- | --- |
| Empty Core | `.G. / GSG / .G.` | G glass; S raw uncharged Lumite Shard |
| Infusing Pedestal | `I.I / DSD / BBB` | I iron ingot; D polished diorite; S diamond; B polished deepslate |
| Lumina Core | `.I. / IEI / .I.` | I Infused Lumite Shard; E Empty Core without legacy credit |
| Arm | `L / I` | L log; I iron ingot |
| Leg | `L / P / I` | L log; P planks; I iron ingot |
| Head | `LPL / NRN / LIL` | L log; P planks; N iron nugget; R redstone; I iron ingot |
| Automaton Torso | `LIL / CRC / LIL` | L log; I iron ingot; C Lumina Core; R redstone |
| Home pedestal | `SCS / IGI / SCS` | S stone bricks; C Lumina Core; I iron ingot; G gold ingot |
| Totem | `.C. / ILI / .S.` | C Lumina Core; I iron ingot; L log; S stone bricks |
| Wand | `..C / .S. / S..` | C Lumina Core; S stick |
| Hammer | `CIC / .S. / .S.` | C cobblestone; I iron ingot; S stick |

One Head, one Torso, two Arms and two Legs assemble into one Automaton at its home pedestal, with no extra Lumina payment. The torso remains a distinct component.

All five upgrade families (Totem Range, Lumber Range, Mining Range, Speed and Efficiency), both tiers, and the Upgrade Plate are deferred. The plate has no surviving non-upgrade dependency. Hidden legacy registrations prevent item loss; they grant no bonuses and cannot be installed.

## Shard infusion and first setup

Four independent physical corners each accept one raw shard. Completion costs 25 Lumina (75 exact thirds). Partial shards are nonstackable and retain paid energy through retrieval, reinsertion and saves. All corners share three thirds every four ticks. The four-block radius is measured from each shard to its live owner. Four shards cost 100 Lumina and take 400 active ticks (20 seconds at 20 TPS); one takes 100 ticks. Unloaded time grants no catch-up. Crafting a core consumes four completed shards and an Empty Core, without another payment.

A first setup requires six Lumina Cores: two in the home pedestal, one in the Totem, one in the Wand and two in the torso. Total: 600 Lumina, 24 infused shards, 6 raw shards for Empty Cores and 24 glass, plus other recipe materials. One pedestal completes those 24 infusions in 120 active seconds. Its bootstrap recipe requires one diamond. Existing completed cores remain usable without retroactive payment.

## Lumite generation

Current settings: separate stone and deepslate features, each with eight attempts per chunk, vein size eight and zero air-discard probability. Stone uses triangular Y=-32..64 with peak 16; deepslate uses Y=-64..0 with peak -32. Actual extents clip to each range and owning chunk. Matching replacement tags and eligible Overworld biomes constrain placement. Vein size is not a guaranteed block count. Prior measured terrain results are versioned in VERIFICATION.md.

Both ores require stone-tier pickaxes or better. Loot is uniformly 1–3 shards, then normal Fortune ore bonuses; Silk Touch yields the matching ore block. Wrong tools yield no ore loot. Ore emits no world light, XP or extra Lumina; only its vein layer is emissive.

## Tools and construction

Hammer has 2,000 durability and retains its recipe, Buildington eligibility and two-tick construction action interval. Player mining speed is a positive 0.5, with wooden-pickaxe harvest eligibility. Survival stone measured 90 work ticks versus 23 with a wooden pickaxe under identical conditions. Higher-tier ores still break but yield no drops or mining XP. Normal player modifiers and one durability charge apply.

Minington work is `ceil(hardness × 30 / effectiveToolSpeed)`, minimum one tick. Effective speed is the real stack's speed plus `efficiency² + 1` when base speed exceeds one and its actual enchantment is positive. No retired upgrade supplies a bonus. Unenchanted stone takes 12/8/6 work ticks with stone/iron/diamond picks, excluding travel, lighting and service.

Buildingtons retain one placement every two ticks within reach with supplies and valid survival/permission checks. Door halves cost one operation. Five blueprint costs including displays are in the Build List and `reference/worksites`. New mines have no trapdoor, descend twenty blocks and use nineteen ladders plus four chests for two underground double chests.

## Lumina yields and orb sizes

| Category | Large value min | Large value max |
| --- | ---: | ---: |
| logs | 3 | 7 |
| crops | 2 | 4 |
| fruit | 2 | 4 |
| berries | 1 | 3 |
| flowers | 1 | 2 |
| stems | 0 | 2 |
| leaves | 0 | 1 |
| grass | 0 | 1 |

One inclusive uniform large-value roll per completed eligible player break. A small orb stores exactly one-third of that roll. Flowers/grass produce one small orb; crops produce one orb with a 10% large chance; logs produce two small orbs plus a 40% chance of one large orb. Fruit, berries, stems and leaves keep their earlier rewards. A zero roll produces no entities. Small orbs render at half the large diameter and merge only with matching size/value. Existing saved orbs stay large. Whole points plus a saved 0–2 thirds remainder preserve exact fractions across reloads; each shard consumes exactly 75 thirds (25 points). Numeric bar labels show only whole points, without rounding fractions into spendable points. A reversed configured range is corrected by raising its maximum to its minimum, with a log entry. Ordinary XP and Mending are unaffected. Display levels use the vanilla XP level curve, but all costs and stored balances are exact independent points.

## Server settings

| Key under `provisional` | Default | Meaning |
| --- | --- | --- |
| `storageHeight` | 4 | Vertical chest allowance ±4 |
| `lumberRadius` | 32 | Lumber Totem work and chest range; 65×65 square |
| `farmRadius` | 32 | Farm Totem work and chest range; 65×65 square |
| `mineSurfaceRadius` | 6 | Mine entrance work and chest range; 13×13 square |
| `builderRadius` | 8 | Builder Totem work and chest range; 17×17 square |
| `warehouseRadius` | 8 | Warehouse Totem work and chest range; 17×17 square |
| `rootHeight` | 32 | Separate root-height allowance ±32 |
| `cargoSlots` | 9 | New worker cargo capacity, 1–27; existing larger inventories are retained |
| `walkingAttribute` | .215 | Measured flat-ground default ≈1.996 blocks/second |
| `ironAxePreparationTicksPerBaseLog` | 80 | Baseline before the additional 20 Stone/Iron ticks; final Iron preparation is 5 seconds |
| `ironAxeProcessingTicks` | 40 | Baseline before the additional 20 Stone/Iron ticks; final Iron processing is 3 seconds |
| `fallAnimationTicks` | 30 | 1.5-second visual fall, independent of work timing |
| `serverScanBudgetPerTick` | 4096 | Shared scan/validation units |
| `totemScanBudgetPerTick` | 256 | Incremental tree-search cells per visit |
| `infusionTicksPerPoint` | 4 | Active ticks per exact transferred Lumina point |
| `infusionRange` | 4 | Donor radius in blocks |
| `retainMagicaOnDeath` | true | Retain independent balance on death |
| `logWorkTransitions` | false | Optional transition/skip diagnostics, at most one message/second/worker |
| `displayTiltDegrees` | 30 | Permanent item-display lean |
| `displayRotationSeconds` | 8 | One full Y rotation |
| `displayScale` | .7 | Display item scale |
| `displayBobAmplitude` | .04 | Gentle vertical bob |
| `displayHeight` | 1.4 | Display center above pedestal origin |

Lumina minima/maxima live under `magicaYields.<category>.minimum/maximum`. Numeric ranges are validated by Forge. Old worlds preserve existing configured scan budgets rather than silently replacing user settings.

## Base work settings

One surface work/chest range: Lumber/Farm 32, Mine 6, Builder/Warehouse 8 blocks. No inner exclusion or installed bonus. Underground mining extent defaults to 32 independently of the small entrance area.

Forestry work time is `baseTicks × 6 / toolSpeed + extraTicks`. Extra ticks: 10 for Diamond/Netherite, 20 for Stone/Iron, zero for Wood/Gold. Actual tool Efficiency scales this by `toolSpeed / (toolSpeed + efficiencyBonus)`; the bonus is zero at Efficiency 0, otherwise `efficiency² + 1`. Round to five-tick intervals, minimum ten. Preparation repeats for each base cut.

| Axe | Preparation per base log | Fallen/loose log processing |
| --- | ---: | ---: |
| Wood | 12.00 s (240 ticks) | 6.00 s (120 ticks) |
| Stone | 7.00 s (140 ticks) | 4.00 s (80 ticks) |
| Iron | 5.00 s (100 ticks) | 3.00 s (60 ticks) |
| Diamond | 3.50 s (70 ticks) | 2.00 s (40 ticks) |
| Netherite | 3.25 s (65 ticks) | 1.75 s (35 ticks) |
| Gold | 2.00 s (40 ticks) | 1.00 s (20 ticks) |

These defaults assume 20 TPS, no enchantments, and exclude walking, dispatch and the 30-tick fall animation. Workers keep support for all six vanilla axe materials. Player-triggered natural tree felling requires stone tier or higher (wood/gold excluded). One durability action is charged per base cut and processed log; Unbreaking remains normal. Loose/placed logs use processing time directly, with no preparation or falling animation.


## Dispatch, recovery and safety budgets

Discovery checks closer columns and all configured root heights before releasing farther jobs. A per-dimension standing-tree claim prevents overlapping totems felling the same wood. Standing claims rebuild on load; committed timber owns its own persistent consumed-section and pending-loot state. One active worker claims a timber job. Packing/recall releases it immediately; an unavailable prior claimant is released after 200 loaded timber ticks.

A route search considers all valid work positions around a target together. Workers check actual movement every 20 ticks; after 40 stalled ticks they invalidate the route and avoid its next node for 80 ticks. Unsafe timber is deferred after 60 ticks; an unreachable timber approach is deferred after 100 ticks, with a 200-tick job cooldown. They can work on another reachable log during that interval. Idle workers can unload cargo while the area scan continues.

Global navigation attempts are capped at 32 per tick, fall plans at two, and conversions at one. Deferred work stays neutral. Successful and unsuccessful approaches have short caches, with bounded retries and world-change invalidation. Chest scans inspect up to 64 cells per totem visit, finish a fresh pass before confirming missing/full storage, and recheck current handlers during every actual transfer. Ordinary workers do not force-load chunks.

Species JSON bounds normal trees by height/spread/log count. Associated foliage ownership checks preserve shared crowns. Connected falls try four directions at the lowest safe common resting height, up to eight blocks above or below the root. The tree keeps its original trunk and branch spacing; some sections can bridge gaps while other sections rest on solid terrain or obstacles. Planned cells cannot duplicate another section or an active timber job. If the complete layout cannot fit, no source blocks are transferred. Older stacked jobs pause and replan without losing their remaining ledger. The entire model rotates continuously into its final block orientation. All falls turn into real sideways log blocks at landing; Lumberton jobs retain a hidden persistent processing ledger for those exact blocks. Removed/replaced blocks are reconciled without extra loot, and pending inventory remainders survive reloads. Workers preflight falls before travel and can clear up to 128 permitted obstructing blocks in the least-disruptive valid plan. Failed standing-tree approaches are deferred for 400 ticks. Harvest deliveries happen between trees at 128 output items or 1,200 ticks with partial output; supply reserves do not count. Client/server timber audits run every 100 ticks in batches of 32 known identities. Dropped log stacks inside the work range use bounded nearest-first, line-of-sight and reachable-path searches every 40 ticks; collecting items requires no chopping time.

## Logistics and compatibility

Runnertons retain 18 default cargo slots, a 0.215 walking attribute and their backpack. Reports reconcile every forty ticks, sampling 512 slots per home network; batches cap at 256 items. Overlapping ranges share physical containers. Tools, Hammers, torches and saplings remain in chests for local workers.

Lumbertons retain up to sixteen saplings per type and a stack of dirt or planks for climbing. Paused miners hold position. Quarry starts at the platform edge; tunnel miners claim separate branches. Construction now allows one successful action every two ticks, half the previous placement rate; forestry and mining speeds remain unchanged. Each building also uses one temporary chest item, recovered during cleanup.

Serialized configuration keys `retainMagicaOnDeath` and `magicaYields`, old reward tag paths and core/orb registry IDs remain deliberate compatibility aliases. Player-facing names are Lumina; existing settings and items are preserved.

Forestry pickaxe assistance is bounded to 256 pending obstacle cells per Network. Requests expire after five minutes without renewal, survive saves, and are cancelled if the original block is replaced, the tree is removed, or its worksite is removed. Only active, unpaused Miningtons from the same owner/home/dimension accept them; claims renew while working. One helper owns each cell. Tool tier, cargo capacity, protection, fluid and falling-material checks precede real removal; ordinary player-equivalent mining duration and one durability action apply. Unreachable work backs off for 20 seconds; one assistance attempt is capped at two minutes. Mine cursors and tunnel assignments remain intact. New forestry climbing supports use dirt or planks, never cobblestone.
