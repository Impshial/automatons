# Automatons — first playable Lumberton

Minecraft **1.20.1**, Forge **47.4.10**, Java **17**. No additional runtime dependency.

The development source is in `F:\Development\Codex\Automatons`. Editable art, reference images, documentation, and test captures are mirrored to `F:\Minecraft\Automatons`. The packaged mod is installed in the user-designated **Rackworks Tester** instance. Existing worlds are separate from the new **Automatons Lab** smoke-test world.

## Play

1. Craft an **Automaton Pedestal**, **Automaton Totem**, and **Automaton Wand**.
2. Place the pedestal. Open it with an empty hand or an ordinary item. Insert **6 oak logs, 4 iron ingots, 1 amethyst shard, and 1 iron axe**, then click **Create Lumberton**. The actual axe, including wear and enchantments, becomes tool slot 1. Clear space beside the pedestal if creation cannot find safe ground.
3. Place a totem near trees and put an accessible chest within **4 blocks outward** horizontally and **4 blocks vertically**. The storage footprint is **9 × 9**. Discovery takes a short time; open the totem to inspect registered chests. **Out +** permits deposits, **Tool +** permits replacement axes. Click either to disable it. Blocking a chest lid or its approach can stop service.
4. In **Assign** mode, right-click your Lumberton and then your totem. One worker can occupy each totem. Its original pedestal remains its home. The lumber footprint is **33 × 33**, centered on the totem; trees inside the storage footprint remain eligible.
5. Supply axes through the worker's three filtered tool slots or a chest with tool supply enabled. A worker approaches a tree, chops its base, visibly tips the timber, walks along the grounded sections, processes them, and unloads its separate nine-slot cargo.
6. Install one upgrade per family in the totem's four slots. Upgrades affect only its assigned worker. Their recipes and provisional values are in [BALANCE.md](docs/BALANCE.md).

## Wand and protection

**Shift + mouse wheel** cycles **Assign / Protect / Revoke** and consumes the scroll. Sneak-right-click in Assign mode cancels a pending selection. Selections expire after one minute.

Protect/Revoke selects the clicked chunk and opens a **Yes / No** confirmation. No, Escape, or closing the screen makes no change. Protection covers the whole dimension height, including negative Y. Only the creator or a server operator can revoke it. It blocks this mod's destructive jobs; walking, chest use, and unloading remain permitted.

Holding a Protect wand shows a green filled curtain; Revoke shows red. The visible band starts eight blocks below the viewer and fades out over 64 blocks. Adjacent protected chunks have no interior shared wall. An unprotected hole retains its boundary. The main-hand wand takes precedence over an off-hand wand.

## Help and recovery

An amber beam signals any combination of **No usable tools**, **No eligible wood**, **Storage full**, and **Cargo full**. The totem and worker screens explain the reasons. Restoring tools, growing eligible trees, freeing storage, or removing cargo clears each reason independently and permits automatic recovery. The worker unloads full cargo when accessible storage has room.

**Call home** on a pedestal recalls only its own workers and keeps their assignments and inventories. **Resume** releases this persistent recall. Fixing an alert does not release recall. Workers can wait for unloaded dependencies without deleting their links or force-loading chunks.

A destroyed totem clears its assignment. Removing all output chests sends the worker home while keeping its assignment. Destroying a home preserves the worker; its owner must hold the wand, open the worker, click **Rebind missing home**, then right-click a replacement pedestal. Replacing a block at the same coordinates does not recreate the original identity.

Players can also right-click reachable fallen timber with an axe. Each timber section has one persisted consumption record shared by player and worker harvesting.

## Build and check

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.12.7-hotspot'
.\gradlew.bat build
.\gradlew.bat runGameTestServer
python tools/validate_assets.py
```

Output: `build/libs/automatons-0.1.1.jar`. Install the same JAR on a multiplayer server and its clients. The creative test egg has no survival recipe; survival creation uses the pedestal.

- [Balance and recipes](docs/BALANCE.md)
- [Model and texture sources](docs/ART.md)
- [Validation evidence](docs/VERIFICATION.md)
- [Release changes](docs/CHANGELOG.md)
- [Concrete limits and extension points](docs/LIMITS.md)

The supplied specification is retained in `reference/original-specification.txt`. The direct request overrides the reference image: the torso uses ordinary horizontal planks, with no brick pattern.
