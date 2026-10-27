---
navigation:
  title: "Warehouse"
  parent: "worksites/index.md"
  position: 2
  icon: "automatons:totem"
categories:
  - "worksites"
---

# Warehouse

Configure the matching purpose in **the Totem profession chooser** before assigning your Runnerton. A Buildington constructs the surface building from the current blueprint.

<GameScene zoom="1.3" interactive={true}>
  <ImportStructure src="../assets/structures/warehouse.nbt" />
  <IsometricCamera yaw="135" pitch="30" />
</GameScene>

The illustration preserves the blueprint's block states and Totem anchor. Item-frame signs are omitted from this scene; their real item costs remain in Build List.
The finished warehouse provides eight double chests. Keep lids and the entrance usable. Runnertons use this as their home stock location.

<a name="supplies" />
## Supplies and recovery

Open **Build List** for exact total, remaining, available and missing resources, including stock in valid inventories. Door halves cost one door together; double slabs cost two slabs. Containers start empty.
The yellow perimeter shows the reserved space. Bring missing supplies or clear a reported hard obstruction, then let the worker retry.
Use **Pause/Resume** for construction control. **Repair** schedules missing building cells without silently overwriting different blocks.

[Read the Runnerton guide](../professions/runnerton.md).
