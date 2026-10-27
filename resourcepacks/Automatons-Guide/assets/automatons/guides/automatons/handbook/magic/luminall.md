---
navigation:
  title: "Luminall and the Collector"
  parent: "magic/index.md"
  position: 5
  icon: "automatons:lumina_collector"
item_ids:
  - "automatons:lumina_drain"
  - "automatons:lumina_collector"
  - "automatons:tank"
  - "automatons:luminall_bottle"
  - "automatons:luminall_bucket"
  - "automatons:dead_plant"
  - "automatons:dead_crop"
---

# Luminall, collectors and upkeep

The fixed conversion is **one Lumina point = two mB**. A full bottle holds **250 mB = 125 points**, a bucket **1,000 mB = 500 points**, and an 8,000 mB tank holds **4,000 points**. The Machine's engine capacity remains 10,000 points.

<Recipe id="automatons:tank" />
<Recipe id="automatons:lumina_collector" />

The Collector recipe uses four Stone, **two Infused Lumite Shards** and an empty Tank. Its reservoir holds 4,000 mB by default. It produces at most **five mB every 100 ticks**, one quarter of the original rate. Per-source fractions accumulate; low-yield plants still produce over time. Overlapping collectors share one source budget, and full collectors stop extracting.

## Source footprint and exhaustion

The world-aligned footprint is 4×4: X/Z offsets −1 through +2, with four blocks of vertical reach in either direction. Eligible sources include flowers, mature supported crops, ripe berries, stems, pumpkins/melons, grass and recognized living trees. Planted vegetation can contribute; structural wood and immature crops do not. Tall plants are one source.

Non-tree plants begin with 200 lifetime Lumina and crops with 100, configurable through plantLifetimeLumina and cropLifetimeLumina. Only actual extraction spends that reserve. Growth and reloads preserve it. Exhaustion replaces the source with **Dead Plant** or **Dead Crop**. These blocks do not grow, accept bone meal or drop anything when broken. Trees and leaves are never killed by collector depletion.

## Containers and recharge

Place a Glass Bottle on a collector by right-clicking; it fills from actual stored fluid. Retrieve it with an empty hand. Players can drink partial bottles proportionally. Automatons require a full bottle, use it for 32 ticks and return an empty Glass Bottle. Any worker-meter overflow is spent.

Buckets transfer 1,000 mB. Compatible pipes extract through collector sides/bottom; its top is reserved for the bottle. Tanks accept compatible registered fluids on all faces and never mix fluids. Shift-right-click a Tank with the Wand to carry its contents. Breaking a Tank normally drops an empty tank and spills its fluid; a Collector retains its stored fluid and bottle when broken.

<Recipe id="automatons:lumina_drain" />

Place a Lumina Drain above a Tank and stand on it to convert personal energy at **five points per second = ten mB**. Full or incompatible tanks refuse the transfer. Fractional energy remains conserved.

[Luminariton service](../professions/luminariton.md) maintains collectors, bottles, living plants and healing stock.
