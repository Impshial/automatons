# Lumina and Lumite — 0.7.0

Lumina is the renamed independent energy balance. Existing points, exact fractional thirds, acquisition rewards, orb sizes, XP and Mending behavior are preserved. Mining Lumite produces material, not energy.

## Mine, infuse, craft

Mine Lumite between Y −32 and 64 in new eligible Overworld terrain with a stone pickaxe or better. Stone and deepslate variants drop 1–3 raw shards, with normal Fortune bonuses; Silk Touch gives the corresponding block. Its pale-purple veins remain visible in darkness without lighting nearby blocks.

Craft the Infusing Pedestal with two iron ingots, two polished diorite, three polished deepslate and one diamond. Right-click one of its four raised corners with a raw shard. Each corner accepts exactly one. Stand within four blocks with Lumina available. Each shard costs 25 Lumina; all four corners share the original rate of one point every four ticks. At 20 TPS, one shard takes five active seconds and four take twenty. Different players can use different corners and only pay for their own shards.

Empty-hand right-click retrieves your selected item; an operator may retrieve another donor's. Partially infused shards retain exact paid thirds and do not stack. Reinsertion pays only the remainder. Out-of-range, offline, dead or empty-balance donors pause. Green particles and a chime announce each completion. Breaking the pedestal drops its real contents once.

Craft an Empty Core from four glass around one raw uncharged shard. Craft a Lumina Core with four **Infused** Lumite Shards in a cross around that Empty Core. No extra energy is deducted at crafting. The Automaton Torso is still a separate body component. See [all recipe changes](RECIPE-CHANGES-0.6.md).

## Existing saves

- Energy balances and old completed cores keep their existing serialized identities. Completed cores remain usable with no retroactive payment.
- An old occupied infuser preserves its core separately from the new corners. Empty-hand use returns that item before ordinary corner retrieval.
- A partially infused old core retains its paid amount. If its infuser recorded a donor, that donor can right-click the returned core once to recover the exact credit. The Empty Core remains and can then be used in crafting. Repeated use or reload cannot refund again.
- If no donor was recorded, the credit stays on the core. The assignment recovery command has been retired; unowned credit is preserved without inventing a donor or refund.
- Existing upgrade items stay registered but inert. All five families, both tiers, and the unused Upgrade Plate have no crafting, creative/JEI listing, installation slot or effect. Opening an owned Totem returns installed contents to inventory; overflow drops once. Existing carried items remain inert. No speculative material or energy refunds are created.
- Workers, identities, jobs, buildings, inventories, tool enchantments, backpacks, base construction speed and one-range worksite settings remain intact.

The old `magica_core` / `empty_magica_core` / `magica_orb` registry IDs, reward tag paths, `retainMagicaOnDeath` and `magicaYields` configuration keys are intentional compatibility aliases. Player-facing text uses Lumina. Avoid renaming saved registry IDs manually.

## Generation and retired commands

New eligible Overworld chunks use separate stone and deepslate features, each with eight attempts and configured vein size 8. Stone uses triangular Y -32..64 with peak 16; deepslate uses -64..0 with peak -32. Each replaces only its matching rock type. Size is not a guaranteed cluster count. Existing terrain is never refilled.

Automatons registers no commands. The former retrogen scheduler and recovery command are removed; pending jobs cannot resume automatically, acquire tickets or modify terrain. Completed and pending save records are retained, including unknown fields, without executing them. The legacy exclusion config is inert and cannot enable a job.

JER 1.4.0.247 is optional client integration. Its separate stone and deepslate entries use deterministic candidate-placement estimates from the bundled feature JSON and actual cluster algorithm. It is explicitly not a measured-world histogram. JER loads this registration before a world is available and its public API does not synchronize remote worldgen overrides; the handbook explains that data packs can differ. Actual drops remain in loot tables.

## Hammer

The native 16×16 iron mallet has 2,000 durability and keeps its original recipe and builder timing. Player-held mining uses normal positive crack progress at speed 0.5, slower than a wooden pickaxe. It harvests only wooden-pickaxe-tier blocks; higher-tier ores can be broken but yield no drops or mining XP. It is not accepted as a Minington tool.

