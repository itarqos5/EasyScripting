package dev.easyscripting.integration;

import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.events.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

/** Voice callbacks operate entirely on the voice API, never on Bukkit world objects. */
public final class SimpleVoiceBridge implements VoiceBridge, VoicechatPlugin {
  private final BukkitVoicechatService service;
  private volatile boolean muted, closed;
  private final Set<UUID> broadcasters = ConcurrentHashMap.newKeySet();
  private final Set<UUID> connected = ConcurrentHashMap.newKeySet();

  public SimpleVoiceBridge(BukkitVoicechatService service) {
    this.service = service;
    service.registerPlugin(this);
  }

  public String getPluginId() {
    return "easyscripting";
  }

  public boolean available() {
    return !closed;
  }

  public boolean muted() {
    return muted;
  }

  public void mute(boolean value) {
    muted = value;
  }

  public void broadcast(Player speaker, boolean enabled) {
    if (enabled) broadcasters.add(speaker.getUniqueId());
    else broadcasters.remove(speaker.getUniqueId());
  }

  public void registerEvents(EventRegistration registration) {
    registration.registerEvent(
        PlayerConnectedEvent.class,
        event -> {
          if (!closed) connected.add(event.getConnection().getPlayer().getUuid());
        });
    registration.registerEvent(
        PlayerDisconnectedEvent.class,
        event -> {
          connected.remove(event.getPlayerUuid());
          broadcasters.remove(event.getPlayerUuid());
        });
    registration.registerEvent(
        MicrophonePacketEvent.class,
        event -> {
          if (closed) return;
          VoicechatConnection sender = event.getSenderConnection();
          if (sender == null) return;
          if (muted && !broadcasters.contains(sender.getPlayer().getUuid())) {
            event.cancel();
            return;
          }
          if (!broadcasters.contains(sender.getPlayer().getUuid())) return;
          event.cancel();
          VoicechatServerApi api = event.getVoicechat();
          var packet = event.getPacket().staticSoundPacketBuilder().build();
          for (UUID listener : connected) {
            if (listener.equals(sender.getPlayer().getUuid())) continue;
            VoicechatConnection connection = api.getConnectionOf(listener);
            if (connection != null) api.sendStaticSoundPacketTo(connection, packet);
          }
        });
  }

  // API 2.6 has no unregisterPlugin; callbacks become inert until server shutdown.
  public void close() {
    closed = true;
    muted = false;
    broadcasters.clear();
    connected.clear();
  }
}
