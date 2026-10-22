# Changelog

## 0.1.1 - 22 October 2026

- Fixed creative-mode wand selection being lost between clicking a Lumberton and clicking its totem. Minecraft supplies a disposable item-stack copy to the entity callback in creative mode; the mod now saves the selection on the actual wand in the interacting hand.
- The same correction allows creative spawn-egg workers to be claimed and bound to a pedestal.
- Added game tests that exercise `Player.interactOn` followed by `ServerPlayerGameMode.useItemOn`, covering creative and survival assignment in both hands and creative worker claim/home/assignment. The creative tests reproduced the lost selection before the fix.
- Existing workers, wands, inventories, and worlds remain compatible. Restart Minecraft after installing the update, then select the worker again.

## 0.1.0 - 22 October 2026

First playable Lumberton with custom model and animation, pedestal creation, totem forestry, persistent inventories and bindings, tree protection, and upgrades.
