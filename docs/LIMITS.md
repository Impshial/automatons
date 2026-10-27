# Current limits
This expansion targets fresh Minecraft 1.20.1 / Forge worlds and does not promise old-save migration.

- Work uses loaded chunks and bounded scans/path budgets. Unavailable destinations wait; workers create no chunk tickets.
- Totems have six ordinary production places and a separate guard roster. Mines follow the fixed supplied layout; quarries are 15×15.
- Hunting is centered on the Totem within 96 blocks. Babies, equines, cats, wolves, pets and managed livestock are excluded. Protection hooks can refuse actions.
- Crafting supports bounded ordinary crafting, smelting and blasting dependencies. Special recipes/custom mechanics can remain unsupported. It never creates free infused shards or Luminall.
- Fishing needs the authored 7×10 source-water rectangle, 3×8 dock and six reserved positions.
- Ranching supports sheep, cows, chickens and pigs. Milking needs a bucket and compatible Tank, shearing needs shears, and dispatch needs a lead and intact enclosure. Babies and two adults survive.
- Mage planting is restricted to managed non-tree beds. Operating and healing reserves are separate.
- Guards are 1.4×2.5 blocks and patrol the surface. Ordinary workers are 0.7×1.625. Narrow interiors and underground routes are not guard paths.
- Automatic hostile targeting is restricted to Zombies, Skeletons, Spiders and Cave Spiders. Creepers are excluded.
- Reassignment overflow supports linked vanilla chests, including double chests. Insufficient safe space rejects without partial item loss.
- Death recovery uses chest graves and a persistent pending journal. Bedrock and valuable containers are not overwritten.
- Multiplayer, long-duration and broad third-party compatibility are not implied by the automated tests. See [verification](VERIFICATION.md).

Runtime templates and bills are data-driven. New jobs require server behavior, inventory rules and integration checks beyond adding a name.
