# Verification — 22 October 2026

## Executed

- `gradlew.bat runGameTestServer build`: **17 required game tests passed**, followed by a successful reobfuscated JAR build.
- The game tests run in Forge's dedicated GameTest server. This exercises server-side loading without Minecraft client rendering classes.
- `python tools/validate_assets.py`: **44 JSON assets**, **21 PNG textures**, the **24-unit model**, and proportional block-face UVs passed validation.
- Walking was measured after acceleration over 40 ticks: **1.9956 blocks/second** at attribute **0.215**. Loaded movement uses the same value. Preparation, processing, and fall defaults are 80, 40, and 30 server ticks respectively; Efficiency and work-speed calculations remain separate from walking.
- A fairness check started **50 loaded totems** under the shared scanning budget; every totem made progress. This is a scheduling check, not a claim of unlimited forestry throughput.
- An actual Minecraft client was launched from the Forge development project. Screenshots revealed menu clipping at the default small window size; the menus were shortened to 234 scaled pixels and checked again. Tool rendering and totem coplanar surfaces were adjusted after inspection.
- The reobfuscated mod was launched in **Rackworks Tester**, using its installed Forge 47.4.10 libraries and existing mod set (including Rackworks, Mekanism, AE2, Thermal, JEI, and the existing decoration mods). A separate **Automatons Lab** save and offline test identity were used. Launcher credentials were not read. The harness restored the instance's original `options.txt` after exit.
- Actual instance captures and server state logs showed **Travelling → Chopping → Felling → Processing timber → Unloading → Seeking tree**. Menus and both curtain colors rendered. Final captures are stored with the assets. The client test is opt-in through `-Dautomatons.visualSmoke=true`; normal launches do not run it.

## Required game tests

1. Exactly three filtered tool slots, separate cargo, upgrade conflicts, real held-stack identity, and inventory NBT.
2. Pedestal consumption once, persistent home, and transfer of the actual worn/enchantable axe.
3. Protection at negative chunk coordinates across the dimension height, plus recall/protection serialization.
4. Natural tree acceptance, rejection of tracked player construction, and protected-tree rejection.
5. Tree discovery, felling, grounded processing, and exact five-log delivery to storage.
6. Independent tool/cargo/storage alerts; tool recovery preserves other alerts; recovery does not cancel recall.
7. Overlapping reservations and identity replacement after a totem is destroyed.
8. Partial chest insertion preserves cargo; tool withdrawal rejects a sword and preserves axe data.
9. Protection added during an active fall pauses it; revocation recovers it; timber reload cannot pay a consumed section twice.
10. Measured level-ground navigation speed.
11. A replacement home is not silently adopted; explicit owner rebind works.
12. An unloaded dependency retains its binding and is not force-loaded.
13. Worn axes break and switch through all three slots; death drops tools/cargo exactly once.
14. Full storage stops work and recovers automatically when emptied.
15. A fully searched area with no wood signals; a new tree is later discovered and harvested without Resume.
16. Fair progress across 50 competing totems.
17. Silk Touch foliage loot moves to one staged payout, survives NBT, and cannot have two authoritative owners while cargo is full.

## Evidence and remaining acceptance work

Build/test output is copied to `F:\Minecraft\Automatons\verification`. Actual client captures are in `screenshots`. The latest JAR hash is recorded in `releases/SHA256.txt`.

Desktop UI control approval timed out, so there was no claim of hand-operated UI testing. Visual inspection used screenshots produced by the running Minecraft client. Manual mouse/keyboard confirmation flows, multiplayer latency, every natural shape of the other enabled tree families, arbitrary modded chest implementations, and specific third-party claim mods remain unverified. Mangrove root shapes are deliberately unsupported. See `LIMITS.md`.
