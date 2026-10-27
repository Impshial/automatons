"""Author the expansion's source handbook; never edit an installed player's resource pack."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BOOK = ROOT / 'resourcepacks/Automatons-Guide/assets/automatons/guides/automatons/handbook'

def page(name, text, title=None, icon='automatons:automaton', position=7):
    path = BOOK / name
    if path.exists():
        front = path.read_text(encoding='utf-8').split('---', 2)[1]
    else:
        parent = str(Path(name).parent / 'index.md').replace('\\', '/')
        front = f'\nnavigation:\n  title: "{title}"\n  parent: "{parent}"\n  position: {position}\n  icon: "{icon}"\n'
    path.write_text('---' + front + '---\n\n' + text.strip() + '\n', encoding='utf-8')

page('professions/index.md', '''# Thirteen professions

Configure a production Totem, then use the Wand to assign an ordinary Automaton. Assignment gives the worker the Totem's profession. Each production site supports six ordinary workers. Existing workers retain their identity, name, health, charge and real inventory when reassigned.

Operatortons are assigned directly to a parked Machine. Guardingtons require an armored body and use separate protection assignments; they do not occupy production places.

<SubPages icons={true} />''')

page('getting-started.md', '''# Your first working network

1. Mine Lumite Ore with a stone pickaxe or better. Collect personal Lumina from eligible natural vegetation.
2. Craft an Infusing Pedestal and Empty Cores. Infuse raw shards at 25 Lumina each; craft four infused shards around an Empty Core for a Lumina Core.
3. Craft an Automaton Pedestal, Wand, Totem and the six ordinary body parts. Assemble the parts in the pedestal's body-shaped slots. The pedestal becomes the worker's home.
4. Place the Automaton on clear ground. It starts paused. Open the Totem and choose **Building**, then its orientation. This bootstrap site does not need an already completed workshop.
5. Put a Hammer in the worker's tool slots. Supply materials in reachable chests outside the building footprint. Use the site's missing-material list for quantities.
6. Select the worker with the Wand and right-click the configured Totem. It becomes a Buildington and builds its shed from real supplies.
7. Configure more Totems on the same home network. Builders construct their layouts. Assign ordinary workers to give them the appropriate jobs, up to six per Totem.
8. Supply tools, planting items and full Luminall bottles. Workers have 200 operating Lumina and consume one point per minute of loaded time. A full 250 mB bottle adds 125 points.

Use a fresh test world for this prerelease expansion. A profession change preserves existing world blocks and creates a paused replacement construction plan; it does not demolish the old building.

[Totems and assignments](network/totems.md) · [All professions](professions/index.md) · [Luminall](magic/luminall.md)''')

page('network/automatons.md', '''# Workers, equipment and recovery

Right-click an owned worker for **Status** and **Inventory**. Status shows its name, read-only profession, assignment, health, operating charge and current task. Assign it to a configured Totem to change profession. Operatortons use Machine assignment; armored Guardingtons retain their body and profession.

<ItemImage id="automatons:automaton" scale="3" />

Ordinary workers have three profession-filtered equipment slots and nine cargo slots. Runnertons have eighteen cargo slots. Incompatible tools move to cargo or available linked chest storage during reassignment, with their names, enchantments and durability intact. If everything cannot fit, reassignment waits without partially changing the inventory.

Workers have 200 operating Lumina, initially full. They consume one point per 1,200 loaded ticks. At two points or less they stop working and return for a full bottle. Drinking takes 32 ticks and returns an empty bottle; overflow beyond capacity is spent. At zero, movement and work stop and the eyes turn black. Give the worker a full bottle to restart it. Charge does not restore health.

Packing preserves identity, name, home, profession and charge, while dropping carried equipment/cargo once. On death, belongings and recoverable body parts go into an ordinary chest with a gravestone. The torso always survives; three to six total parts are recovered. A blocked grave location keeps its contents pending rather than destroying existing valuable containers.

Nearby dropped-item pickup uses available cargo space and respects item ownership. Flower collection stays within two blocks of the current route, skips protected collector beds and never diverts a worker during combat, escape or climbing.

[Wand controls](wand.md) · [Guardington](../professions/guardington.md) · [Healing](../professions/luminariton.md)''')

page('network/totems.md', '''# Configured Totems

An unconfigured Totem opens a profession chooser. Choose one of eleven production purposes, then use **Overview**, **Workers** and **Settings**. Overview shows current work, storage and shortages. Workers lists production workers and guards separately. Settings contains only controls relevant to the selected profession.

<Recipe id="automatons:totem" />

A Totem accepts at most **six ordinary workers** from one owner and home network. Assignment automatically gives each worker the site's profession. Guard protection does not consume those six places. Operatortons assigned to a Machine do not count at unrelated Totems.

The site's name can be changed. Find outlines an owned loaded worker for 30 seconds. Unloaded workers retain their identity and assignment but cannot work or receive remote items.

Changing a Totem's profession requires confirmation and no assigned production workers or active work claims. Existing blocks remain. The replacement plan starts paused so its placement and supplies can be reviewed before construction.

Lumber and farm work ranges default to 32 blocks; mine surface storage to six; builder and warehouse to eight. Hunters search a fixed 96-block area while using their local shed for supplies. Mine depots, the authored quarry and registered garden/pasture use their own geometry. Both halves of a double chest need loaded, usable access.

[Construction](../professions/buildington.md) · [Professions](../professions/index.md)''')

page('professions/minington.md', '''# Minington

Assign an ordinary worker to a Mine Totem and provide a suitable pickaxe, optional shovel, ladders, chests and ordinary torches. A Buildington constructs the surface minehead. Miningtons then build the shaft and underground infrastructure from real supplies.

The first landing is **20 blocks below** the entrance; subsequent landings are **four blocks lower**. Each landing is **5×5 with three blocks of clearance**. Eight chest blocks form four double chests, stacked in two tiers against the back wall. Earlier depots remain available.

Each floor has a three-wide, two-high center corridor extending 25 blocks beyond the landing, plus **20 side branches in ten opposing pairs**. The first four branches extend 24 blocks; the other sixteen extend 25. Workers claim separate branches and resume their saved positions after unloading. A new floor starts only after the current floor's work is settled and workers have left safely.

A side branch stops at an exposed cave or fluid boundary. A central-corridor obstruction pauses the floor for player inspection and acknowledgement. Full storage, missing tools, blocked access and unloaded chunks are waits, never completed excavation.

Torches are real inventory items. Managed workspaces need actual block light of at least eight; skylight, glowing ore and held lights do not substitute. Couriers can supply tools and torches and collect registered depots through the ladder route. Miningtons can also clear eligible pickaxe obstacles requested by Lumbertons on the same network.

[Mine entrance](../worksites/mine.md) · [Quarryton](quarryton.md) · [Recovery](../troubleshooting.md#mine)''')

page('worksites/mine.md', '''# Mine entrance and landings

Configure a Mine Totem and let a Buildington construct its surface entrance. The surface shaft stays open. Supply ladders, torches and **eight chests per landing**. The first landing is 20 blocks down, followed by four-block level spacing.

<GameScene zoom="1.3" interactive={true}>
  <ImportStructure src="../assets/structures/minehead.nbt" />
  <IsometricCamera yaw="135" pitch="30" />
</GameScene>

The illustration shows the surface minehead. Underground geometry uses the supplied landing, shaft-step and strip-level layouts. Each landing provides four double chests; earlier depots remain registered for storage and courier collection.

<a name="supplies" />
## Supplies and recovery

Use the missing-material list for real construction quantities. Chest halves cost actual chest items and containers start empty. Keep access, chest lids and supported approaches clear. Inspect reported caves or fluids before acknowledging and resuming work.

[Minington details](../professions/minington.md).''')

page('professions/farmington.md', '''# Farmington

Assign to a completed Farm Totem and provide a hoe, planting stock, one water bucket and a glass block. The three Settings sample slots select **Left**, **Middle** and **Right** strips. Samples are not consumed. Wheat or wheat seeds select wheat; carrots, potatoes, beetroot or beetroot seeds select their corresponding crops. Clear a slot to disable planting there.

The default field is **9×9**, divided into three **3×9** strips. E5 is the water source, with a full glass block above it, leaving 80 crop cells. Existing suitable farmland is adopted instead of being replaced. A changed selector waits for an existing crop to mature before harvesting and planting its replacement.

Workers use real stock, keep planting reserves, and optionally use bone meal. Dead Crop is cleared without drops and replaced from stock. No harvest is awarded for dead vegetation. Multiple farmers share cell claims and irrigation rather than duplicating water or harvests.

[Farm setup](../worksites/farm.md).''')

page('professions/runnerton.md', '''# Runnerton

Assign to a Warehouse Totem. The backpack gives eighteen cargo slots. A Runnerton physically carries items among loaded sites on the same owner/home network, retaining names, enchantments, durability and other item data.

The network publishes shortages for tools, torches, arrows, recharge bottles, planting stock, ranch supplies and crafting ingredients. Local work reserves are protected. Tools, torches and saplings remain out of ordinary bulk collection, while explicit requests may transport them. Display armor is not a stock source.

Couriers use actual doors, mine ladders and the mage workshop ladder. Full storage, unavailable chunks and changed destinations retain the parcel's real cargo until delivery or return is possible. Packing releases reservations and drops the actual cargo once.

The warehouse uses eight double chests with a clear center aisle. Builders can start from partial material supplies and couriers can assist construction before their own warehouse is complete.

[Warehouse](../worksites/warehouse.md).''')

page('professions/buildington.md', '''# Buildington

Configure a Building Totem, equip an ordinary Automaton with a Hammer and assign it using the Wand. Assignment gives it the Buildington profession; no completed workshop or manual job selector is required. It builds its own shed before taking other network projects.

Supply actual materials in reachable chests outside the footprint. Partial kits are useful. The missing-material list includes real chest halves, ladders, machines, frames, display items and armor stands. Containers and stands begin empty. Builders place from supported positions outside the footprint and preserve progress through shortages and reloads.

The supplied schematic geometry, orientation and explicit clearing cells govern the new layouts. Existing valuable block entities are not overwritten. Move a reported conflicting chest or workstation yourself, then resume. Omitted cells are preserved, and changing a Totem's profession never automatically demolishes its old building.

[Builder shed](../worksites/builders-shed.md).''')

page('professions/operatorton.md', '''# Operatorton

Use the Wand to assign an ordinary Automaton from the same home network directly to a parked Machine with an empty seat. This gives it the Operatorton profession and preserves its identity and supplies. It needs no production Totem and occupies no production-worker place.

The operator physically approaches and boards the Machine. Only an actual seated driver enables piloted work. Recall, unloading or a missing driver pauses the Machine's job. Operating charge is separate from engine fuel: the operator stops and leaves safely to obtain its own recharge bottle when low.

[The Machine](../machine/index.md) · [Programs](../machine/programs.md).''')

new = {
'hunterton': ('Hunterton', 'minecraft:bow', '''Searches a fixed 96-block area around a Hunting Totem for eligible adult passive animals. Babies, equines, cats, wolves, pets and managed ranch livestock are excluded, including protected livestock that escape their pen.

Supply real swords or bows, arrows and recharge bottles. Attacks use weapon damage, enchantments, ammunition and durability, with ordinary loot. Hunters check targets again before striking and avoid friendly fire. They can retreat to accessible owned network sheds and resume after the threat clears.'''),
'crafterton': ('Crafterton', 'minecraft:crafting_table', '''Uses actual RecipeManager recipes, crafting-table access and real furnace or blast-furnace processing. Place requested ingredients and fuel in network storage. Container remainders and output capacity are checked before committing a craft.

Network tools have priority, followed by torches, construction, other requested items and the decorative iron armor set. The workshop stand starts empty; missing armor pieces are crafted last. With couriers available, the crafter stays at its workshop. Without them, it fetches stock and delivers output physically. Recipe searches are bounded; unsupported or cyclic recipes show a wait reason instead of inventing materials. Luminall and infused shards require Luminariton service.'''),
'fisherton': ('Fisherton', 'minecraft:fishing_rod', '''Uses one of six exclusive fishing positions on the supplied three-wide, eight-long dock. Keep the center aisle open. The required 7×10 water rectangle contains 70 actual source-water cells; a tiny pool or flowing-water substitute does not qualify.

Supply fishing rods and bottles. Vanilla fishing timing and loot conditions use real water, weather, enchantments and rod durability. The seated pose and chair are visual; catching fish does not award a player's statistics or XP. Workers release their bay when reassigned or interrupted.'''),
'rancherton': ('Rancherton', 'minecraft:shears', '''Tends sheep, cows, chickens and pigs inside the supplied fenced pasture. Each species defaults to a target of eight, configurable from two to 64 with its own toggle. Bring real animals; the ranch does not spawn livestock.

Provide shears, buckets, leads, wheat, seeds and carrots as needed. Breeding uses actual food and normal cooldowns. Shearing uses real shears; eggs are collected from drops. Cow milk is transferred using a real bucket to a compatible tank, then the bucket is reused. Each cow has a saved 24,000-tick milk cooldown.

Surplus adults are led physically into the shed and dispatched with the gate closed. Babies and the last two adults are protected. The lead is recovered. Managed livestock retain their hunting protection after escape or reload. Repair broken fencing before work resumes.'''),
'quarryton': ('Quarryton', 'minecraft:diamond_pickaxe', '''Excavates the supplied 15×15 footprint in complete horizontal layers. The default bottom is the dimension's minimum build height; Settings may choose a higher bottom. Bedrock stays intact. Quarrying is separate from Minington strip mining.

The access ladder and its backing remain outside the excavation footprint. Supply real ladders, suitable filler and torches, plus pickaxes or shovels. Workers contain reachable fluids with paid filler and keep workspaces at block-light level eight. Caves pause work for inspection and acknowledgement. A full inventory returns through the surface route to unload; waiting for a path, tool or storage never marks cells finished.'''),
'luminariton': ('Luminariton', 'automatons:luminall_bottle', '''Works in the supplied raised tower and flower courtyard. It physically climbs the rear ladder for workshop storage and infusion, then returns to collectors and the ground-level healing clearing. Ordinary workers and couriers may use that ladder; guards stay on the ground.

Its 200 operating points are separate from a 400-point service reserve, initially empty. Full bottles fund each meter independently. At 125 service points or less it replenishes toward 400 when stock is available. Operating recharge takes priority. Healing reaches four blocks from the actual mage, restores up to one HP per recipient per second, and costs two service points per HP. It prioritizes the most injured fraction and can heal itself. Injured network workers at half health or less seek a safe visit.

Supply empty glass bottles, buckets, raw shards and eligible living plants. Each full bottle costs 250 mB from a real collector. Infusing costs 25 service points per shard. Tank transfers conserve fluid. The twelve 4×4 beds contain one collector and fifteen planting cells each. The mage clears dead vegetation without drops and plants actual suitable stock; it never grows trees or redesigns paths and the healing clearing.'''),
'guardington': ('Guardington', 'minecraft:iron_chestplate', '''Requires six armored body parts assembled at a pedestal. Armored recipes replace each log in the ordinary counterpart with an iron ingot; the torso retains two Lumina Cores. An ordinary worker cannot change into an armored guard.

Guards have 60 HP, 12 base armor and a 1.4-wide, 2.5-high body. Supply swords or bows, arrows, recharge bottles and optional armor. Fists deal two damage per second when no weapon is available. Use the guard's controls for a single protected site or network circuit patrol. Guards have a separate roster and do not use the six production slots.

They defend against Zombies, Skeletons, Spiders and Cave Spiders. They do not pursue Creepers. Patrols stay on accessible ground and avoid underground mines, quarry pits and the mage's raised workshop. Recall changes them to a ten-block home perimeter; releasing recall restores the previous patrol mode.'''),
}
for offset, (name, (title, icon, body)) in enumerate(new.items(), 7):
    page(f'professions/{name}.md', f'# {title}\n\n{body}', title, icon, offset)

page('magic/luminall.md', '''# Luminall, collectors and upkeep

The fixed conversion is **one Lumina point = two mB**. A full bottle holds **250 mB = 125 points**, a bucket **1,000 mB = 500 points**, and an 8,000 mB tank holds **4,000 points**. The Machine's engine capacity remains 10,000 points.

<Recipe id="automatons:tank" />
<Recipe id="automatons:lumina_collector" />

The Collector recipe uses four Stone, **two Infused Lumite Shards** and an empty Tank. Its reservoir holds 4,000 mB by default. It produces at most **five mB every 100 ticks**, one quarter of the original rate. Per-source fractions accumulate; low-yield plants still produce over time. Overlapping collectors share one source budget, and full collectors stop extracting.

## Source footprint and exhaustion

The world-aligned footprint is 4×4: X/Z offsets −1 through +2, with four blocks of vertical reach in either direction. Eligible sources include flowers, mature supported crops, ripe berries, stems, pumpkins/melons, grass and recognized living trees. Planted vegetation can contribute; structural wood and immature crops do not. Tall plants are one source.

Non-tree plants begin with 200 lifetime Lumina and crops with 100, configurable through plantLifetimeLumina and cropLifetimeLumina. Only actual extraction spends that reserve. Growth and reloads preserve it. Exhaustion replaces the source with **Dead Plant** or **Dead Crop**. These blocks do not grow, accept bone meal or drop anything when broken. Trees and leaves are never killed by collector depletion.

## Containers and recharge

Place a Glass Bottle on a collector by right-clicking; it fills from actual stored fluid. Retrieve it with an empty hand. Players can drink partial bottles proportionally. Automatons require a full bottle, use it for 32 ticks and return an empty Glass Bottle. Any worker-meter overflow is spent.

Buckets transfer 1,000 mB. Compatible pipes extract through collector sides/bottom; its top is reserved for the bottle. Tanks accept compatible registered fluids on all faces and never mix fluids. Shift-right-click a Tank with the Wand to carry its contents. Breaking a Tank normally drops an empty tank and spills its fluid; a Collector retains its stored fluid and bottle when broken.

<Recipe id="automatons:lumina_drain" />

Place a Lumina Drain above a Tank and stand on it to convert personal energy at **five points per second = ten mB**. Full or incompatible tanks refuse the transfer. Fractional energy remains conserved.

[Luminariton service](../professions/luminariton.md) maintains collectors, bottles, living plants and healing stock.''')

page('troubleshooting.md', '''# Troubleshooting

Read the worker's Status and the site's Overview before changing anything. A waiting message identifies the current missing supply, blocked route or unavailable operation.

## Worker will not start

Configure the Totem before assignment, use an ordinary body for production, and stay within six production workers. Check owner/home network, recall, worksite readiness, required tools and operating charge. A powered-down worker needs a full bottle. Unloaded chunks do not advance work or recharge time.

## Construction waiting

Supply missing materials in reachable chests outside the footprint. Keep lids and approaches open. Partial kits are accepted. Existing valuable containers are preserved: move a conflicting block yourself, then resume. A changed profession starts a paused plan and leaves the old building standing.

<a name="mine" />
## Mine or quarry waiting

Finish the surface site, supply real ladders, chests, torches and tools, and keep the access route intact. Minington landings use eight chest blocks each. Quarrytons also need suitable filler. Actual block light must reach eight. Repair a broken shaft or blocked chest lid. Inspect cave/fluid alerts before acknowledging them; storage and path waits do not count as finished excavation.

## Couriers and crafting

Keep the entire route loaded. Local planting/construction reserves remain protected. Explicit supply requests can move tools and torches; ordinary bulk collection leaves spare equipment in place. A full or changed destination retains real cargo. Crafters need valid recipes, a table, compatible furnaces and actual fuel. A missing recipe or cyclic dependency cannot produce free output.

## Ranch and garden

Repair missing fences and keep the handling gate clear. Bring livestock and actual supplies. Babies and a minimum breeding pair are protected. For mage service, keep the tower ladder and upper dismount clear, supply empty bottles and living plants, and ensure collectors contain fluid. Dead plants/crops drop nothing.

## Infusion and handbook

Player infusion needs the shard's donor within four blocks with energy available. Partial shards preserve paid progress. The handbook needs GuideME 20.1.15 for Forge 1.20.1 plus the enabled Automatons-Guide resource pack. Use F3+T after editing that pack.''')

# Short corrections in pages whose unchanged recipes/scenes remain relevant.
for path in BOOK.rglob('*.md'):
    text = path.read_text(encoding='utf-8')
    text = text.replace('250 mB = five Lumina', '250 mB = 125 Lumina')
    text = text.replace('two underground double chests', 'four double chests at each underground landing')
    text = text.replace('docs/RUNNINGTON.md', 'docs/RUNNERTON.md')
    path.write_text(text, encoding='utf-8')
print('Updated expansion handbook source pages.')
