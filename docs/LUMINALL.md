# Luminall and source reserves
The fixed conversion is **2 mB per Lumina point**: 250 mB bottle = 125 points, 1,000 mB bucket = 500, 8,000 mB Tank = 4,000. The Machine stores 10,000 points. Fractional accounting prevents rounding gains.

## Workers
Workers start with 200 operating points, spending one per 1,200 loaded ticks. Partial intervals persist; sleep and world-time changes do not advance the clock. At two points or less they seek refill. At zero they stop with unlit eyes and a depleted pose.

A worker drinks one full bottle over 32 ticks, restores 125 points up to capacity, wastes overflow and returns glass. It does not heal. Players retain partial-bottle consumption; Machines take only fluid that fits and preserve container excess.

Luminaritons have a separate 400-point service reserve, initially empty. Operating refill takes priority. Healing spends two service points per health point, up to one health point per second per eligible nearby same-network recipient, including self. Allocation prioritizes the lowest health fraction fairly. Service refill begins at 125 points or below. A bottle charges only one pool.

## Collector and Tank
Tank: eight Glass around an Empty Core. Collector: `SIS / .T. / SIS`, using four Stone, two Infused Lumite Shards and an empty Tank. Filled Tanks are rejected.

Default Collector footprint is 4×4, reaching four blocks above/below. Capacity is 4,000 mB; Tank capacity is 8,000. The cap is **5 mB per 100 loaded ticks**. Category rates are one quarter of their previous values: logs/crops/fruit 0.75 mB, flowers/berries 0.5, leaves/grass/stems 0.25 per eligible source per interval. Fractional production persists. A capped Collector fills a bottle in 250 seconds.

Overlapping Collectors share each source's output, rotating allocation among recipients with room. Full or paused collectors do not drain reserves. Collection never force-loads chunks.

## Eligibility and exhaustion
Existing Lumina tags, crop maturity and tree recognition define sources. Mature wheat, carrots, potatoes, beetroot and nether wart qualify through crop tags. Ripe berries and eligible fruit, flowers, grass, stems, leaves and tree logs retain their categories. Planted vegetation can feed Collectors; structural logs cannot. Tall flowers count once from the lower block.

Eligible non-tree plants start with 200 source points; crops start with 100. Collection debits exact produced energy. Growth and reload do not replenish reserves. Exhaustion replaces the source with Dead Plant or Dead Crop without living-plant drops. Trees are immune to exhaustion.

Dead blocks have static artwork and supported placement items. **Breaking them drops nothing**, including with shears, Silk Touch or Fortune. They produce no Lumina, cannot grow and reject bone meal. Farmers and mages clear dead cells and replant using actual stock in managed areas. Mages do not plant or grow trees.

## Transfers
Tanks support registered fluids, including milk, and reject mixing. Wand packing preserves contents; ordinary breaking spills fluid. Collectors expose extraction from sides/bottom, with no top capability. Bottles retain actual partial contents.

The Lumina Drain transfers up to five player points per second into a Tank below: 10 mB at the current conversion. It stops on stepping off, depleted energy or full/incompatible storage. Fractional remainders are conserved.
