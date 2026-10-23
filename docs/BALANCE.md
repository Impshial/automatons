# Recipes and provisional balance — 0.2.4

This is the single design reference for initial recipes and configurable values. Server configuration is `serverconfig/automatons-server.toml` inside each world. Recipes, source tags and species definitions live in the mod data pack. All values are provisional.

## Survival recipes

Every recipe produces one item. `—` is an empty crafting-grid slot. All charged cores are consumed; partial cores cannot substitute. The Infusing Pedestal and empty core use only vanilla materials.

### Automaton

At the Automaton Pedestal: Head above; Arm / Automaton Torso / Arm across the middle; two Legs below. Take one Automaton result after inserting all six components. No extra Magica cost.

### Automaton Arm

Shaped vertical: Log above Iron Ingot. Fits all six 1×2 placements in the 3×3 grid.

### Automaton Torso

| Left | Center | Right |
| --- | --- | --- |
| Log | Iron Ingot | Log |
| Full Magica Core | Redstone | Full Magica Core |
| Log | Iron Ingot | Log |

### Automaton Head

| Left | Center | Right |
| --- | --- | --- |
| Log | Planks | Log |
| Iron Nugget | Redstone | Iron Nugget |
| Log | Iron Ingot | Log |

### Automaton Leg

Shaped vertical: Log above Planks above Iron Ingot. Fits any of the three columns.

### Efficiency 1

Shapeless: 1 × Upgrade Plate, 1 × Book, 2 × Lapis Lazuli, 1 × Amethyst Shard, 1 × Magica Core.

### Efficiency 2

Shapeless: 1 × Efficiency 1, 1 × Book, 2 × Lapis Lazuli, 2 × Amethyst Shard, 1 × Diamond, 2 × Magica Core.

### Empty Magica Core

| Left | Center | Right |
| --- | --- | --- |
| — | Glass | — |
| Glass | Amethyst Shard | Glass |
| — | Glass | — |

### Infusing Pedestal

| Left | Center | Right |
| --- | --- | --- |
| Iron Ingot | — | Iron Ingot |
| Polished Diorite | Amethyst Shard | Polished Diorite |
| Polished Deepslate | Polished Deepslate | Polished Deepslate |

### Lumber 1

Shapeless: 1 × Upgrade Plate, 1 × Compass, 4 × Overworld log / wood (including stripped), 1 × Magica Core.

### Lumber 2

Shapeless: 1 × Lumber 1, 1 × Compass, 2 × Iron Ingot, 2 × Amethyst Shard, 2 × Magica Core.

### Pedestal

| Left | Center | Right |
| --- | --- | --- |
| Stone Bricks | Magica Core | Stone Bricks |
| Iron Ingot | Gold Ingot | Iron Ingot |
| Stone Bricks | Magica Core | Stone Bricks |

### Speed 1

Shapeless: 1 × Upgrade Plate, 4 × Sugar, 2 × Redstone, 1 × Magica Core.

### Speed 2

Shapeless: 1 × Speed 1, 2 × Sugar, 2 × Redstone, 1 × Diamond, 2 × Magica Core.

### Storage 1

Shapeless: 1 × Upgrade Plate, 1 × Chest, 1 × Compass, 1 × Magica Core.

### Storage 2

Shapeless: 1 × Storage 1, 1 × Chest, 2 × Iron Ingot, 2 × Amethyst Shard, 2 × Magica Core.

### Totem

| Left | Center | Right |
| --- | --- | --- |
| — | Magica Core | — |
| Iron Ingot | Overworld log / wood (including stripped) | Iron Ingot |
| — | Stone Bricks | — |

### Upgrade Plate

Shapeless: 2 × Iron Ingot, 2 × Redstone, 1 × Amethyst Shard.

### Wand

| Left | Center | Right |
| --- | --- | --- |
| — | — | Magica Core |
| — | Stick | — |
| Stick | — | — |

## World infusion

An Empty Magica Core holds 0–49 points; 50 converts it to the distinct completed Magica Core item. Empty and completed cores stack; partial cores do not. One point transfers each four server ticks, within a four-block radius from the displayed core, with an online living donor in the same dimension. Fifty points require 200 active ticks at the default cadence. Partial progress persists; unloaded time grants no catch-up.

## Magica yields and orb sizes

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

One inclusive uniform large-value roll per completed eligible player break. A small orb stores exactly one-third of that roll. Flowers/grass produce one small orb; crops produce one orb with a 10% large chance; logs produce two small orbs plus a 40% chance of one large orb. Fruit, berries, stems and leaves keep their earlier rewards. A zero roll produces no entities. Small orbs render at half the large diameter and merge only with matching size/value. Existing saved orbs stay large. Whole points plus a saved 0–2 thirds remainder preserve exact fractions across reloads; infusion still consumes 50 whole points. Numeric bar labels show only whole points, without rounding fractions into spendable points. A reversed configured range is corrected by raising its maximum to its minimum, with a log entry. Ordinary XP and Mending are unaffected. Display levels use the vanilla XP level curve, but all costs and stored balances are exact independent points.

## Server settings

| Key under `provisional` | Default | Meaning |
| --- | --- | --- |
| `storageRadius` | 4 | Outward horizontal distance; 9×9 base square |
| `storageHeight` | 4 | Vertical chest allowance ±4 |
| `lumberRadius` | 16 | Root-center square; 33×33 base footprint |
| `rootHeight` | 16 | Separate root-height allowance ±16 |
| `cargoSlots` | 9 | New worker cargo capacity, 1–27; existing larger inventories are retained |
| `walkingAttribute` | .215 | Measured flat-ground default ≈1.996 blocks/second |
| `ironAxePreparationTicksPerBaseLog` | 80 | Baseline before the additional 20 Stone/Iron ticks; final Iron preparation is 5 seconds |
| `ironAxeProcessingTicks` | 40 | Baseline before the additional 20 Stone/Iron ticks; final Iron processing is 3 seconds |
| `fallAnimationTicks` | 30 | 1.5-second visual fall, separate from work upgrades |
| `serverScanBudgetPerTick` | 4096 | Shared scan/validation units |
| `totemScanBudgetPerTick` | 256 | Incremental tree-search cells per visit |
| `infusionTicksPerPoint` | 4 | Active ticks per exact transferred Magica point |
| `infusionRange` | 4 | Donor radius in blocks |
| `retainMagicaOnDeath` | true | Retain independent balance on death |
| `logWorkTransitions` | false | Optional transition/skip diagnostics, at most one message/second/worker |
| `displayTiltDegrees` | 30 | Permanent item-display lean |
| `displayRotationSeconds` | 8 | One full Y rotation |
| `displayScale` | .7 | Display item scale |
| `displayBobAmplitude` | .04 | Gentle vertical bob |
| `displayHeight` | 1.4 | Display center above pedestal origin |

Magica minima/maxima live under `magicaYields.<category>.minimum/maximum`. Numeric ranges are validated by Forge. Old worlds preserve existing configured scan budgets rather than silently replacing user settings.

## Upgrades and work formula

Storage Range I/II adds 4/8 total blocks; default radii become 8/12 (17×17 and 25×25). Lumber Range I/II adds 8/16 total blocks; radii become 24/32 (49×49 and 65×65). Neither changes vertical allowances. One installed item per family affects every compatible assigned worker. Duplicate family slots are rejected.

Unenchanted work time is `baseTicks × 6 / toolSpeed + extraTicks`. Extra ticks are 10 for Diamond/Netherite tier, 20 for Stone/Iron tier, and zero for Wood/Gold. Efficiency scales this by `toolSpeed / (toolSpeed + efficiencyBonus)`, and Speed I/II by 0.8/0.6. `efficiencyBonus` is zero at Efficiency 0, otherwise `effectiveEfficiency² + 1`; effective Efficiency is the larger of enchantment and totem tier. The result rounds to the nearest five-tick work interval with a ten-tick minimum. Preparation repeats that duration for each required base cut.

| Axe | Preparation per base log | Fallen/loose log processing |
| --- | ---: | ---: |
| Wood | 12.00 s (240 ticks) | 6.00 s (120 ticks) |
| Stone | 7.00 s (140 ticks) | 4.00 s (80 ticks) |
| Iron | 5.00 s (100 ticks) | 3.00 s (60 ticks) |
| Diamond | 3.50 s (70 ticks) | 2.00 s (40 ticks) |
| Netherite | 3.25 s (65 ticks) | 1.75 s (35 ticks) |
| Gold | 2.00 s (40 ticks) | 1.00 s (20 ticks) |

These defaults assume 20 TPS, no enchantments or upgrades, and exclude walking, dispatch and the 30-tick fall animation. Workers keep support for all six vanilla axe materials. Player-triggered natural tree felling requires stone tier or higher (wood/gold excluded). One durability action is charged per base cut and processed log; Unbreaking remains normal. Loose/placed logs use processing time directly, with no preparation or falling animation.


## Dispatch, recovery and safety budgets

Discovery checks closer columns and all configured root heights before releasing farther jobs. A per-dimension standing-tree claim prevents overlapping totems felling the same wood. Standing claims rebuild on load; committed timber owns its own persistent consumed-section and pending-loot state. One active worker claims a timber job. Packing/recall releases it immediately; an unavailable prior claimant is released after 200 loaded timber ticks.

A route search considers all valid work positions around a target together. Workers check actual movement every 20 ticks; after 40 stalled ticks they invalidate the route and avoid its next node for 80 ticks. Unsafe timber is deferred after 60 ticks; an unreachable timber approach is deferred after 100 ticks, with a 200-tick job cooldown. They can work on another reachable log during that interval. Idle workers can unload cargo while the area scan continues.

Global navigation attempts are capped at 32 per tick, fall plans at two, and conversions at one. Deferred work stays neutral. Successful and unsuccessful approaches have short caches, with bounded retries and world-change invalidation. Chest scans inspect up to 64 cells per totem visit, finish a fresh pass before confirming missing/full storage, and recheck current handlers during every actual transfer. No chunks are force-loaded.

Species JSON bounds normal trees by height/spread/log count. Associated foliage ownership checks preserve shared crowns. Connected falls try four directions at the lowest safe common resting height, up to eight blocks above or below the root. The tree keeps its original trunk and branch spacing; some sections can bridge gaps while other sections rest on solid terrain or obstacles. Planned cells cannot duplicate another section or an active timber job. If the complete layout cannot fit, no source blocks are transferred. Older stacked jobs pause and replan without losing their remaining ledger. The entire model rotates continuously into its final block orientation. Player-triggered falls turn into real blocks at landing; Lumberton-triggered falls retain their persistent processing ledger. Dropped log stacks inside the work range use bounded nearest-first, line-of-sight and reachable-path searches every 40 ticks; collecting items requires no chopping time.

## Survival dependency total

Before optional upgrades, a first full setup needs six completed cores (300 Magica): two in the home pedestal, one in the Totem, one in the Wand, and two in the Automaton Torso body component. Crafting axes is separate. All component and upgrade recipes fit the ordinary 3×3 grid except the explicitly documented six-part pedestal assembly.

Connected-fall clearance accepts non-colliding plants and natural, unplaced leaves. Their loot joins the existing foliage ledger. Both halves of tall plants are removed together with one drop calculation. Other solid blocks remain obstacles. A failed fall is deferred after 60 ticks and shared across the Totem for 400 ticks; changes to the work area invalidate this retry delay.
