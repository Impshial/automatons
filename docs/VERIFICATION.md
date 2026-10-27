# Expansion verification — 0.10.0

All **277 full regression tests passed** at 10:48 on October 27, 2026. The focused expansion suite also passed all **44 tests**, including clean shutdown. The full server saved all dimensions and exited cleanly (BUILD SUCCESSFUL in 10m 50s).

Coverage includes physical ranch leading/gate closure/dispatch and real lead recovery after reload, guard spacing/recall/restoration, mage tower service in all four rotations, remote crafting with conserved fuel containers, linked-storage reassignment, fixed mining/quarrying, death recovery, resource conservation, existing professions, construction and machine behavior. Tests run in disposable worlds under build/gametest-redesign.

Resource checks pass 231 JSON assets, 51 texture dimensions, native geometry/UV validation, 40 authored handbook pages, 34 live recipes, 42 item associations and seven structure previews. Five handbook installer tests pass. All ten supplied layouts match their native cells and manifests. The JAR matches all 303 authored resource files.

Packaged client review build/packaged-smoke/20261027-103656 completed all 39 captures and compiled the current guide (41 runtime pages including its generated page). The Forge client exited 0 and saved all dimensions. The old wrapper subsequently returned 1 because Windows locked the disposable JAR during cleanup. Review evidence and the reviewed JAR remained intact; the wrapper now retains isolated review copies. No live instance or player worlds were changed by the review.

Earlier diagnostic failures are retained rather than hidden. The last broad run's two failures were fixed: an oversized rotated-layout test gallery exposed neighboring wood to the five-log forestry fixture; the gallery now stays within its own bounds and the exact five-log assertion remains. The rancher now physically collects real dispatched loot and its reusable lead beyond passive pickup range. The complete 277-test rerun passes both cases.

The client-reviewed and final server-tested JARs have identical classes and resources. Their only difference is Gradle's build timestamp in META-INF/MANIFEST.MF. Installation and archiving use the exact client-reviewed JAR; the comparison is recorded in expansion-artifact-identity.log.

Multiplayer, long-duration and broad third-party compatibility testing remain manual. Use a fresh test world.

Installed in Rackworks Tester at 10:50 on October 27. Exactly one Automatons JAR is installed, with SHA256 50FC9D48A292C3443211DF564E875E85178D8CBCA484021C46E9F877F872890A. The installer updated 33 handbook files, enabled the pack and reported no conflicting local edits. The previous 0.9.1 JAR and changed guide files are preserved in .automatons-release-backups/20261027-105036-474. Player worlds were not modified.

Versioned release files are under F:/Minecraft/Automatons/releases: automatons-0.10.0.jar, automatons-0.10.0-source.zip and 0.10.0-manifest.json. Matching schematics, material bills and the editable guide are in releases/0.10.0. Logs, artifact identity checks, installation receipt and all 39 client captures are retained in verification/0.10.0. Source and final documentation are on GitHub branch codex/full-expansion; master remains the pre-expansion checkpoint a70c39c.
