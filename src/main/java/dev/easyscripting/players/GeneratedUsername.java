package dev.easyscripting.players;

/** Rules for public-provider and local-fallback usernames, separate from manual display names. */
public final class GeneratedUsername {
  private GeneratedUsername() {}

  public static boolean valid(String value) {
    return value != null
        && value.matches("[A-Za-z0-9_]{5,16}")
        && value.matches(".*[A-Za-z].*")
        && value.matches(".*[0-9_].*");
  }
}
