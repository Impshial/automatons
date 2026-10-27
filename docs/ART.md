# Art and model maintenance

Version 0.9.0 uses code-native Machine cuboids and existing 16×16 Minecraft textures, with one texel per model unit. Long entity faces are tiled in 16-unit quads by `MachineRenderer`; component block UVs match cuboid dimensions. `tools/author_machine.py` generates the six assembly models, attachment/card items and formed chassis/Blade/Bore data. `tools/preview_machine.py` produces the offline comparison using those same geometry files and the installed vanilla JAR. Framing is predominantly iron; copper is restricted to small rivets. The Collector retains its copper-free stone model.

The 0.9.0 Lumite revision uses an image-generated lighter palette, imported only into dark purple pixels inside the existing cavity mask. `tools/import_lumite_machine.ps1` reproduces the masked palette import. Each native image is still exactly 16×16. Fifty pixels change in each texture; all other pixels, including the chipped rim and exact vanilla background, are unchanged. `tools/validate_machine.py` verifies these invariants, model footprints, native material resolutions and the nine drill pivots. Provenance and native/enlarged previews live under `art/lumite-0.9.0` and `art/machine-0.9.0`.

The Operatorton uses the existing worker atlas and a small work cap. Runtime geometry lives in `LumbertonModel`; no new raster atlas is required.

Version 0.7.3 replaces raw and infused shards with the exact user-provided 16x16 PNGs. Authoritative sources and SHA-256 hashes are in `reference/shards-0.7.3`. `tools/import_shard_textures.py` validates and copies their bytes without decoding, resampling, recoloring or re-encoding them. The older `author_raw_shard.ps1` entry point delegates to this import, and `author_lumina.py` imports the current sources before generating data. Ore textures, models and gameplay are unchanged.

Version 0.7.2 increases the visible Lumite RGB values by 12% (clamped), preserving the darkest recess, all stone/rim pixels, and the 18-pixel glow mask. Native files and nearest-neighbor previews are in `art/lumite-0.7.2`. The raw shard has 28 interior pixels recolored to very pale purple (`#f4e9ff` and `#e7d4f8`); all eight-neighbor boundary pixels and transparency remain unchanged. `tools/author_raw_shard.ps1` reproduces that edit from the preserved original. The infused shard is unchanged. Both revisions remain flat 16x16 artwork.

Version 0.7.1 is a flat texture revision. The original full cube and existing flat emissive-pixel mechanism are restored; there are no recessed faces, rim geometry, bevels, or altered collision. The depth effect is painted into two opaque 16x16 PNGs. Each texture has 148 unchanged pixels from the user's exact vanilla background, 49 shaded rock pixels forming a chipped rim, and 59 dark/crystal interior pixels. Only the 18 selected Lumite pixels glow. No world light is emitted.

The two authoritative files are `art/lumite-0.7.1/lumite_ore_stone_texture.png` and `lumite_ore_deepslate_texture.png`. `tools/import_lumite_cavity.ps1` resamples the selected image-generated interior to the native grid, then composites it with the supplied backgrounds. Rim highlights/shadows use the original background texel at the same coordinate. All outer background pixels remain exact. Enlarged previews use nearest-neighbor scaling only. `generation.json` records the built-in generation prompts and subsequent asset assembly. The original 0.7.0 artwork remains untouched in `reference/current-run` and its versioned release archive.

`tools/author_current_run.py` is restored from 0.7.0. It validates the current texture hashes and aligns the existing flat glow mask to the artwork, without modifying PNGs. `tools/author_lumina.py` delegates to it and preserves existing Hammer/shard artwork. The current PNGs contain vanilla backgrounds; matching alternate resource packs would require matching ore PNGs.

Show texture previews to the user before launching Minecraft for future artwork reviews. The user explicitly requested texture changes, not new block geometry.

The authoritative worker pixel/geometry source is `tools/author_worker_rig.py`, invoked by `tools/author_assets.py`, and the editable geometry is `art/lumberton-model.json` plus `art/lumberton.bbmodel`. Runtime geometry and animation are in `client/LumbertonModel.java`. Regenerate the current worker with `python tools/author_assets.py --rig-only`; mining plates use `python tools/author_mining_assets.py`. The broader historical generator also writes recipes and other assets, so use the scoped command for face/rig edits.



- Final standing height: **26 model units = 1.625 blocks**.

- One model unit and one UV unit: **1/16 block and one source pixel**.

- Collision box: **0.7 × 1.625 blocks**. Eye height: **1.4 blocks**.

- Atlas: **128 × 128** per expression. The unused space provides layout room, not increased pixel density.

- Block materials and item sprites: **16 × 16**. Partial block faces use proportionate UV rectangles.

- Geometry uses integer cuboid dimensions. No whole-model shrinking, blurred image resampling, or smooth material gradients are used.



| Part | Dimensions in model units | UV origin |

|---|---:|---:|

| Head | 9 × 8 × 8 | 0, 0 |

| Body | 9 × 9 × 6 | 36, 0 |

| Right Arm | 3 × 13 × 4 | 68, 0 |

| Left Arm | 3 × 13 × 4 | 84, 0 |

| Right Leg | 3 × 8 × 4 | 100, 0 |

| Left Leg | 3 × 8 × 4 | 0, 19 |

| Neck | 4 × 1 × 4 | 16, 19 |

| Shoulder R | 1 × 3 × 4 | 34, 19 |

| Shoulder L | 1 × 3 × 4 | 46, 19 |



The head pivots at its base. The arm rig anchors the actual tool stack; the axe is not part of the geometry. Idle sway, opposing walking legs/arms, head tracking, chopping swings, and returning/unloading poses use the same rig.



The face occupies a 9 × 8 front UV rectangle starting at (8, 8). The supplied `reference/automaton-character-reference.webp` guides the larger wooden frame, inset warm face, violet eyes; the newer Runnington specification explicitly removes mouths. Expression atlases are `idle`, `smile`, `brow`, `focused`, `frown`, `anger`, and `blink`. Calm at home reuses neutral. Full matching atlases avoid overlay z-fighting. Server state selects the expression; smiles last 50 ticks and hurt reactions 35 ticks. Persistent alerts take priority over smiles. The eyes use bright atlas colors, without an extra emissive rendering pass.



The torso consists of ordinary horizontal plank rows, with a small amber marking. The supplied reference's brick motif was deliberately excluded. Seven enlarged atlas previews in `art` are inspection aids; the game loads only the original 128 × 128 files.



The totem uses adjoining cuboids to approximate an octagonal column. Wood sections stop at the iron bands to avoid coplanar faces. Its top glows. The protection curtain uses a smooth opacity fade in rendering; this is separate from the pixel-art material textures.



`F:\Minecraft\Automatons` contains organized copies of references, model sources, textures, documentation, and actual in-game captures.





## Redesign assets



The empty/full cores, head, torso, arm, leg use native 16×16 sprites. The packed Automaton now renders the same entity rig and job gear as its deployed counterpart; its 16×16 fallback/display silhouette remains editable. Infuser black/white materials are also 16×16. Its four raised prongs reach sixteen model units (one block). Four shard items float and rotate independently; paid progress controls glow and transfer pulses.



The Automaton Pedestal renders the complete Automaton item continuously, without spawning an entity or creating collectible drops. Its translucent material uses the current packed entity geometry and native 128×128 idle texture coordinates. Version 0.4.1 removes the obsolete item-atlas remapping that produced the tiled/ghost texture; the floating worker now retains its wooden body and white/purple eyes. Its center stays fixed while a configurable 30-degree lean rotates around Y; the default revolution takes eight seconds with a small bob. Infusion emits 2–4 white/purple motes every three ticks, sampled across the upper chest and shoulders at 72–85% of player height. Each mote has its own size, 12–32-tick lifetime, acceleration and cubic curve: it lifts away from the chest, then approaches the bobbing core from a random angle. Birth positions remain fixed when the donor moves. There are at most 48 live motes per infuser; distance and the nearest-16-infuser budget still apply. These visual motes have no pickup entity or effect on resource accounting. A completed core shows restrained green particles.



The work rig and expression atlases are preserved. Positional wood impacts are triggered by authoritative work progress alongside swings; movement, waiting, recall and capacity pauses do not emit chopping impacts. Timing and renderer effects are separate from resource accounting.



## 0.2.2 visual refinements



The pedestal uses a quiet, symmetrical inset-square stone texture. Its preview center is 1.4 blocks above the origin, alpha 130/255. The wand's 16×16 sprite has a slim violet handle, silver collar and bright white/violet core tip. The Lumina HUD is seven GUI pixels high, with a purple fill and white highlight below a raised XP bar; health/food reserve the same extra vertical space.



Continuous arm cycles run while the synchronized chopping target is active. Head/body face the target and the normal vanilla held-item orientation is retained, with a wrist-position correction for the 13-unit arm. Standing blocks use vanilla destroy-progress packets; fallen log models project the same destroy-stage textures.



Show Work Area draws flat-colored translucent green shared work/chest air cells above terrain. Shared interior faces are omitted: surfaces are seamless and texture-free. No world blocks or collisions are created. The area is one block high per column and refreshes as terrain or base settings change.



Lumina pickups have two physical display sizes: large orbs keep their 0.3 render scale; small orbs use 0.15 with the same white/purple material. The HUD and pedestal GUI show whole-number balances; exact thirds remain in the saved balance and bar fill. In 0.2.3 the complete trunk and branches rotate together through 90 degrees, preserving relative positions and continuously rotating their grain into the final horizontal orientation.



## 0.4.0 authoritative rig



`tools/author_worker_rig.py` drives the runtime layer, editable JSON/Blockbench hierarchy, seven 128×128 expression atlases (including blink), mouthless component/display sprites and dynamic packed-item model. Run `python tools/author_assets.py --rig-only`, then format Java and run `tools/validate_assets.py`. Full regeneration retains hand-maintained UI translations and finishes the newer rig after historical sprite generation. `tools/asset_pixels.py` supplies shared standard-library pixel authoring.



Head 9×8×8, torso 9×9×6, height 26 units, no global runtime scaling. Normal eyes are three pixels wide, with white/purple eyes and wood framing. Accessories are job-controlled children of head/body. The backpack is 7×8×3 plus a 3×3×1 parcel motif. Physical entity collision remains 0.7×1.625 blocks; packed-item presentation has its own normal inventory display scale. Both supplied Sponge schematics remain editable in reference/, while `tools/import_buildings.py` generates runtime blueprint JSON without WorldEdit.





0.4.2: Minington helmet dimensions change from 9×2×9 to 10×3×9, centered over the head; the lamp moves up half a pixel. Miner visual height becomes 27 pixels. Base body geometry, head dimensions and the 0.7×1.625 collision body stay unchanged. Generated runtime rig, editable model, Blockbench source and all expression atlases share these dimensions. The pedestal no longer has a Recipes label or clickable recipe hotspot; JEI recipe lookup remains available normally.





## 0.5.0 worksite assets



Buildington adds a carpenter apron/tool belt and the registered 16×16 Hammer. Farmington adds a straw hat and farming apron; its held item follows its hoe, planting, irrigation or bone-meal action. The shared body and existing mouthless expression atlases are retained at native pixel density. The farmer hat reaches 28.5 model units; collision remains 0.7×1.625 blocks.



The five latest schematics are imported through `tools/import_worksites.py`, including real identification frames and separate display-item costs. The supplied Farmer's Shed hip roof is preserved. Edited exports remove the mine trapdoor and move the Builder's Shed front chest pairs within four blocks of its anchor. Originals remain in `reference/worksites/source`; matching runtime JSON, schematic exports and exact material manifests are retained together.



## Lumina assets — 0.6.0



`tools/author_lumina.py` authors native 16×16 shards, transparent veins, the full-height infuser and Hammer. Ore uses shaded vanilla rock below an emissive cutout vein layer, offset 0.001 model units to avoid coplanar flicker and with hidden-face culling. No block light is emitted.



Hammer source is `art/hammer-pixels.json`, with `art/hammer-native.png` and the nearest-neighbor `art/hammer-pixel-preview.png`. A warm diagonal handle supports a compact perpendicular iron mallet head. Packaged-client held/frame and native-size captures accompany release evidence.


## Supplied fuel pieces (0.7.4)

The coal and charcoal piece item textures are direct copies of the supplied 16x16 PNGs, preserving their small silhouettes and transparent space. Sources are retained in `reference/fuel-pieces-0.7.4`. The authoring script validates their hashes and restores exact bytes without re-encoding.
