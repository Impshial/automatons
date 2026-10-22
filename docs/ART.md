# Art and model maintenance

The authoritative editable pixel source is `tools/author_assets.py`, and the editable geometry is `art/lumberton-model.json` plus `art/lumberton.bbmodel`. Runtime geometry and animation are in `client/LumbertonModel.java`. Regenerate the resource artwork with `python tools/author_assets.py`.

- Final standing height: **24 model units = 1.5 blocks**.
- One model unit and one UV unit: **1/16 block and one source pixel**.
- Collision box: **0.7 × 1.5 blocks**. Eye height: **1.29 blocks**.
- Atlas: **64 × 64** per expression. The unused space provides layout room, not increased pixel density.
- Block materials and item sprites: **16 × 16**. Partial block faces use proportionate UV rectangles.
- Geometry uses integer cuboid dimensions. No whole-model shrinking, blurred image resampling, or smooth material gradients are used.

| Part | Dimensions in model units | UV origin |
|---|---:|---:|
| Head | 6 × 6 × 6 | 0, 0 |
| Torso | 8 × 9 × 6 | 0, 14 |
| Each arm | 3 × 13 × 4 | 32, 0 / 48, 0 |
| Each leg | 3 × 8 × 4 | 32, 20 / 48, 20 |
| Neck | 4 × 1 × 4 | 0, 34 |
| Shoulder joints | 1 × 3 × 4 | 0, 42 / 12, 42 |

The head pivots at its base. The arm rig anchors the actual tool stack; the axe is not part of the geometry. Idle sway, opposing walking legs/arms, head tracking, chopping swings, and returning/unloading poses use the same rig.

The face occupies a 6 × 6 front UV rectangle starting at (6, 6). Expression atlases are `idle`, `smile`, `brow`, `focused`, `frown`, and `anger`. Calm at home reuses neutral. Full matching atlases avoid overlay z-fighting. Server state selects the expression; smiles last 50 ticks and hurt reactions 35 ticks. Persistent alerts take priority over smiles.

The torso consists of ordinary horizontal plank rows, with a small amber marking. The supplied reference's brick motif was deliberately excluded. Six enlarged atlas previews in `art` are inspection aids; the game loads only the original 64 × 64 files.

The totem uses adjoining cuboids to approximate an octagonal column. Wood sections stop at the iron bands to avoid coplanar faces. Its top glows. The protection curtain uses a smooth opacity fade in rendering; this is separate from the pixel-art material textures.

`F:\Minecraft\Automatons` contains organized copies of references, model sources, textures, documentation, and actual in-game captures.
