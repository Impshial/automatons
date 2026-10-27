# Full expansion implementation

Authoritative request: `reference/schematics-2026-10-27/Automatons-Schematics/Automatons-Full-Implementation-Prompt.txt`, with supplied geometry and integration notes. Prior transcript is historical context. Existing 0.9.1 source/assets/tools/docs are preserved in `build/expansion-baseline-0.9.1.zip` before edits. No AGENTS.md was found in the repository or its ancestors.

This is an active implementation record, not a completion report.

Build workflow: reuse `tools/test_release.ps1 -Expansion` and the repository Gradle wrapper. The exact test command with output in `build/expansion-tests.log` has a saved approval. Do not change shell launchers or invent new build wrappers. The managed sandbox requires escalated access to the existing user Gradle cache; use the saved command, without another conversational permission request. Installed `Rackworks Tester/mods/automatons-0.9.1.jar` was verified unchanged, last modified October 25 at 23:22.

User clarifications: reuse the existing six professions and add only the seven missing jobs. Dead Plant and Dead Crop disappear on breaking with no drops, superseding the handoff's dead-item drop rule. GitHub pushes are authorized; use standalone Git commands to match saved approvals. The exact pre-expansion 0.9.1 baseline was committed and pushed to `https://github.com/Impshial/automatons` master as `a70c39c1e4b1ae6cf3eeb20c65e47cdbc86ffb1d`; expansion work is on `codex/full-expansion`.

The user chose to continue in this task after verifying the changes, rather than reverting or returning to the old task. The original task, **Draft Minington automaton prompt**, ID `01a1660b-d50d-7b93-aaf2-725339a2a66f`, was read through the task tools. It used the SAME bundled PowerShell runtime and repository scripts. The earlier explanation attributing the approvals to a different runtime was incorrect; current sandbox restrictions are observed, but the old task's effective permission policy was not established. Do not repeat that explanation. No app security settings were changed.

- [ ] Shared professions, six-worker assignment, body compatibility and equipment preservation.
- [ ] Operating charge, service reserve, exact fluid conversion, depletion and dead vegetation.
- [ ] Threat behavior, safe retreat, grave recovery and armored assembly.
- [ ] Import all ten supplied layouts, metadata, rotations and exact material accounting.
- [ ] Fixed strip mining, independent quarrying and three farming strips.
- [ ] Hunterton, Crafterton, Fisherton, Rancherton, Luminariton and Guardington jobs.
- [ ] Shared opportunistic flowers and complete network supplies.
- [ ] Finished contextual totem and worker interfaces.
- [ ] Thirteen profession models, textures, powered-down and fishing presentation.
- [ ] Regression suite, focused behavior tests, asset/package validation, client review.
- [ ] Current documentation, packaged JAR and release evidence.

## Evidence

The ZIP was extracted and CODEX-START-HERE.txt read before other package documents. All ten manifests were inspected. Four supplied custom block IDs match current registrations: totem, tank, lumina_collector, infusing_pedestal. The baseline Tank already accepts arbitrary registered fluids, and the existing collector/source recognition and construction transactions will be extended.

## Foundation checkpoint scope

Implemented in this working branch: profession IDs and server assignment checks, distinct guard entity/body recipes, operating/service energy storage, fixed fluid ratio, quarter-rate collector production with persisted depletion, non-dropping dead vegetation, supplied layout import/rotation adapters, field strips and server-side ghost selections, water/glass supply actions, deferred ordinary-chest grave recovery, and initial exact-type hostile targeting/retreat/shared combat code.

These are foundations, not a finished expansion. In particular: the new profession task loops, fixed mining/quarry replacement, guard patrol/protection integration, Luminariton service/healing, full logistics supply routing, GUI replacement, all thirteen appearances, release documentation and complete regression/client validation remain unfinished. The new fields have server selections but the replacement UI has not yet been connected. Shared combat is present but guard/hunter task dispatch remains pending. Do not install or describe this branch as a release.

Checks so far: compileJava passed; `validate_assets.py` passed 231 JSON assets and 50 textures; `validate_handbook.py` passed its existing 33-page checks; import validated every explicit block against all ten supplied manifests. Nine focused server GameTests passed, covering dead vegetation loot/clearing, persistent exhaustion/tree exemption, three-strip field persistence, whole-bottle energy and fractional drain interval, exact death inventory recovery and duplicate-callback prevention, blocked/lava grave recovery, tall source exhaustion, and all four collector-bed rotations. Full pre-existing regression suite and client review have NOT passed for the expansion yet.

Reuse the saved test command verbatim (normal installed Gradle cache access is required):

```powershell
.\tools\test_release.ps1 -Expansion *> build/expansion-tests.log; Get-Content build/expansion-tests.log -Tail 50; exit $LASTEXITCODE
```
