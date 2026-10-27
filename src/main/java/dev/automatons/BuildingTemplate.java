package dev.automatons;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;

/** Blocks and cosmetic displays, never schematic inventories or a replacement live totem. */
public final class BuildingTemplate {
  public record Cell(BlockPos local, BlockState state) {}

  public record Frame(BlockPos local, Item item, Direction facing) {}

  public final List<Cell> cells;
  public final BlockPos anchor;
  public final BlockPos size;
  public final List<Frame> frames;
  public final Map<Item, Integer> materials;
  public final JsonObject metadata;
  public final List<BlockPos> stands;
  public final boolean explicitAir;
  private final Map<Direction, Map<BlockPos, BlockPos>> collectorRotations = new EnumMap<>(Direction.class);

  private BuildingTemplate(List<Cell> cells, BlockPos anchor, BlockPos size, List<Frame> frames, JsonObject metadata) {
    this.cells = List.copyOf(cells);
    this.anchor = anchor;
    this.size = size;
    this.frames = List.copyOf(frames);
    this.metadata = metadata;
    explicitAir = metadata.get("version").getAsInt() >= 4;
    stands = new ArrayList<>();
    if (metadata.has("armor_stands"))
      metadata.getAsJsonArray("armor_stands").forEach(p -> stands.add(point(p)));
    Map<Item, Integer> counted = new LinkedHashMap<>();
    for (Cell c : cells) {
      ItemStack cost = cost(c.state);
      if (!cost.isEmpty()) counted.merge(cost.getItem(), cost.getCount(), Integer::sum);
    }
    for (Frame frame : frames) {
      counted.merge(Items.ITEM_FRAME, 1, Integer::sum);
      counted.merge(frame.item, 1, Integer::sum);
    }
    if (!stands.isEmpty()) counted.merge(Items.ARMOR_STAND, stands.size(), Integer::sum);
    this.materials = Collections.unmodifiableMap(counted);
  }

  public static BuildingTemplate load(ServerLevel level, String name) {
    try (InputStream in =
        BuildingTemplate.class.getResourceAsStream(
            "/data/automatons/buildings/" + name + ".json")) {
      if (in == null) throw new IOException("Missing blueprint " + name);
      JsonObject data =
          JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
              .getAsJsonObject();
      List<Cell> cells = new ArrayList<>();
      for (JsonElement value : data.getAsJsonArray("blocks")) {
        JsonObject b = value.getAsJsonObject();
        cells.add(
            new Cell(
                new BlockPos(b.get("x").getAsInt(), b.get("y").getAsInt(), b.get("z").getAsInt()),
                BlockStateParser.parseForBlock(
                        level.holderLookup(Registries.BLOCK), b.get("state").getAsString(), false)
                    .blockState()));
      }
      JsonArray a = data.getAsJsonArray("anchor");
      JsonArray size = data.getAsJsonArray("size");
      List<Frame> frames = new ArrayList<>();
      if (data.has("frames"))
        for (JsonElement value : data.getAsJsonArray("frames")) {
          JsonObject f = value.getAsJsonObject();
          JsonArray tile = f.getAsJsonArray("tile");
          Item item =
              net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                  new net.minecraft.resources.ResourceLocation(f.get("item").getAsString()));
          if (item == Items.AIR) throw new IOException("Unregistered display item");
          frames.add(
              new Frame(
                  new BlockPos(
                      tile.get(0).getAsInt(), tile.get(1).getAsInt(), tile.get(2).getAsInt()),
                  item, f.has("facing") ? Direction.from3DDataValue(f.get("facing").getAsInt()) : Direction.NORTH));
        }
      return new BuildingTemplate(
          cells,
          new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()),
          new BlockPos(size.get(0).getAsInt(), size.get(1).getAsInt(), size.get(2).getAsInt()),
          frames, data);
    } catch (Exception e) {
      throw new IllegalStateException("Invalid building blueprint " + name, e);
    }
  }

  public static Rotation rotation(Direction front) {
    return switch (front) {
      case EAST -> Rotation.CLOCKWISE_90;
      case SOUTH -> Rotation.CLOCKWISE_180;
      case WEST -> Rotation.COUNTERCLOCKWISE_90;
      default -> Rotation.NONE;
    };
  }

  public BlockPos world(BlockPos origin, Direction front, BlockPos local) {
    BlockPos offset = collectorRotations.computeIfAbsent(front, this::collectorOffsets)
        .getOrDefault(local, local.subtract(anchor).rotate(rotationFor(front)));
    return origin.offset(offset);
  }

  public static BlockPos point(JsonElement e) {
    JsonArray a = e.getAsJsonArray();
    return new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
  }

  public Rotation rotationFor(Direction front) {
    return rotation(metadata.has("default_front") ? front.getOpposite() : front);
  }

  public BlockPos logicalLocal(BlockPos logical) {
    return logical.subtract(metadata.has("logical_min") ? point(metadata.get("logical_min")) : BlockPos.ZERO);
  }

  public BlockPos logicalWorld(BlockPos origin, Direction front, BlockPos logical) {
    return origin.offset(logicalLocal(logical).subtract(anchor).rotate(rotationFor(front)));
  }

  public JsonObject worksite() { return metadata.has("worksite") ? metadata.getAsJsonObject("worksite") : new JsonObject(); }

  public BlockPos feature(BlockPos origin, Direction front, String name, BlockPos fallback) {
    JsonObject features = metadata.has("features") ? metadata.getAsJsonObject("features") : new JsonObject();
    return features.has(name) ? logicalWorld(origin, front, point(features.get(name))) : fallback;
  }

  private Map<BlockPos, BlockPos> collectorOffsets(Direction front) {
    Map<BlockPos, BlockPos> swaps = new HashMap<>();
    if (!worksite().has("collector_beds")) return swaps;
    for (JsonElement value : worksite().getAsJsonArray("collector_beds")) {
      JsonObject bed = value.getAsJsonObject();
      JsonArray bounds = bed.getAsJsonArray("bounds_xz");
      BlockPos original = logicalLocal(point(bed.get("collector")));
      int y = point(bed.get("collector")).getY();
      BlockPos a = logicalWorld(BlockPos.ZERO, front, new BlockPos(bounds.get(0).getAsJsonArray().get(0).getAsInt(), y, bounds.get(0).getAsJsonArray().get(1).getAsInt()));
      BlockPos b = logicalWorld(BlockPos.ZERO, front, new BlockPos(bounds.get(1).getAsJsonArray().get(0).getAsInt(), y, bounds.get(1).getAsJsonArray().get(1).getAsInt()));
      BlockPos desired = new BlockPos(Math.min(a.getX(), b.getX()) + 1, a.getY(), Math.min(a.getZ(), b.getZ()) + 1);
      Rotation inverse = switch (rotationFor(front)) {
        case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
        case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
        default -> rotationFor(front);
      };
      BlockPos displaced = desired.rotate(inverse).offset(anchor);
      swaps.put(original, desired);
      swaps.put(displaced, original.subtract(anchor).rotate(rotationFor(front)));
    }
    return swaps;
  }

  public static ItemStack cost(BlockState state) {
    if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.FARMLAND)) return new ItemStack(Items.DIRT);
    if (state.is(Blocks.WATER)) return new ItemStack(Items.WATER_BUCKET);
    if (state.isAir()
        || state.is(Automatons.TOTEM.get())
        || state.getBlock() instanceof DoorBlock
            && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) return ItemStack.EMPTY;
    return new ItemStack(
        state.getBlock() instanceof WallTorchBlock ? Items.TORCH : state.getBlock().asItem(),
        state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE
            ? 2
            : 1);
  }

  /** Build upward from the foundation; attach lights and door after their supports exist. */
  public List<Cell> placements() {
    return cells.stream()
        .filter(c -> !c.state.isAir() && !c.state.is(Automatons.TOTEM.get()))
        .sorted(
            Comparator.comparingInt(
                    (Cell c) -> {
                      if (c.state.getBlock() instanceof DoorBlock) return 100 + c.local.getY();
                      if (c.state.getBlock() instanceof LanternBlock
                          || c.state.getBlock() instanceof TorchBlock) return 90;
                      return c.local.getY();
                    })
                .thenComparingLong(c -> c.local.asLong()))
        .toList();
  }
}
