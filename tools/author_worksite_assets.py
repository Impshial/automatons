"""Editable 16px Hammer, recipe advancement and the current worksite item assets."""
from asset_pixels import *

from author_lumina import hammer
hammer()
write(RES/'assets/automatons/models/item/hammer.json', {
    'parent':'minecraft:item/handheld', 'textures':{'layer0':'automatons:item/hammer'}})
write(RES/'data/automatons/advancements/recipes/hammer.json', {
    'parent':'minecraft:recipes/root',
    'criteria':{'has_materials':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['minecraft:iron_ingot']}]}}},
    'rewards':{'recipes':['automatons:hammer']}})
print('Authored Hammer texture, model and recipe advancement.')
