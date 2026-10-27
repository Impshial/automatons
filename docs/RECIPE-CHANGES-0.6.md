# Recipe changes — 0.5.0 → 0.6.0

Compared against the archived 0.5.0 JAR and current recipe JSON. All outputs are one item; the Automaton uses pedestal assembly. The old completed core was called Magica Core; its unchanged registry now displays Lumina Core. Layouts of surviving existing recipes are unchanged.

| Output | 0.5.0 ingredients | 0.6.0 ingredients |
| --- | --- | --- |
| Automaton | 1 Automaton Head; 2 Automaton Arm; 1 Automaton Torso; 2 Automaton Leg | 1 Automaton Head; 2 Automaton Arm; 1 Automaton Torso; 2 Automaton Leg |
| Automaton Arm | 1 overworld logs tag; 1 Iron Ingot | 1 overworld logs tag; 1 Iron Ingot |
| Automaton Torso | 4 overworld logs tag; 2 Iron Ingot; 2 Lumina Core; 1 Redstone | 4 overworld logs tag; 2 Iron Ingot; 2 Lumina Core; 1 Redstone |
| Automaton Head | 4 overworld logs tag; 1 overworld planks tag; 2 Iron Nugget; 1 Redstone; 1 Iron Ingot | 4 overworld logs tag; 1 overworld planks tag; 2 Iron Nugget; 1 Redstone; 1 Iron Ingot |
| Automaton Leg | 1 overworld logs tag; 1 overworld planks tag; 1 Iron Ingot | 1 overworld logs tag; 1 overworld planks tag; 1 Iron Ingot |
| Tool Enchantment I | 1 Upgrade Plate; 1 Book; 2 Lapis Lazuli; 1 Amethyst Shard; 1 Lumina Core | Not craftable |
| Tool Enchantment II | 1 Tool Enchantment I; 1 Book; 2 Lapis Lazuli; 2 Amethyst Shard; 1 Diamond; 2 Lumina Core | Not craftable |
| Empty Core | 4 Glass; 1 Amethyst Shard | 4 Glass; 1 Lumite Shard |
| Hammer | 2 Cobblestone; 1 Iron Ingot; 2 Stick | 2 Cobblestone; 1 Iron Ingot; 2 Stick |
| Infusing Pedestal | 2 Iron Ingot; 2 Polished Diorite; 1 Amethyst Shard; 3 Polished Deepslate | 2 Iron Ingot; 2 Polished Diorite; 1 Diamond; 3 Polished Deepslate |
| Lumber Range I | 1 Upgrade Plate; 1 Compass; 4 overworld logs tag; 1 Lumina Core | Not craftable |
| Lumber Range II | 1 Lumber Range I; 1 Compass; 2 Iron Ingot; 2 Amethyst Shard; 2 Lumina Core | Not craftable |
| Lumina Core | World infusion: one Empty Core + 50 Magica | 4 Infused Lumite Shard; 1 Empty Core |
| Mining Range I | 1 Upgrade Plate; 1 Compass; 2 Iron Ingot; 1 Lumina Core | Not craftable |
| Mining Range II | 1 Mining Range I; 1 Compass; 1 Diamond; 2 Iron Ingot; 2 Lumina Core | Not craftable |
| Automaton Pedestal | 4 Stone Bricks; 2 Lumina Core; 2 Iron Ingot; 1 Gold Ingot | 4 Stone Bricks; 2 Lumina Core; 2 Iron Ingot; 1 Gold Ingot |
| Work Speed I | 1 Upgrade Plate; 4 Sugar; 2 Redstone; 1 Lumina Core | Not craftable |
| Work Speed II | 1 Work Speed I; 2 Sugar; 2 Redstone; 1 Diamond; 2 Lumina Core | Not craftable |
| Totem Range Upgrade I | 1 Upgrade Plate; 1 Chest; 1 Compass; 1 Lumina Core | Not craftable |
| Totem Range Upgrade II | 1 Totem Range Upgrade I; 1 Chest; 2 Iron Ingot; 2 Amethyst Shard; 2 Lumina Core | Not craftable |
| Automaton Totem | 1 Lumina Core; 2 Iron Ingot; 1 overworld logs tag; 1 Stone Bricks | 1 Lumina Core; 2 Iron Ingot; 1 overworld logs tag; 1 Stone Bricks |
| Upgrade Plate | 2 Iron Ingot; 2 Redstone; 1 Amethyst Shard | Not craftable |
| Automaton Wand | 1 Lumina Core; 2 Stick | 1 Lumina Core; 2 Stick |

The new Lumina Core uses the exact shaped cross: `.I. / IEI / .I.` (I = Infused Lumite Shard; E = Empty Core). Its four shards contain 100 Lumina already paid. The Empty Core accepts raw uncharged shards; the infuser uses a diamond. Partial/legacy-credit items cannot disappear into these uncharged ingredients.

All eleven upgrade-only outputs are removed. The ten upgrades were the plate’s only consumers. No surviving other amethyst ingredient remained after those removals, so no additional infused-shard substitutions were necessary.

Source comparison: `tools/recipe_report.py`.
