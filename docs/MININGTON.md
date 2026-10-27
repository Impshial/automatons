# Minington and Quarryton
Assign an ordinary worker to a Mine or Quarry Totem to select its profession. Supply actual pickaxes, suitable shovels, ladders, chests, torches and safe filler. Production begins after Buildington completes the supplied surface structure.

## Fixed strip mine
The first landing is twenty blocks below the entrance; subsequent levels are four blocks lower. Each landing is 5×5×3 with eight chest blocks forming four double chests in two tiers along the back. Earlier depots remain usable.

Each level has a three-wide, 25-block-long, two-high central corridor and twenty side passages in ten pairs. The first two pairs are 24 blocks long; the remaining eight pairs are 25. Positions and rotation follow the supplied strip-level metadata.

Workers share saved progress and exclusive cell claims, construct access from actual materials, wear tools, place lights and carry drops to storage. Missing supplies, blocked access and full storage preserve unfinished work.

Side cave/fluid hazards end the affected branch. Central-access hazards pause the mine with a reason. Settings expose the applicable resume control. Mine Totems do not offer variable tunnel-width or the previous quarry mode.

## Quarry
A usable pickaxe is required before excavation starts, and a broken pickaxe must be replaced. Shovels are optional for soil and cannot satisfy this starting requirement. The tool alert names the needed tool tier and, once selected, the block and coordinates.

The separate Quarry Totem excavates a fixed 15×15 footprint in layers. Ladder shaft and backing remain outside. Settings choose stopping depth, bounded by the dimension's minimum height, and expose cave acknowledgement.

Fluid plugs consume safe filler and lighting consumes torches. Unbreakable cells remain and are reported. Cave alerts persist until acknowledged; a newly discovered opening causes a new alert. Workers use the ladder to unload and retreat. Guards stay on the surface.

## Recovery
Recall, depleted energy, threats and service interruptions preserve progress. Repair access and replenish supplies before resuming. Miningtons can provide same-network forestry assistance when their site is not recalled.

Runtime geometry and bills are in `src/main/resources/data/automatons/buildings`; source manifests and .schem files are under `reference/schematics-2026-10-27/Automatons-Schematics`.
