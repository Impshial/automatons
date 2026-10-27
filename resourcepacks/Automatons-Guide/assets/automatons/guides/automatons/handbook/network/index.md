---
navigation:
  title: "The Network"
  parent: "index.md"
  position: 3
  icon: "automatons:pedestal"
---

# The Network

The **Pedestal** owns worker identities, names, membership and recall. Each **Totem** manages one local worksite, its building, stock and shared work board. Workers perform the physical actions.

<GameScene zoom="1.6" interactive={true}>
  <ImportStructure src="../assets/structures/network.nbt" />
  <IsometricCamera yaw="135" pitch="30" />
</GameScene>

This small diagram shows a home, a Totem and local storage; it is not a building blueprint or the full work range.
Stock reports and reservations coordinate work, but items move only through real worker inventories. No part of the Network force-loads chunks.

<SubPages icons={true} />
