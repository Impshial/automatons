package dev.automatons;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.item.*;

/** Population settings and expected births belong to the site, not to an individual rancher. */
public final class RanchPlan {
  public final int[] targets = {8, 8, 8, 8};
  public final boolean[] enabled = {true, true, true, true};
  public UUID handling;
  private record Birth(UUID a, UUID b, int species, long until) {}
  private final List<Birth> births = new ArrayList<>();
  public static int species(Animal animal) {
    return animal instanceof Sheep ? 0 : animal instanceof Cow ? 1 : animal instanceof Chicken ? 2 : animal instanceof Pig ? 3 : -1;
  }
  public static Item food(int species) { return species == 2 ? Items.WHEAT_SEEDS : species == 3 ? Items.CARROT : Items.WHEAT; }
  public int pending(ServerLevel level, int species) {
    births.removeIf(b -> b.until <= level.getGameTime()
        || level.getEntity(b.a) instanceof Animal a && (!a.isAlive() || !a.isInLove())
        || level.getEntity(b.b) instanceof Animal a && (!a.isAlive() || !a.isInLove()));
    return (int)births.stream().filter(b -> b.species == species).count();
  }
  public void expect(Animal a, Animal b) { births.add(new Birth(a.getUUID(), b.getUUID(), species(a), a.level().getGameTime() + 600)); }
  public CompoundTag save() {
    var n = new CompoundTag(); n.putIntArray("Targets", targets);
    for (int i = 0; i < 4; i++) n.putBoolean("Enabled" + i, enabled[i]);
    if (handling != null) n.putUUID("Handling", handling);
    ListTag list = new ListTag();
    for (Birth b : births) { var tag = new CompoundTag(); tag.putUUID("A", b.a); tag.putUUID("B", b.b); tag.putInt("Species", b.species); tag.putLong("Until", b.until); list.add(tag); }
    n.put("Births", list); return n;
  }
  public void load(CompoundTag n) {
    int[] values = n.getIntArray("Targets");
    for (int i = 0; i < 4; i++) { targets[i] = values.length > i ? Math.max(2, Math.min(64, values[i])) : 8; enabled[i] = !n.contains("Enabled" + i) || n.getBoolean("Enabled" + i); }
    handling = n.hasUUID("Handling") ? n.getUUID("Handling") : null; births.clear();
    for (Tag value : n.getList("Births", 10)) { var b = (CompoundTag)value; births.add(new Birth(b.getUUID("A"), b.getUUID("B"), b.getInt("Species"), b.getLong("Until"))); }
  }
}
