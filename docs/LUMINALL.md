# Luminall and the Lumina Collector — 0.8.1

The approved Collector brief is preserved in `reference/luminall-collector-specification.txt`. The user's explicit Tank and Collector recipes replace the brief's unspecified recipe slots. Existing `automatons:magica_core` is the charged **Lumina Core**; no duplicate core is added.

## Recipes

Tank: `GGG / GEG / GGG`, with Glass blocks and the existing Empty Core. Output: one Tank.

Collector: `SCS / CTC / SCS`, with Stone, the existing Lumina Core and the Tank. Output: one Lumina Collector. Combined cost: **4 Stone, 8 Glass blocks, 4 infused Lumina Cores and 1 Empty Core**. The recipe refuses a Tank containing fluid so crafting cannot discard stored value. Its custom serializer still exposes a normal shaped crafting recipe to JEI and GuideME.

## Defaults and economy

All new balance values are under `[luminall]` in the world's `serverconfig/automatons-server.toml`.

| Setting | Default |
| --- | --- |
| Horizontal footprint | Exactly 4 × 4; X and Z offsets −1 through +2 |
| `verticalReach` | 4 above and 4 below, 9 scanned Y levels |
| `collectorCapacityMb` | 4,000 mB |
| `tankCapacityMb` | 8,000 mB |
| `collectionIntervalTicks` | 100 ticks / 5 seconds at 20 TPS |
| `maximumMbPerCollection` | 20 mB per Collector per interval |
| `bottleVolumeMb` | 250 mB |
| `luminaPerBottle` | 5 Lumina |

`[luminall.producerMbPerCollection]` defaults: logs/crops/fruit = 3; flowers/berries = 2; leaves/grass/stems = 1. Zero disables a category. Eligibility reuses the existing `automatons:magica/*` block tags and tree species data. Crops and berries must be mature; player-planted vegetation is eligible. Structural logs are rejected through the existing rooted-tree/canopy recognition and placed-log history. Tall flowers contribute once, through their lower block.

At maximum output, a Collector produces four mB/second: a 250 mB bottle every 62.5 seconds on average, restoring up to 4.8 Lumina/minute. The four charged cores represent 400 Lumina invested, roughly 83 minutes of capped production before paying that energy back. Multiple non-overlapping gardens can scale production. Bottles draw at five mB per server tick when fluid is already stored; this transfer speed does not generate extra fluid.

## Conservation and lifecycle

Every collection interval, active loaded Collectors share one per-position producer map. Each producer's finite configured output is divided across recipients with available capacity and collection budget, rotating allocation between intervals. A full Collector cannot reserve or block a producer. The volume is scanned once per interval, not every tick; all accesses first check loaded chunks. Collection changes no plant blocks and creates no chunk tickets.

Collector capability: standard Forge `FLUID_HANDLER`, extraction only, on sides/bottom and unsided queries. Top access is absent. Tank capability: standard bidirectional handler, all faces, one compatible fluid at a time. Buckets use Forge's normal transactional container transfer; one source/bucket is 1,000 mB. Luminall registers source/flowing forms, FluidType, placeable LiquidBlock, BucketItem, fluid tag and client still/flow rendering. Source regeneration and hydration are disabled. Flowing cells cannot yield source buckets. Bottles never extract from flowing world cells.

The bottle on top is a real ItemStack. Partial contents, fixed full volume and full reward are saved on that item, including after retrieval and reinsertion. Standard fluid-container capabilities expose those same contents. Full or partial bottles can be drunk; proportional rewards retain fractions of a third in the saved ledger. Every drink returns a Glass Bottle, including creative. Hunger does not matter; a bank without room for the proportional reward refuses consumption. Lumina uses the existing balance/sync API. Existing bottles keep their configured volume/reward across later configuration changes.

Block entities persist Tank and Bottle NBT. Collector loot copies both into its dropped BlockEntityTag; placing the block restores them. Creative Collector removal also recovers a nonempty Collector once. Tanks require Wand packing to retain fluid; ordinary breaking spills it. Pistons cannot move machines. Lowering capacity does not truncate already-stored fluid: extraction remains available, filling waits until below the new limit. Tanks with contents cannot be used in the Collector recipe.

## Appearance and documentation

Code-authored block models reuse native vanilla stone and glass textures with one texel per model pixel. The Collector has a stone frame; the Tank uses glass throughout. Their break particles match those materials. Luminall uses Minecraft's animated water still/flow sprites tinted pale Lumina purple (`#D9BBF5`). No existing game PNG was modified. The reservoir renderer shows actual fluid height, and a glass bottle above the Collector displays its actual fill. Two restrained motes travel from a contributing producer per collection; full reservoirs show no collection effects. Looking at a Collector with a Wand renders the same exact bounds used by the scanner.

The external editable handbook adds `magic/luminall.md` with live recipes, defaults and usage. Normal handbook installation preserves user-edited pages.

## Other changes in this release

Deepslate Lumite has a separate configured and placed feature, exclusively replacing deepslate between −64 and 0 with triangular peak −32. Stone keeps its −32..64 distribution. Cluster geometry is centered on block centers instead of biasing the distribution downward; JER now registers separate variant estimates. Existing chunks and ore are untouched.

The Infuser does not automatically retrieve a completed shard into an empty offhand. Main-hand retrieval, partial raw shards and deliberate manual hand swapping remain supported. Floating shard centers are raised by exactly three model pixels (3/16 block).

Buildingtons stage whatever material load is available in their temporary chest and start using it. Missing later materials keep the same project claim, then trigger local replenishment/fetching. Full-kit availability is no longer a gate. The prior two-tick action rate, perimeter movement and chest cleanup remain.

Verification results and any unperformed in-game checks are recorded in `VERIFICATION.md` and the versioned release manifest.

Hammer durability is now **2,000**. Existing damage remains on used Hammers; the higher maximum applies to them too. Door-gap alignment now applies to every Automaton, fixing Builders catching their bodies on an open shed door and retrying the route.

The main Pedestal's **Build Lists** button lists unfinished buildings and active mine infrastructure in its Network. Each opens the same live material accounting used by worker Build Lists. Completed projects disappear, paused projects remain, and unloaded building sites show their location without forcing chunks or inventing stock counts.

## Collector appearance in 0.8.3

The supplied reference is recreated as a stone pedestal and open basin with a raised central bottle stand. Lavender fluid follows the actual fill level around the stand; a placed bottle retains its own fill level and has glass highlights and a wooden stopper. The collision matches the stone geometry. The Tank, recipes, capacity, producer range and filling behavior are unchanged. No copper is used.


## Portable Tanks and Lumina Drain (0.9.1)

Shift-right-click a Tank with a Wand to retain its fluid. Ordinary removal drops an empty Tank and a cosmetic, uncollectable puddle that evaporates after 60 ticks; it cannot create a spreading or scoopable source. Collectors retain their existing packed loot behavior. Filled Tank items expose a fluid-container capability and transfer Luminall directly to the Machine on right-click, preserving excess. Machine refueling also accepts partial bottles and preserves sub-third fuel across saves.

The craftable Lumina Drain (three Iron Bars over Stone / Empty Core / Stone) requires a Tank below it. Each second, a grounded player directly on the plate transfers up to five Lumina at the default bottle conversion, stopping when they step off, the balance is exhausted, or the Tank is full/incompatible. Integer mB/third quanta prevent rounding from creating energy under custom conversion settings.
