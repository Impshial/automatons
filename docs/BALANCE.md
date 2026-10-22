# Recipes and provisional balance — 0.2.0

This is the single design reference for initial recipes and configurable values. Server configuration is `serverconfig/automatons-server.toml` inside each world. Recipes, source tags and species definitions live in the mod data pack. All values are provisional.

## Survival recipes

Every recipe produces one item. `—` is an empty crafting-grid slot. All charged cores are consumed; partial cores cannot substitute. The Infusing Pedestal and empty core use only vanilla materials.

### Automaton

At the Automaton Pedestal: Head above; Arm / Automaton Core / Arm across the middle; two Legs below. Take one Automaton result after inserting all six components. No extra Magica cost.

### Automaton Arm

Shapeless: 1 × Overworld log / wood (including stripped), 1 × Iron Ingot.

### Automaton Core

| Left | Center | Right |
| --- | --- | --- |
| Iron Ingot | Magica Core | Iron Ingot |
| Redstone | Redstone | Redstone |
| Iron Ingot | Magica Core | Iron Ingot |

### Automaton Head

Shapeless: 2 × Overworld log / wood (including stripped), 2 × Iron Nugget, 1 × Redstone.

### Automaton Leg

Shapeless: 1 × Overworld log / wood (including stripped), 1 × Overworld plank, 1 × Iron Ingot.

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

## Magica yields

| Category | Min | Max |
| --- | ---: | ---: |
| logs | 3 | 7 |
| crops | 2 | 4 |
| fruit | 2 | 4 |
| berries | 1 | 3 |
| flowers | 1 | 2 |
| stems | 0 | 2 |
| leaves | 0 | 1 |
| grass | 0 | 1 |

One inclusive uniform roll per completed eligible player break. A reversed configured range is corrected by raising its maximum to its minimum, with a log entry. Ordinary XP and Mending are unaffected. Display levels use the vanilla XP level curve, but all costs and stored balances are exact independent points.

## Server settings

| Key under `provisional` | Default | Meaning |
| --- | --- | --- |
| `storageRadius` | 4 | Outward horizontal distance; 9×9 base square |
| `storageHeight` | 4 | Vertical chest allowance ±4 |
| `lumberRadius` | 16 | Root-center square; 33×33 base footprint |
| `rootHeight` | 16 | Separate root-height allowance ±16 |
| `cargoSlots` | 9 | New worker cargo capacity, 1–27; existing larger inventories are retained |
| `walkingAttribute` | .215 | Measured flat-ground default ≈1.996 blocks/second |
| `ironAxePreparationTicksPerBaseLog` | 80 | Four seconds per root-base cut with an unenchanted iron axe |
| `ironAxeProcessingTicks` | 40 | Two seconds per log section with an unenchanted iron axe |
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
| `displayHeight` | 1.75 | Display center above pedestal origin |

Magica minima/maxima live under `magicaYields.<category>.minimum/maximum`. Numeric ranges are validated by Forge. Old worlds preserve existing configured scan budgets rather than silently replacing user settings.

## Upgrades and work formula

Storage Range I/II adds 4/8 total blocks; default radii become 8/12 (17×17 and 25×25). Lumber Range I/II adds 8/16 total blocks; radii become 24/32 (49×49 and 65×65). Neither changes vertical allowances. One installed item per family affects every compatible assigned worker. Duplicate family slots are rejected.

Work duration is `max(10, round(baseTicks × 6 / max(1, toolSpeed + efficiencyBonus) × (1 − 0.2 × speedTier)))`. `efficiencyBonus` is zero at Efficiency 0, otherwise `effectiveEfficiency² + 1`. Effective Efficiency is the larger of the actual enchantment and the totem tier, never their sum. No permanent enchantment is written to the axe. Speed I/II shortens work by 20%/40% total. Movement and visual fall are separate. One tool-durability action is charged per required base cut and one per completed timber section; Unbreaking remains normal.

## Dispatch, recovery and safety budgets

Discovery checks closer columns and all configured root heights before releasing farther jobs. A per-dimension standing-tree claim prevents overlapping totems felling the same wood. Standing claims rebuild on load; committed timber owns its own persistent consumed-section and pending-loot state. One active worker claims a timber job. Packing/recall releases it immediately; an unavailable prior claimant is released after 200 loaded timber ticks.

Global navigation attempts are capped at 32 per tick, fall plans at two, and conversions at one. Deferred work stays neutral. Successful and unsuccessful approaches have short caches, with bounded retries and world-change invalidation. Chest scans inspect up to 64 cells per totem visit, finish a fresh pass before confirming missing/full storage, and recheck current handlers during every actual transfer. No chunks are force-loaded.

Species JSON bounds normal trees by height/spread/log count. Associated foliage ownership checks preserve shared crowns. Full falls try four directions; compact fallback keeps all unconsumed sections in one persistent job and exposes grounded sections in order.

## Survival dependency total

Before optional upgrades, a first full setup needs six completed cores (300 Magica): two in the home pedestal, one in the Totem, one in the Wand, and two in the Automaton Core body component. Crafting axes is separate. All component and upgrade recipes fit the ordinary 3×3 grid except the explicitly documented six-part pedestal assembly.
