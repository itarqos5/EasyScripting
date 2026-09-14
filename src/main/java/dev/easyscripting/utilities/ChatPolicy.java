package dev.easyscripting.utilities;

public final class ChatPolicy {
  private ChatPolicy() {}

  public static boolean blocked(
      boolean chatBlocked,
      boolean operator,
      boolean recording,
      boolean allowRecordingChat,
      boolean recordingBypass) {
    return (chatBlocked && !operator) || (recording && !allowRecordingChat && !recordingBypass);
  }
}
