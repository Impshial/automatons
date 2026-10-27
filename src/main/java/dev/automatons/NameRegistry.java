package dev.automatons;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.*;
import java.text.Normalizer;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.util.RandomSource;

/** One server-thread allocator. Issued keys are never recycled, including after rename or death. */
public final class NameRegistry {
  public static final int KEY_VERSION = 1;
  private static List<String> catalog;
  private final Map<UUID, String> current = new HashMap<>();
  private final Map<String, UUID> issued = new HashMap<>();
  private long pairCursor, suffix = 1;
  private int compoundPoolSize;
  private boolean pairsExhausted;

  public String name(UUID id) {
    return current.get(id);
  }

  public int issuedCount() {
    return issued.size();
  }

  public static String key(String name) {
    String normalized = Normalizer.normalize(name, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT);
    StringBuilder result = new StringBuilder();
    normalized
        .codePoints()
        .forEach(
            c -> {
              int type = Character.getType(c);
              if (type == Character.NON_SPACING_MARK
                  || type == Character.COMBINING_SPACING_MARK
                  || type == Character.ENCLOSING_MARK
                  || Character.isWhitespace(c)
                  || Character.isSpaceChar(c)
                  || type == Character.DASH_PUNCTUATION
                  || c == '\''
                  || c == 0x2019
                  || c == 0x2018
                  || c == 0x02bc
                  || c == 0xff07) return;
              String fold =
                  switch (c) {
                    case 'ł' -> "l";
                    case 'ø' -> "o";
                    case 'đ', 'ð' -> "d";
                    case 'ı' -> "i";
                    case 'æ' -> "ae";
                    case 'œ' -> "oe";
                    case 'þ' -> "th";
                    case 'ß' -> "ss";
                    default -> null;
                  };
              if (fold != null) result.append(fold);
              else result.appendCodePoint(c);
            });
    return result.toString();
  }

  public static List<String> catalog() {
    if (catalog != null) return catalog;
    List<String> names = new ArrayList<>();
    Map<String, String> keys = new HashMap<>();
    try (InputStream in =
        NameRegistry.class.getResourceAsStream("/data/automatons/names/catalog.json")) {
      if (in == null) throw new IOException("Missing name catalog");
      var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT);
      JsonObject root =
          JsonParser.parseReader(new InputStreamReader(in, decoder)).getAsJsonObject();
      JsonArray groups = root.getAsJsonArray("groups");
      int masculine = 0, feminine = 0;
      for (JsonElement group : groups)
        for (String selection : List.of("masculine", "feminine")) {
          JsonArray entries = group.getAsJsonObject().getAsJsonArray(selection);
          if (selection.equals("masculine")) masculine += entries.size();
          else feminine += entries.size();
          for (JsonElement entry : entries) {
            String value = entry.getAsString(),
                canonical = key(value),
                previous = keys.putIfAbsent(canonical, value);
            if (canonical.isBlank() || previous != null)
              throw new IOException(
                  "Canonical name collision: ["
                      + previous
                      + "] / ["
                      + value
                      + "] key="
                      + canonical);
            names.add(value);
          }
        }
      if (root.get("schema_version").getAsInt() != 1
          || groups.size() != 32
          || masculine != 320
          || feminine != 320)
        throw new IOException(
            "Expected 32 groups and 320+320 names; got "
                + groups.size()
                + ", "
                + masculine
                + "+"
                + feminine);
    } catch (Exception e) {
      throw new IllegalStateException("Invalid bundled Automaton names", e);
    }
    catalog = List.copyOf(names);
    return catalog;
  }

  private String reserve(UUID id, String display) {
    String spelling = Normalizer.normalize(display.strip(), Normalizer.Form.NFC);
    issued.put(key(spelling), id);
    current.put(id, spelling);
    return spelling;
  }

  public String resolve(UUID id, String legacy, RandomSource random) {
    if (current.containsKey(id)) return current.get(id);
    if (!key(legacy).isBlank() && !issued.containsKey(key(legacy))) return reserve(id, legacy);
    return allocate(id, random, catalog());
  }

  public String allocate(UUID id, RandomSource random, List<String> pool) {
    if (current.containsKey(id)) return current.get(id);
    List<String> available = pool.stream().filter(n -> !issued.containsKey(key(n))).toList();
    if (!available.isEmpty()) return reserve(id, available.get(random.nextInt(available.size())));
    if (pool.size() < 2) throw new IllegalArgumentException("Compound pool needs two names");
    if (compoundPoolSize != pool.size()) {
      compoundPoolSize = pool.size();
      pairsExhausted = false;
      pairCursor = 0;
    }
    long combinations = (long) pool.size() * (pool.size() - 1);
    for (long attempts = 0; !pairsExhausted && attempts < combinations; attempts++) {
      long index = Math.floorMod(pairCursor++, combinations);
      int first = (int) (index / (pool.size() - 1)), second = (int) (index % (pool.size() - 1));
      if (second >= first) second++;
      String compound = pool.get(first) + "-" + pool.get(second);
      if (!issued.containsKey(key(compound))) return reserve(id, compound);
    }
    pairsExhausted = true;
    String stem = pool.get(0) + "-" + pool.get(1);
    // Every failed suffix consumes a distinct existing key, so this search is bounded.
    for (int attempts = 0, limit = issued.size() + 1; attempts < limit; attempts++) {
      String compound = stem + "-" + suffix++;
      if (!issued.containsKey(key(compound))) return reserve(id, compound);
    }
    throw new IllegalStateException("Name suffix allocation invariant");
  }

  public boolean rename(UUID id, String requested) {
    String canonical = key(requested);
    if (canonical.isBlank()
        || requested.codePointCount(0, requested.length()) > 128
        || requested.codePoints().anyMatch(Character::isISOControl)) return false;
    UUID holder = issued.get(canonical);
    // A retired spelling is also reserved, including the worker's own previous names.
    if (holder != null
        && !(holder.equals(id) && canonical.equals(key(current.getOrDefault(id, "")))))
      return false;
    reserve(id, requested);
    return true;
  }

  public int[] migrate(Map<UUID, CompoundTag> workers, RandomSource random) {
    List<UUID> ids = workers.keySet().stream().sorted().toList();
    int named = 0, conflicts = 0;
    // Preserve all indexed custom names before assigning any unused catalog entries.
    for (UUID id : ids)
      if (!current.containsKey(id)) {
        String legacy = WorkerNames.recordName(workers.get(id));
        if (!key(legacy).isBlank()) {
          if (!issued.containsKey(key(legacy))) {
            reserve(id, legacy);
            named++;
          } else {
            conflicts++;
            org.slf4j.LoggerFactory.getLogger("automatons.names")
                .warn("Legacy name conflict: {} [{}]", id, legacy);
          }
        }
      }
    for (UUID id : ids) {
      if (!current.containsKey(id)) {
        allocate(id, random, catalog());
        named++;
      }
      WorkerNames.writeName(workers.get(id), current.get(id));
    }
    return new int[] {named, conflicts};
  }

  public CompoundTag save() {
    CompoundTag n = new CompoundTag();
    n.putInt("KeyVersion", KEY_VERSION);
    n.putLong("PairCursor", pairCursor);
    n.putLong("Suffix", suffix);
    n.putInt("CompoundPoolSize", compoundPoolSize);
    n.putBoolean("PairsExhausted", pairsExhausted);
    ListTag names = new ListTag(), history = new ListTag();
    current.forEach(
        (id, name) -> {
          CompoundTag c = new CompoundTag();
          c.putUUID("Id", id);
          c.putString("Name", name);
          names.add(c);
        });
    issued.forEach(
        (key, id) -> {
          CompoundTag c = new CompoundTag();
          c.putUUID("Id", id);
          c.putString("Key", key);
          history.add(c);
        });
    n.put("Names", names);
    n.put("Issued", history);
    return n;
  }

  public static NameRegistry load(CompoundTag n) {
    if (n.contains("KeyVersion") && n.getInt("KeyVersion") != KEY_VERSION)
      throw new IllegalStateException("Unsupported name canonicalization version");
    NameRegistry registry = new NameRegistry();
    registry.pairCursor = Math.max(0, n.getLong("PairCursor"));
    registry.suffix = Math.max(1, n.getLong("Suffix"));
    registry.compoundPoolSize = n.getInt("CompoundPoolSize");
    registry.pairsExhausted = n.getBoolean("PairsExhausted");
    for (Tag t : n.getList("Issued", 10)) {
      CompoundTag c = (CompoundTag) t;
      if (c.hasUUID("Id")) registry.issued.put(c.getString("Key"), c.getUUID("Id"));
    }
    for (Tag t : n.getList("Names", 10)) {
      CompoundTag c = (CompoundTag) t;
      if (c.hasUUID("Id")) {
        UUID id = c.getUUID("Id");
        String display = c.getString("Name");
        UUID holder = registry.issued.get(key(display));
        if (holder == null || holder.equals(id)) registry.reserve(id, display);
      }
    }
    return registry;
  }
}
