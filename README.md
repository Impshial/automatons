# Automatons — Minecraft 1.20.1 / Forge
Version **0.10.0** expands the existing six professions to thirteen. It adds Hunterton, Crafterton, Fisherton, Rancherton, Quarryton, Luminariton and the armored Guardington, using the supplied building schematics. Runnerton is the courier's current name.

This is a fresh-world prerelease. Use Java 17, Forge 47.4.10 and GuideME 20.1.15, with matching mod versions on client and server. JEI 15.56.0.205 and JER 1.4.0.247 are optional client integrations.

## Getting started
1. Craft the Handbook from a book and any plank. Mine Lumite, collect Lumina from eligible vegetation, and infuse shards at the Infusing Pedestal. Each shard costs 25 Lumina; four infused shards around an Empty Core make a Lumina Core.
2. Craft a home Automaton Pedestal, Wand, Totem and six ordinary body components. Assemble one head, one torso, two arms and two legs at the home Pedestal.
3. Deploy the worker. Configure a Totem as **Builder**, then assign the worker with the Wand. Assignment automatically makes it a Buildington. Equip its Hammer.
4. Supply reachable chests outside the planned footprint. **Build List** shows actual material requirements. Builders can start with partial stock.
5. Configure additional Totems and assign ordinary workers. Each site accepts six production workers and automatically selects their profession. Supply appropriate tools and consumables. Direct Machine assignment selects Operatorton.
6. Keep Luminall bottles available. Workers start with 200 operating points and spend one per 1,200 loaded ticks. At two points they seek a refill; at zero they stop. One full 250 mB bottle restores 125 points and returns glass.

Guardingtons require six matching armored components. They have a permanent profession and a separate assignment roster.

## Professions
| Profession | Work |
| --- | --- |
| Lumberton | Shared forestry, fallen timber and replanting |
| Minington | Fixed strip-mine levels and underground depots |
| Farmington | Shared fields with three crop selectors |
| Runnerton | Physical network deliveries and output collection |
| Buildington | Supplied blueprints, repairs and construction |
| Operatorton | Physically boards and operates a Machine |
| Hunterton | Hunts eligible adult wild animals |
| Crafterton | Crafting dependencies and actual furnace processing |
| Fisherton | Reserved dock positions and ordinary fishing loot |
| Rancherton | Protected livestock, breeding, shearing, eggs and milk |
| Quarryton | 15×15 excavation with external ladder access |
| Luminariton | Non-tree planting, bottles, infusion and healing |
| Guardington | Surface patrols with an armored body |

## Guides and development
See [network setup](docs/THE_NETWORK.md), [mining and quarrying](docs/MININGTON.md), [couriers](docs/RUNNERTON.md), [Luminall](docs/LUMINALL.md), [Machine controls](docs/MACHINE.md), [limits](docs/LIMITS.md), [balance](docs/BALANCE.md) and [verification](docs/VERIFICATION.md).

The editable handbook is in `resourcepacks/Automatons-Guide`. Enable it in Resource Packs; edit its Markdown and press F3+T to reload. The installer preserves local edits. See [authoring](docs/HANDBOOK-AUTHORING.md).

`tools/test_release.ps1` builds and runs the full Forge GameTest suite; `-Expansion` selects expansion tests. Tests use disposable worlds under `build/gametest-redesign`. Also run `validate_assets.py`, `validate_handbook.py` and `verify_machine_package.py` from `tools`.

The build output is `build/libs/automatons-0.10.0.jar`. Close Minecraft before installation. `tools/install_test_instance.ps1` targets Rackworks Tester, backs up the previous JAR, installs the handbook and verifies the copied hash. Versioned distributions use `F:/Minecraft/Automatons/releases`. Consult verification for the actual release status.

The original schematic bundle is preserved under `reference/schematics-2026-10-27/Automatons-Schematics`. `tools/import_expansion.py` verifies and imports its ten native layouts, matching exports, metadata and material bills.
