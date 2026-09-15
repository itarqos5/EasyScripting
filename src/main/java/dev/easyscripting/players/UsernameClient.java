package dev.easyscripting.players;

import com.google.gson.JsonParser;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Two workers, bounded queue and response; requests contain no Minecraft player data. */
public final class UsernameClient implements AutoCloseable {
  private static final int MAX_NAMES = 256;
  private static final int MAX_NAME_BYTES = 262_144;
  private static final int MAX_SKIN_BYTES = 131_072;
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
                        URI.create(
                                "https://randomuser.me/api/1.4/?results="
                                    + MAX_NAMES
                                    + "&inc=login&noinfo")
                            .toURL()
                            .openConnection();
                connection.setConnectTimeout(timeoutMillis);
                connection.setReadTimeout(timeoutMillis);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "EasyScripting/0.1.8");
                if (connection.getResponseCode() != 200)
                  throw new IOException("Username provider unavailable.");
                try (InputStream input = connection.getInputStream()) {
                  byte[] bytes = input.readNBytes(MAX_NAME_BYTES + 1);
                  if (bytes.length > MAX_NAME_BYTES)
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

  /** Craftdex publishes a small rotating set of real Minecraft profiles suitable as skin owners. */
  public CompletableFuture<List<String>> requestSkinOwners(int timeoutMillis) {
    return CompletableFuture.supplyAsync(
            () -> {
              HttpURLConnection connection = null;
              try {
                connection =
                    (HttpURLConnection)
                        URI.create("https://craftdex.net/top.json").toURL().openConnection();
                connection.setConnectTimeout(timeoutMillis);
                connection.setReadTimeout(timeoutMillis);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "EasyScripting/0.1.8");
                if (connection.getResponseCode() != 200)
                  throw new IOException("Skin profile provider unavailable.");
                try (InputStream input = connection.getInputStream()) {
                  byte[] bytes = input.readNBytes(MAX_SKIN_BYTES + 1);
                  if (bytes.length > MAX_SKIN_BYTES)
                    throw new IOException("Skin profile response exceeded its size limit.");
                  return parseSkinOwners(new String(bytes, StandardCharsets.UTF_8));
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
    if (json.length() > MAX_NAME_BYTES)
      throw new IllegalArgumentException("Username response is too large.");
    var results = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("results");
    if (results == null || results.size() > MAX_NAMES)
      throw new IllegalArgumentException("Invalid username response.");
    Map<String, String> names = new LinkedHashMap<>();
    for (var result : results) {
      if (!result.isJsonObject()) continue;
      var loginValue = result.getAsJsonObject().get("login");
      if (loginValue == null || !loginValue.isJsonObject()) continue;
      var name = loginValue.getAsJsonObject().get("username");
      if (name != null
          && name.isJsonPrimitive()
          && name.getAsJsonPrimitive().isString()
          && GeneratedUsername.valid(name.getAsString()))
        names.putIfAbsent(name.getAsString().toLowerCase(Locale.ROOT), name.getAsString());
    }
    return List.copyOf(names.values());
  }

  public static List<String> parseSkinOwners(String json) {
    if (json.length() > MAX_SKIN_BYTES)
      throw new IllegalArgumentException("Skin profile response is too large.");
    var top = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("top");
    if (top == null) throw new IllegalArgumentException("Invalid skin profile response.");
    Map<String, String> names = new LinkedHashMap<>();
    var players = top.getAsJsonArray("players");
    if (players != null) {
      if (players.size() > 100) throw new IllegalArgumentException("Too many skin profiles.");
      for (var value : players)
        if (value.isJsonObject())
          collectSkinOwner(value.getAsJsonObject().get("username"), names);
    }
    var skins = top.getAsJsonArray("skins");
    if (skins != null) {
      if (skins.size() > 100) throw new IllegalArgumentException("Too many skin profiles.");
      for (var value : skins) {
        if (!value.isJsonObject()) continue;
        var firstValue = value.getAsJsonObject().get("first_player");
        if (firstValue != null && firstValue.isJsonObject())
          collectSkinOwner(firstValue.getAsJsonObject().get("username"), names);
      }
    }
    return List.copyOf(names.values());
  }

  private static void collectSkinOwner(
      com.google.gson.JsonElement value, Map<String, String> names) {
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return;
    String name = value.getAsString();
    if (name.matches("[A-Za-z0-9_]{3,16}"))
      names.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
  }

  public void close() {
    executor.shutdownNow();
  }
}
