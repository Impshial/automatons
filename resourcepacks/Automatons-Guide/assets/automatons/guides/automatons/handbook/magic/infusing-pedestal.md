---
navigation:
  title: "Infusing Pedestal"
  parent: "magic/index.md"
  position: 3
  icon: "automatons:infusing_pedestal"
item_ids:
  - "automatons:infusing_pedestal"
  - "automatons:infused_lumite_shard"
---

# Infusing Pedestal

Craft the Infusing Pedestal, then right-click a raised corner with one raw Lumite Shard. Each of its four corners holds one shard.
Stay within **four blocks** with Lumina available. Each shard costs **25 Lumina**.

<GameScene zoom="2.6" interactive={true}>
  <ImportStructure src="../assets/structures/infusion.nbt" />
  <IsometricCamera yaw="135" pitch="30" />
</GameScene>

All corners share **one Lumina every four ticks**. At 20 ticks per second, one shard takes five active seconds; four take twenty seconds and cost 100 Lumina in total.
The diagram shows four occupied corners; it does not run an infusion or drain energy.

Different players can own different corners. Only each shard's donor pays. Moving away, going offline, dying or running out of Lumina pauses that donor's transfer.
Green particles and a chime mark completion.

Empty-hand right-click retrieves your selected shard. A partial shard keeps its exact paid thirds, does not stack, and needs only the remainder after reinsertion.
Completed shards require an empty main hand for retrieval; the pedestal does not automatically move them into your offhand. You can still swap hands manually.
Breaking the pedestal drops its actual contents once. Operators can retrieve another donor's corner.

<Recipe id="automatons:infusing_pedestal" />

Infusion is an interaction in the world, not a crafting-table recipe. The first pedestal uses a diamond, so it never requires its own infused output.
