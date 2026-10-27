---
navigation:
  title: "Jobs, names and cargo"
  parent: "network/index.md"
  position: 3
  icon: "automatons:automaton"
item_ids:
  - "automatons:automaton"
  - "automatons:gravestone"
---

# Workers, equipment and recovery

Right-click an owned worker for **Status** and **Inventory**. Status shows its name, read-only profession, assignment, health, operating charge and current task. Assign it to a configured Totem to change profession. Operatortons use Machine assignment; armored Guardingtons retain their body and profession.

<ItemImage id="automatons:automaton" scale="3" />

Ordinary workers have three profession-filtered equipment slots and nine cargo slots. Runnertons have eighteen cargo slots. Incompatible tools move to cargo or available linked chest storage during reassignment, with their names, enchantments and durability intact. If everything cannot fit, reassignment waits without partially changing the inventory.

Workers have 200 operating Lumina, initially full. They consume one point per 1,200 loaded ticks. At two points or less they stop working and return for a full bottle. Drinking takes 32 ticks and returns an empty bottle; overflow beyond capacity is spent. At zero, movement and work stop and the eyes turn black. Give the worker a full bottle to restart it. Charge does not restore health.

Packing preserves identity, name, home, profession and charge, while dropping carried equipment/cargo once. On death, belongings and recoverable body parts go into an ordinary chest with a gravestone. The torso always survives; three to six total parts are recovered. A blocked grave location keeps its contents pending rather than destroying existing valuable containers.

Nearby dropped-item pickup uses available cargo space and respects item ownership. Flower collection stays within two blocks of the current route, skips protected collector beds and never diverts a worker during combat, escape or climbing.

[Wand controls](wand.md) · [Guardington](../professions/guardington.md) · [Healing](../professions/luminariton.md)
