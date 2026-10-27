---
navigation:
  title: "Mine entrance"
  parent: "worksites/index.md"
  position: 1
  icon: "automatons:totem"
categories:
  - "worksites"
---

# Mine entrance

Configure the matching purpose in **Totem → Worksite Settings** before assigning your Minington. A Buildington constructs the surface building from the current blueprint.

<GameScene zoom="1.3" interactive={true}>
  <ImportStructure src="../assets/structures/minehead.nbt" />
  <IsometricCamera yaw="135" pitch="30" />
</GameScene>

The illustration preserves the blueprint's block states and Totem anchor. Item-frame signs are omitted from this scene; their real item costs remain in Build List.
The foundation is buried one block, keeping the entrance flush with the ground. There is no hatch. The underground shaft, platform, ladders and two double-chest depots are added by Miningtons after the surface handover.

<a name="supplies" />
## Supplies and recovery

Open **Build List** for exact total, remaining, available and missing resources, including stock in valid inventories. Door halves cost one door together; double slabs cost two slabs. Containers start empty.
The yellow perimeter shows the reserved space. Bring missing supplies or clear a reported hard obstruction, then let the worker retry.
Use **Pause/Resume** for construction control. **Repair** schedules missing building cells without silently overwriting different blocks.

[Read the Minington guide](../professions/minington.md).
