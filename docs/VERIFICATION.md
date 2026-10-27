# Expansion verification — 0.10.0

Final full regression and packaged client review are in progress. Rackworks Tester still has 0.9.1; player worlds remain unchanged.

All 44 focused expansion tests passed at 10:38 on October 27 (build/expansion-tests.log). They cover the full ranch lead/gate/dispatch cycle and real drop recovery after reload, guard spacing/recall/restoration, mage tower service in all four rotations, remote crafting and conserved fuel containers, linked-storage reassignment, mining/quarrying, conservation and recovery. Server shutdown is being checked before the next full run.

The preceding broad run passed 274/276. Its forestry failure was traced to seven delivered logs instead of the fixture's five: a rotated-layout gallery extended into neighboring test areas. The gallery now reuses one isolated footprint; the axe fixture's entire work radius stays within its own bounds. Exact delivery assertions remain. The second failure exposed ranch drops outside passive pickup range; the worker now persists a physical collection trip. Diagnostic logs and a read-only world inspection are retained under build/.

Resource checks pass 231 JSON assets, 51 texture dimensions, native geometry/UV validation, 40 handbook pages, 34 live recipes, 42 item associations and seven structure previews. Five handbook installer tests pass. All ten supplied layouts match their native cells and manifests. The JAR matches all 303 authored resource files.

Packaged review build/packaged-smoke/20261027-102125 passed with 39 captures and compiled all current guide pages. After the ranch correction, the current binary is being reviewed in build/packaged-smoke/20261027-103656. This uses a disposable copy of Rackworks Tester.

No multiplayer, long-duration or broad third-party compatibility testing is claimed. Final installation and archive receipts are pending.
