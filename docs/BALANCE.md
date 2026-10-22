# Provisional design and balance

These are testing defaults, not permanently approved balance decisions.

| Setting | Default | Location |
|---|---|---|
| Workers per totem | 1 | Assignment validation |
| Storage range | 4 outward, 9 × 9 | `Balance.STORAGE` |
| Storage height | ±4 | `Balance.HEIGHT` |
| Lumber range | 16 outward, 33 × 33 | `Balance.LUMBER` |
| Trunk-base search height | ±16 around totem | Station scanner |
| Cargo | 9 slots | Separate from tools |
| Tools | Exactly 3, one axe per slot | Job-filtered inventory |
| Upgrade slots | 4, one item per family | Totem inventory |
| Walking attribute | 0.215 | `Balance.WALK` |
| Measured level-ground speed | 1.996 blocks/second | 40-tick navigation sample after acceleration |
| Loaded movement | Same walking value | No weight penalty |
| Iron-axe preparation | 80 ticks per rooted base log | `Balance.PREP` |
| Iron-axe processing | 40 ticks per section | `Balance.PROCESS` |
| Tipping animation | 30 ticks | `Balance.FALL` |
| Protection curtain | 64 high, bottom at viewer Y − 8 | Display only; actual protection is full height |
| Global scan budget | 512 work units per tick | Fair queue across dimensions |
| Per-totem tree scan | Up to 32 cells per visit | Storage scans use another bounded 16 cells |

Forge writes editable server values to `<world>/serverconfig/automatons-server.toml`. A server can provide defaults through `defaultconfigs`. Existing config files retain their saved values. Movement and work durations are separate settings.

Chopping duration is `max(10, round(baseTicks × 6 / effectiveToolSpeed × 0.8^workSpeedTier))`. Effective tool speed is the tool tier's speed plus `efficiency² + 1` when effective Efficiency is positive. Effective Efficiency is the **higher** of the axe's existing enchantment and the totem's granted level. The totem never writes an enchantment to the axe. Normal tool wear, including Unbreaking, uses the real stack.

Felling charges one wear per rooted base log when the standing wood is committed. Processing charges one wear per timber section when it yields drops. Rendered swings do not cause additional wear or yield. Crown loot is generated once with the felling tool and held by the timber job. Shared or decorative foliage is retained.

## Upgrades

| Family | Tier I | Tier II |
|---|---|---|
| Storage Range | +4 outward | +8 outward |
| Lumber Range | +8 outward | +16 outward |
| Work Speed | 20% shorter chopping | 36% shorter chopping |
| Tool Enchantment | Effective Efficiency I | Effective Efficiency II |

Tiers replace each other in the same family; they do not stack. Inventory insertion rejects a second item from the same family. Removing a range upgrade revalidates pending work and can leave timber waiting for a restored range or player recovery.

## Recipes

All crafting recipes live in `src/main/resources/data/automatons/recipes`. The pedestal uses the custom datapack recipe type `automatons:creation`; it accepts four ordered ingredients with configurable wood and metal counts.

Pedestal:

```text
stone brick    amethyst    stone brick
iron ingot     gold ingot  iron ingot
stone brick    stone brick stone brick
```

Totem:

```text
empty       amethyst    empty
iron ingot  oak log     iron ingot
empty       stone brick empty
```

Wand:

```text
empty  empty amethyst
empty  stick empty
stick  empty empty
```

Lumberton, in the pedestal: **6 oak-log-tag items, 4 iron ingots, 1 amethyst shard, 1 iron axe**. The exact recipe axe transfers to the entity. Spawn clearance is checked before any ingredient is consumed.

Each Tier I upgrade uses ` I /ACA/ I `: two iron ingots, two amethyst shards, and the following center ingredient:

- Storage: chest.
- Lumber: oak sapling.
- Speed: redstone dust.
- Tool Enchantment: lapis lazuli.

Tier II uses ` G /AUA/ G `: two gold ingots, two amethyst shards, and the corresponding Tier I upgrade. These are ordinary survival crafting recipes.
