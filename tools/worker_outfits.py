"""Native cuboid clothing and material palettes for the shared worker atlas."""
from asset_pixels import rgb


PALETTES = {
    'straw': ['997534', 'c7a34e', 'e4c87b'],
    'green': ['284635', '416547', '658158'],
    'helmet': ['805b22', 'ba8b33', 'dfb75b'],
    'lamp': ['9d7538', 'f4da92', 'fff1c6'],
    'leather': ['503325', '765038', '986b47'],
    'strap': ['35271f', '523a2a', '78513a'],
    'parcel': ['9b7441', 'c49960', 'e2c088'],
    'denim': ['304553', '476575', '678894'],
    'patch': ['79704a', 'a19867', 'c0b583'],
    'boot': ['292724', '40372d', '61503b'],
    'glove': ['59412c', '876340', 'ad8656'],
    'goggle': ['25464d', '497b82', '97c4bd'],
    'purple': ['38244d', '61427e', '8b60a6'],
    'trim': ['88612d', 'bd9854', 'e2c484'],
    'armor': ['454d56', '747e88', 'a1adb5'],
    'steel_dark': ['323840', '49535b', '667680'],
    'red': ['411c20', '923337', 'bb4c45'],
    'camo': ['3b4030', '65704a', '8a8054'],
}


def clothing(part):
    def p(name, pos, size, material, parent, job):
        part(name, [0, 0, 0], pos, size, material, parent, job)

    def shirt(prefix, color, job):
        p(prefix+'_shirt', [-5, 0, -3.5], [10, 9, 7], color, 'body', job)
        p(prefix+'_sleeve_r', [-3.5, 0, -2.5], [4, 6, 5], color, 'right_arm', job)
        p(prefix+'_sleeve_l', [-.5, 0, -2.5], [4, 6, 5], color, 'left_arm', job)

    def gloves(prefix, job):
        p(prefix+'_glove_r', [-3.5, 9, -2.5], [4, 4, 5], 'glove', 'right_arm', job)
        p(prefix+'_glove_l', [-.5, 9, -2.5], [4, 4, 5], 'glove', 'left_arm', job)

    def goggles(prefix, job, high=False):
        # Keep the lenses above the expressive eyes, including the black powered-down face.
        y = -9 if high else -8
        p(prefix+'_band', [-5, y, -4.5], [10, 1, 9], 'strap', 'head', job)
        for suffix, x in [('r', -4), ('l', 1)]:
            p(prefix+'_rim_'+suffix, [x-.5, y-1, -5], [4, 3, 1], 'iron', 'head', job)
            p(prefix+'_lens_'+suffix, [x, y-.5, -5.5], [3, 2, 1], 'goggle', 'head', job)

    # Boots follow each leg, including all ordinary professions and unassigned workers.
    p('boot_r', [-2.5, 4, -2.5], [4, 4, 5], 'boot', 'right_leg', -1)
    p('boot_l', [-1.5, 4, -2.5], [4, 4, 5], 'boot', 'left_leg', -1)
    shirt('lumber', 'red', 1)
    p('lumber_belt', [-5, 7, -4], [10, 2, 1], 'strap', 'body', 1)
    p('axe_belt', [2, 6, -4.5], [2, 2, 1], 'iron', 'body', 1)

    for prefix, job in [('miner', 2), ('quarry', 11)]:
        p(prefix+'_apron', [-4, 1, -4], [8, 8, 1], 'leather', 'body', job)
        p(prefix+'_knee_r', [-2.5, 2, -3], [4, 3, 1], 'iron', 'right_leg', job)
        p(prefix+'_knee_l', [-1.5, 2, -3], [4, 3, 1], 'iron', 'left_leg', job)
    p('miner_helmet', [-5, -9, -4.5], [10, 3, 9], 'helmet', 'head', 2)
    p('miner_brim', [-5.5, -7, -5.5], [11, 1, 11], 'helmet', 'head', 2)
    p('miner_lamp', [-1.5, -8.5, -5.5], [3, 2, 1], 'lamp', 'head', 2)
    goggles('quarry', 11)

    p('backpack', [-4, 0, 3], [8, 10, 4], 'leather', 'body', 3)
    for suffix, x in [('right', -3.5), ('left', 2.5)]:
        p('strap_'+suffix, [x, 0, -4], [1, 8, 1], 'strap', 'body', 3)
        p('strap_top_'+suffix, [x, -.5, -3], [1, 1, 6], 'strap', 'body', 3)
    p('strap_cross', [-3.5, 4, -4.5], [7, 1, 1], 'strap', 'body', 3)
    p('parcel', [-2, 5, 7], [4, 3, 1], 'parcel', 'body', 3)
    p('runner_cap', [-5, -9, -4.5], [10, 3, 9], 'denim', 'head', 3)
    p('runner_peak', [-4, -7, -7.5], [8, 1, 4], 'denim', 'head', 3)

    p('builder_apron', [-3.5, 1, -4], [7, 7, 1], 'leather', 'body', 4)
    p('builder_belt', [-4.5, 6, -4.5], [9, 2, 1], 'strap', 'body', 4)
    p('builder_buckle', [-1, 6, -5], [2, 2, 1], 'iron', 'body', 4)
    p('builder_pouch', [2, 6, -5], [3, 3, 2], 'leather', 'body', 4)
    p('farmer_brim', [-6, -8.5, -5.5], [12, 1, 11], 'straw', 'head', 5)
    p('farmer_crown', [-4, -10.5, -3.5], [8, 2, 7], 'straw', 'head', 5)
    p('farmer_overalls', [-3.5, 1, -4], [7, 8, 1], 'denim', 'body', 5)
    p('farmer_patch', [1, 5, -4.5], [2, 2, 1], 'patch', 'body', 5)
    p('farmer_trouser_r', [-2.5, 0, -2.5], [4, 4, 5], 'denim', 'right_leg', 5)
    p('farmer_trouser_l', [-1.5, 0, -2.5], [4, 4, 5], 'denim', 'left_leg', 5)
    for suffix, x in [('r', -3), ('l', 2)]:
        p('farmer_strap_'+suffix, [x, 0, -4], [1, 4, 1], 'denim', 'body', 5)
    goggles('operator', 6)
    gloves('operator', 6)

    shirt('hunter', 'camo', 7)
    p('hunter_cap', [-5, -9, -4.5], [10, 3, 9], 'camo', 'head', 7)
    p('hunter_peak', [-4, -7, -6.5], [8, 1, 3], 'camo', 'head', 7)
    p('hunter_quiver', [1, -1, 3.5], [3, 10, 3], 'leather', 'body', 7)
    for x in [1, 3]:
        p('hunter_arrow_'+str(x), [x, -4, 4.5], [1, 5, 1], 'parcel', 'body', 7)
        p('hunter_feather_'+str(x), [x-.5, -4, 4], [2, 2, 2], 'iron', 'body', 7)
    p('crafter_apron', [-4, 0, -4.5], [8, 11, 2], 'leather', 'body', 8)
    p('crafter_pocket', [-2, 4, -5.5], [4, 3, 1], 'strap', 'body', 8)
    goggles('crafter', 8, high=True)
    gloves('crafter', 8)

    shirt('fisher', 'green', 9)
    p('fisher_brim', [-6, -7.5, -5.5], [12, 1, 11], 'green', 'head', 9)
    p('fisher_hat', [-4.5, -10.5, -4], [9, 3, 8], 'green', 'head', 9)
    p('fisher_waders', [-4, 4, -4.5], [8, 5, 1], 'boot', 'body', 9)
    p('fisher_wader_r', [-2.5, 0, -2.5], [4, 8, 5], 'boot', 'right_leg', 9)
    p('fisher_wader_l', [-1.5, 0, -2.5], [4, 8, 5], 'boot', 'left_leg', 9)
    for suffix, x, color in [('red', -3, 'red'), ('gold', 2, 'trim')]:
        p('fisher_lure_'+suffix, [x, 1, -4.5], [1, 2, 1], color, 'body', 9)
    p('ranch_brim', [-6.5, -8.5, -5.5], [13, 1, 11], 'leather', 'head', 10)
    p('ranch_crown', [-4, -11.5, -3.5], [8, 3, 7], 'leather', 'head', 10)
    p('ranch_band', [-4.5, -9.5, -4], [9, 1, 8], 'strap', 'head', 10)
    gloves('ranch', 10)

    p('mage_robe', [-5, 0, -4], [10, 13, 8], 'purple', 'body', 12)
    p('mage_trim', [-1, 0, -4.5], [2, 13, 1], 'trim', 'body', 12)
    p('mage_sleeve_r', [-4, 0, -3], [5, 12, 6], 'purple', 'right_arm', 12)
    p('mage_sleeve_l', [-1, 0, -3], [5, 12, 6], 'purple', 'left_arm', 12)
    p('mage_brim', [-6, -8.5, -5.5], [12, 1, 11], 'purple', 'head', 12)
    for i, size in enumerate([8, 6, 4, 2]):
        p('mage_hat_'+str(i), [-size/2, -10.5-i*2, -size/2], [size, 2, size], 'purple', 'head', 12)

    # A 40-unit armored body, wider stance and distinct heavy limbs; no scaling of wood.
    part('guard_head', [0, -6, 0], [-4.5, -10, -5], [9, 10, 10], 'plank')
    part('guard_body', [0, -2, 0], [-6, 0, -4], [12, 14, 8], 'armor')
    part('guard_right_arm', [-6, -2, 0], [-5, 0, -3], [5, 19, 6], 'steel_dark')
    part('guard_left_arm', [6, -2, 0], [0, 0, -3], [5, 19, 6], 'steel_dark')
    part('guard_right_leg', [-3.5, 12, 0], [-2.5, 0, -3], [5, 12, 6], 'steel_dark')
    part('guard_left_leg', [3.5, 12, 0], [-2.5, 0, -3], [5, 12, 6], 'steel_dark')
    part('guard_neck', [0, 0, 0], [-3, -6, -3], [6, 4, 6], 'iron')
    p('guard_helmet_top', [-5.5, -10, -5.5], [11, 2, 11], 'armor', 'guard_head', 0)
    p('guard_helmet_back', [-5.5, -8, 4.5], [11, 8, 1], 'armor', 'guard_head', 0)
    p('guard_helmet_r', [-5.5, -8, -5.5], [2, 8, 10], 'armor', 'guard_head', 0)
    p('guard_helmet_l', [3.5, -8, -5.5], [2, 8, 10], 'armor', 'guard_head', 0)
    p('guard_chestplate', [-6.5, 1, -5], [13, 10, 2], 'armor', 'guard_body', 0)
    p('guard_core', [-1.5, 3, -5.5], [3, 4, 1], 'purple', 'guard_body', 0)
    for suffix, x in [('right', -5), ('left', 0)]:
        p('guard_shoulder_'+suffix, [x-.5, -1, -4], [6, 6, 8], 'armor', 'guard_'+suffix+'_arm', 0)
        p('guard_hand_'+suffix, [x-.5, 14, -3.5], [6, 5, 7], 'armor', 'guard_'+suffix+'_arm', 0)
        p('guard_boot_'+suffix, [-3, 7, -4], [6, 5, 8], 'armor', 'guard_'+suffix+'_leg', 0)


def pixel(kind, x, y):
    palette = [rgb(c) for c in PALETTES[kind]]
    if kind == 'red':
        return rgb(['201b1d', '632329', 'b13d3c'][int(x//2 % 2 == 0) + int(y//2 % 2 == 0)])
    if kind == 'camo':
        return palette[(x//3 + y//2 + (x+y)//5) % 3]
    if kind in ('armor', 'steel_dark'):
        return palette[2 if y % 7 == 0 else 0 if x % 7 == 0 else 1]
    return palette[2 if (x+y*3) % 11 == 0 else 0 if y % 7 == 6 else 1]
