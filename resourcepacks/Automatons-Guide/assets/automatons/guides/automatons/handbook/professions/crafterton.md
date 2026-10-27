---
navigation:
  title: "Crafterton"
  parent: "professions/index.md"
  position: 8
  icon: "minecraft:crafting_table"
---

# Crafterton

Uses actual RecipeManager recipes, crafting-table access and real furnace or blast-furnace processing. Place requested ingredients and fuel in network storage. Container remainders and output capacity are checked before committing a craft.

Network tools have priority, followed by torches, construction, other requested items and the decorative iron armor set. The workshop stand starts empty; missing armor pieces are crafted last. With couriers available, the crafter stays at its workshop. Without them, it fetches stock and delivers output physically. Recipe searches are bounded; unsupported or cyclic recipes show a wait reason instead of inventing materials. Luminall and infused shards require Luminariton service.
