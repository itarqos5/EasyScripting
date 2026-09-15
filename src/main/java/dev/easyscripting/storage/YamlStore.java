package dev.easyscripting.storage;

import dev.easyscripting.core.Checks;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.plugin.java.JavaPlugin;

/** Disk is owned by one bounded writer. Callers submit detached serialized snapshots. */
public final class YamlStore implements AutoCloseable {
  private final Path root;
  private final java.util.logging.Logger logger;
  private final ThreadPoolExecutor writer =
      new ThreadPoolExecutor(
          1,
          1,
          0,
          TimeUnit.SECONDS,
          new ArrayBlockingQueue<>(4096),
          r -> {
            Thread t = new Thread(r, "EasyScripting-storage");
            t.setDaemon(true);
            return t;
          },
          new ThreadPoolExecutor.AbortPolicy());

  public YamlStore(JavaPlugin plugin) {
    this(plugin.getDataFolder().toPath(), plugin.getLogger());
  }

  public YamlStore(Path directory, java.util.logging.Logger logger) {
    this.logger = Objects.requireNonNull(logger);
    root = directory.toAbsolutePath().normalize();
  }

  public Path path(String folder, String id) {
    Checks.id(folder);
    Checks.id(id);
    Path result = root.resolve(folder).resolve(id + ".yml").normalize();
    if (!result.startsWith(root))
      throw new IllegalArgumentException("Storage path is outside plugin data.");
    return result;
  }

  public YamlConfiguration read(String folder, String id) {
    return read(path(folder, id));
  }

  public static YamlConfiguration read(Path file) {
    YamlConfiguration yaml = new YamlConfiguration();
    if (!Files.exists(file)) return yaml;
    try {
      yaml.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
      return yaml;
    } catch (IOException | InvalidConfigurationException ex) {
      throw new IllegalArgumentException(file + ": invalid YAML: " + ex.getMessage(), ex);
    }
  }

  public Map<String, YamlConfiguration> load(String folder) {
    Path dir = root.resolve(Checks.id(folder));
    Map<String, YamlConfiguration> result = new TreeMap<>();
    if (!Files.isDirectory(dir)) return result;
    try (var files = Files.list(dir)) {
      files
          .filter(p -> p.getFileName().toString().endsWith(".yml"))
          .sorted()
          .forEach(
              p -> {
                String id = p.getFileName().toString().replaceFirst("\\.yml$", "");
                try {
                  result.put(Checks.id(id), read(p));
                } catch (IllegalArgumentException ex) {
                  logger.warning(ex.getMessage());
                }
              });
    } catch (IOException ex) {
      throw new IllegalArgumentException(dir + ": cannot list files", ex);
    }
    return result;
  }

  public CompletableFuture<Void> save(String folder, String id, YamlConfiguration yaml) {
    // Bukkit objects become plain values on the owning thread; YAML encoding and IO run on the
    // writer.
    Map<?, ?> values = (Map<?, ?>) detach(yaml);
    Map<String, List<String>> comments = new LinkedHashMap<>(), inline = new LinkedHashMap<>();
    for (String key : yaml.getKeys(true)) {
      if (!yaml.getComments(key).isEmpty())
        comments.put(key, new ArrayList<>(yaml.getComments(key)));
      if (!yaml.getInlineComments(key).isEmpty())
        inline.put(key, new ArrayList<>(yaml.getInlineComments(key)));
    }
    List<String> header = new ArrayList<>(yaml.options().getHeader());
    List<String> footer = new ArrayList<>(yaml.options().getFooter());
    Path destination = path(folder, id);
    return submit(
        () -> {
          YamlConfiguration document = new YamlConfiguration();
          restoreSections(document, values);
          comments.forEach(document::setComments);
          inline.forEach(document::setInlineComments);
          document.options().setHeader(header).setFooter(footer);
          atomicWrite(destination, document.saveToString());
        });
  }

  /** Accepts an already immutable tree of plain values, prepared incrementally by large jobs. */
  public CompletableFuture<Void> saveDetached(String folder, String id, Map<?, ?> values) {
    Path destination = path(folder, id);
    return submit(
        () -> {
          YamlConfiguration document = new YamlConfiguration();
          values.forEach((key, value) -> document.set(String.valueOf(key), value));
          atomicWrite(destination, document.saveToString());
        });
  }

  private static void restoreSections(ConfigurationSection section, Map<?, ?> values) {
    values.forEach(
        (key, value) -> {
          String name = String.valueOf(key);
          // Serialized Bukkit items/vectors keep their == discriminator and are encoded as maps.
          if (value instanceof Map<?, ?> nested && !nested.containsKey("=="))
            restoreSections(section.createSection(name), nested);
          else section.set(name, value);
        });
  }

  public static Object detach(Object value) {
    if (value instanceof ConfigurationSection section) return detach(section.getValues(false));
    if (value instanceof ConfigurationSerializable serializable) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("==", ConfigurationSerialization.getAlias(serializable.getClass()));
      map.putAll(serializable.serialize());
      return detach(map);
    }
    if (value instanceof Map<?, ?> source) {
      Map<String, Object> copy = new LinkedHashMap<>();
      source.forEach((key, element) -> copy.put(String.valueOf(key), detach(element)));
      return Collections.unmodifiableMap(copy);
    }
    if (value instanceof Collection<?> collection)
      return collection.stream().map(YamlStore::detach).toList();
    if (value == null
        || value instanceof String
        || value instanceof Number
        || value instanceof Boolean) return value;
    throw new IllegalArgumentException(
        "Unsupported YAML snapshot value: " + value.getClass().getName());
  }

  public CompletableFuture<Void> write(Path path, String text) {
    return submit(() -> atomicWrite(path, text));
  }

  public CompletableFuture<Void> delete(String folder, String id) {
    Path source = path(folder, id);
    return submit(
        () -> {
          if (Files.exists(source)) {
            Path trash = root.resolve("trash");
            Files.createDirectories(trash);
            Files.move(source, trash.resolve(folder + "-" + id + "-" + UUID.randomUUID() + ".yml"));
          }
        });
  }

  private CompletableFuture<Void> submit(IoOperation operation) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    try {
      writer.execute(
          () -> {
            try {
              operation.run();
              future.complete(null);
            } catch (Exception ex) {
              logger.log(
                  Level.SEVERE, "Data could not be saved; check disk access and free space.", ex);
              future.completeExceptionally(ex);
            }
          });
    } catch (RejectedExecutionException ex) {
      throw new IllegalStateException(
          "Save queue is full or closed; this change was not persisted. Check server disk"
              + " throughput and retry.",
          ex);
    }
    return future;
  }

  public static void atomicWrite(Path file, String text) throws IOException {
    Files.createDirectories(file.getParent());
    Path temp = Files.createTempFile(file.getParent(), ".pending-", ".yml");
    try {
      Files.writeString(temp, text, StandardCharsets.UTF_8);
      try {
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException ex) {
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  @Override
  public void close() {
    writer.shutdown();
    try {
      if (!writer.awaitTermination(15, TimeUnit.SECONDS))
        logger.severe(
            "Storage still draining after 15 seconds; do not terminate Java until writes"
                + " finish.");
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      logger.warning("Interrupted while draining storage.");
    }
  }

  @FunctionalInterface
  private interface IoOperation {
    void run() throws IOException;
  }
}
