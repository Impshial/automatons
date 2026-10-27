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
---

# Luminall and the Collector

The **Lumina Collector** quietly condenses Lumina from a nearby garden into pale purple **Luminall**. Plants stay intact; no fuel or external power is needed.

<Recipe id="automatons:tank" />
<Recipe id="automatons:lumina_collector" />

The Collector uses **four Lumina Cores** (the existing infused cores) and a crafted Tank. Total cost is four Stone, eight Glass blocks, four Lumina Cores and one Empty Core. Empty a Tank before using it in the Collector recipe so its stored fluid is not lost.

## Garden and range

Hold the Automaton Wand and look at the Collector to see its boundary. The horizontal footprint is exactly **4 × 4**: offsets **−1 through +2 on X and Z**, extending farther east and south. Default vertical reach is **four blocks above and four below**.

Flowers, fruit such as pumpkins and melons, mature crops and berries, stems, grass and recognized living trees contribute. Planted vegetation counts; wooden structures do not count as living trees. Immature crops contribute nothing until mature. Additional plants increase output, up to the rate limit.

Collectors sharing a plant divide that plant's output. A full Collector yields its share to others with room. Spread Collectors across more garden space to increase total production. Nothing force-loads chunks.

## Storage and bottles

The Collector stores **4,000 mB** by default. Its visible reservoir rises as it fills; at capacity it pauses until fluid is removed. Every five seconds, it collects at most **20 mB**. Default per-plant contributions per collection are three mB for logs, mature crops and fruit; two for flowers and mature berries; one for leaves, grass and stems.

Right-click with a Glass Bottle to put one on top. The physical bottle fills from stored fluid, at five mB per tick. An empty-hand click retrieves it. Partial bottles retain their exact contents, can be put back, and can be drunk for a proportional reward.

A full bottle holds **250 mB** and restores **five Lumina**, even at full hunger. Drinking always returns an empty Glass Bottle, including in creative mode. Fractions from partial bottles accumulate without loss. If your Lumina bank has no room for the full reward, drinking is refused. Existing bottles retain their volume and reward if server settings change.

## Buckets, pipes and Tanks

An empty bucket collects **1,000 mB** when that much is available. Luminall Buckets can place fluid sources in the world; only actual sources can be picked up. Luminall does not regenerate infinite sources.

Compatible Forge pipes can extract from the Collector's sides and bottom. The top is reserved for its bottle. The Collector is output-only; use a **Tank** for general fluid input and output. Tanks hold **8,000 mB** by default, support all faces and compatible fluids, and do not mix different fluids.

**Shift-right-click a Tank with the Wand** to carry it with its fluid intact. Place it again or right-click the Machine with the filled Tank to transfer its fuel. Breaking a Tank normally drops an empty Tank and spills its fluid; the uncollectable puddle evaporates in three seconds. A Collector still retains its reservoir and bottle when broken. Server settings control reach, capacities, interval, rate limit, category contributions, bottle volume and Lumina reward.


## Lumina Drain

<Recipe id="automatons:lumina_drain" />

Craft three Iron Bars above Stone, Empty Core, Stone. Place the Drain directly on top of a Tank, then stand on it to convert your personal Lumina into Luminall. At default settings it moves **five Lumina per second (250 mB)**. Step off to stop. It stops automatically when your usable Lumina runs out, the Tank is full, or the Tank contains a different fluid. Fractional Lumina below the next whole transfer remains yours.

Use the Wand to carry that filled Tank to the Machine and right-click the Machine to refuel in bulk.
