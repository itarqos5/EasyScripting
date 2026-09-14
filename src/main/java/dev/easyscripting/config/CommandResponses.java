package dev.easyscripting.config;

import java.util.ArrayDeque;
import java.util.Deque;
import org.bukkit.command.CommandSender;

/** Scoped per-thread tracking also covers commands dispatched from GUI commands. */
public final class CommandResponses {
  private final ThreadLocal<Deque<Scope>> active = ThreadLocal.withInitial(ArrayDeque::new);

  public Scope begin(CommandSender sender) {
    Scope scope = new Scope(sender);
    active.get().push(scope);
    return scope;
  }

  public void sent(CommandSender sender) {
    Deque<Scope> scopes = active.get();
    for (Scope scope : scopes) if (scope.sender.equals(sender)) scope.responded = true;
    if (scopes.isEmpty()) active.remove();
  }

  public final class Scope implements AutoCloseable {
    private final CommandSender sender;
    private boolean responded;

    private Scope(CommandSender sender) {
      this.sender = sender;
    }

    public boolean responded() {
      return responded;
    }

    public void close() {
      Deque<Scope> scopes = active.get();
      scopes.remove(this);
      if (scopes.isEmpty()) active.remove();
    }
  }
}
