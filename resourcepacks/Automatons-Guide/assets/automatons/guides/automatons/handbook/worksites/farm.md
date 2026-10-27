---
navigation:
  title: "Farmer's Shed"
  parent: "worksites/index.md"
  position: 5
  icon: "automatons:totem"
categories:
  - "worksites"
---

# Farmer's Shed

Configure the matching purpose in **the Totem profession chooser** before assigning your Farmington. A Buildington constructs the surface building from the current blueprint.

<GameScene zoom="1.3" interactive={true}>
  <ImportStructure src="../assets/structures/farm.nbt" />
  <IsometricCamera yaw="135" pitch="30" />
</GameScene>

The illustration preserves the blueprint's block states and Totem anchor. Item-frame signs are omitted from this scene; their real item costs remain in Build List.
The shed is separate from the field. Existing farmland can be adopted; otherwise farmers plan one shared 9 × 9 plot. Supply a hoe, planting stock, a water bucket and a glass block; set the three crop selectors.

<a name="supplies" />
## Supplies and recovery

Open **Build List** for exact total, remaining, available and missing resources, including stock in valid inventories. Door halves cost one door together; double slabs cost two slabs. Containers start empty.
The yellow perimeter shows the reserved space. Bring missing supplies or clear a reported hard obstruction, then let the worker retry.
Use **Pause/Resume** for construction control. **Repair** schedules missing building cells without silently overwriting different blocks.

[Read the Farmington guide](../professions/farmington.md).
