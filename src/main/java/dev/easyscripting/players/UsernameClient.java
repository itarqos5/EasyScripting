package dev.easyscripting.players;

import com.google.gson.JsonParser;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Two workers, bounded queue and response; requests contain no Minecraft player data. */
public final class UsernameClient implements AutoCloseable {
  private final ThreadPoolExecutor executor =
      new ThreadPoolExecutor(
          2,
          2,
          0,
          TimeUnit.SECONDS,
          new ArrayBlockingQueue<>(18),
          r -> {
            Thread t = new Thread(r, "EasyScripting-usernames");
            t.setDaemon(true);
            return t;
          },
          new ThreadPoolExecutor.AbortPolicy());

  public CompletableFuture<List<String>> request(int timeoutMillis) {
    return CompletableFuture.supplyAsync(
            () -> {
              HttpURLConnection connection = null;
              try {
                connection =
                    (HttpURLConnection)
                        URI.create("https://randomuser.me/api/1.4/?results=8&inc=login&noinfo")
                            .toURL()
                            .openConnection();
                connection.setConnectTimeout(timeoutMillis);
                connection.setReadTimeout(timeoutMillis);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "EasyScripting/0.1.5");
                if (connection.getResponseCode() != 200)
                  throw new IOException("Username provider unavailable.");
                try (InputStream input = connection.getInputStream()) {
                  byte[] bytes = input.readNBytes(16385);
                  if (bytes.length > 16384)
                    throw new IOException("Username response exceeded its size limit.");
                  return parse(new String(bytes, StandardCharsets.UTF_8));
                }
              } catch (IOException | RuntimeException error) {
                throw new CompletionException(error);
              } finally {
                if (connection != null) connection.disconnect();
              }
            },
            executor)
        .orTimeout(timeoutMillis * 2L + 1000, TimeUnit.MILLISECONDS);
  }

  public static List<String> parse(String json) {
    if (json.length() > 16384)
      throw new IllegalArgumentException("Username response is too large.");
    var results = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("results");
    if (results == null || results.size() > 8)
      throw new IllegalArgumentException("Invalid username response.");
    List<String> names = new ArrayList<>();
    for (var result : results) {
      var name = result.getAsJsonObject().getAsJsonObject("login").get("username");
      if (name != null
          && name.isJsonPrimitive()
          && name.getAsJsonPrimitive().isString()
          && name.getAsString().matches("[A-Za-z0-9_]{3,16}")) names.add(name.getAsString());
    }
    return names.stream().distinct().toList();
  }

  public void close() {
    executor.shutdownNow();
  }
}
