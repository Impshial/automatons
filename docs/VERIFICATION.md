# Release verification - 0.10.1

All **289 full regression tests passed** on October 27, 2026. The test server saved all dimensions and exited cleanly (BUILD SUCCESSFUL in 15m 54s). The focused worksite suite also passed **46/46 tests** with clean shutdown.

The added cases cover late delivery of materials into a Builder Shed, material stock split across multiple chests, full construction chests preserving incoming and returned cargo, assignment-only build lists, clearing the whole footprint before placement, physical clearing to 16 blocks with paid and recovered climbing blocks, preservation of existing structure and containers, usable Quarryton pickaxes and harvest tiers, recalled quarry workers reaching and staying at home, precise protected/reserved regions, and overlapping perimeters at different elevations. Existing mining, forestry, farming, crafting, ranching, logistics, combat, energy and machine regressions also pass.

Resource checks pass 232 JSON assets, 51 texture dimensions, 40 authored handbook pages, 34 live recipes, 42 item associations and seven structure previews. Five handbook installer tests pass. All ten supplied layouts remain unchanged and match their native cells and manifests.

The final model audit covers unassigned workers and all 13 professions: 1,735 exposed quads, no overlapping coplanar faces within an animated bone, no intersecting solids across articulated groups in the standing pose, retained base boots, and unchanged native atlas density. Guardington now has a one-unit neck gap, a 16-unit torso and an 11-by-11-by-10 head, retaining its 40-unit overall height. Packaged client review `build/packaged-smoke/20261027-133646` completed all 50 captures, passed the Builder GUI and current handbook checks, saved all dimensions and exited 0. Standing, angled and powered-down model views were inspected, along with the shared build list, full-chest Details dialog, green conflict outline and yellow perimeter after camera turns.

The final JAR matches all 304 authored resources. Its SHA256 is `1899AF6612516100FC3A6EDEF6C165D9A317DC73EE2E1A5DE8F54A81AB2B0C76`. Compared with the full server-tested JAR, every gameplay class and data resource is identical. Only the final client model class, its geometry/textures and the build timestamp changed. The exact final JAR was used in the completed client review; the comparison is recorded in `expansion-artifact-identity.log`.

Installed in Rackworks Tester at 13:42 on October 27, 2026, after the player saved and closed Minecraft. Exactly one Automatons JAR is installed, with the final reviewed SHA256 above. The installer updated three handbook files and found no conflicting local edits. The previous 0.10.0 JAR and changed guide files are preserved in `.automatons-release-backups/20261027-134238-765`. Player worlds were not modified.

Versioned release files are `F:/Minecraft/Automatons/releases/automatons-0.10.1.jar`, `automatons-0.10.1-source.zip` and `0.10.1-manifest.json`. Matching schematics, material bills and editable handbook are under `releases/0.10.1`; verification logs, installation receipt and all 50 client captures are under `verification/0.10.1`. The Git branch remains `codex/full-expansion`; master retains the pre-expansion checkpoint.

Multiplayer, long-duration and broad third-party compatibility testing remain manual. Use a fresh test world.

## Previous release: 0.10.0

All **277 full regression tests passed** at 10:48 on October 27, 2026. The focused expansion suite also passed all **44 tests**, including clean shutdown. The full server saved all dimensions and exited cleanly (BUILD SUCCESSFUL in 10m 50s).

Coverage includes physical ranch leading/gate closure/dispatch and real lead recovery after reload, guard spacing/recall/restoration, mage tower service in all four rotations, remote crafting with conserved fuel containers, linked-storage reassignment, fixed mining/quarrying, death recovery, resource conservation, existing professions, construction and machine behavior. Tests run in disposable worlds under build/gametest-redesign.

Resource checks pass 231 JSON assets, 51 texture dimensions, native geometry/UV validation, 40 authored handbook pages, 34 live recipes, 42 item associations and seven structure previews. Five handbook installer tests pass. All ten supplied layouts match their native cells and manifests. The JAR matches all 303 authored resource files.

Packaged client review build/packaged-smoke/20261027-103656 completed all 39 captures and compiled the current guide (41 runtime pages including its generated page). The Forge client exited 0 and saved all dimensions. The old wrapper subsequently returned 1 because Windows locked the disposable JAR during cleanup. Review evidence and the reviewed JAR remained intact; the wrapper now retains isolated review copies. No live instance or player worlds were changed by the review.

Earlier diagnostic failures are retained rather than hidden. The last broad run's two failures were fixed: an oversized rotated-layout test gallery exposed neighboring wood to the five-log forestry fixture; the gallery now stays within its own bounds and the exact five-log assertion remains. The rancher now physically collects real dispatched loot and its reusable lead beyond passive pickup range. The complete 277-test rerun passes both cases.

The client-reviewed and final server-tested JARs have identical classes and resources. Their only difference is Gradle's build timestamp in META-INF/MANIFEST.MF. Installation and archiving use the exact client-reviewed JAR; the comparison is recorded in expansion-artifact-identity.log.

Multiplayer, long-duration and broad third-party compatibility testing remain manual. Use a fresh test world.

Installed in Rackworks Tester at 10:50 on October 27. Exactly one Automatons JAR is installed, with SHA256 50FC9D48A292C3443211DF564E875E85178D8CBCA484021C46E9F877F872890A. The installer updated 33 handbook files, enabled the pack and reported no conflicting local edits. The previous 0.9.1 JAR and changed guide files are preserved in .automatons-release-backups/20261027-105036-474. Player worlds were not modified.

Versioned release files are under F:/Minecraft/Automatons/releases: automatons-0.10.0.jar, automatons-0.10.0-source.zip and 0.10.0-manifest.json. Matching schematics, material bills and the editable guide are in releases/0.10.0. Logs, artifact identity checks, installation receipt and all 39 client captures are retained in verification/0.10.0. Source and final documentation are on GitHub branch codex/full-expansion; master remains the pre-expansion checkpoint a70c39c.
