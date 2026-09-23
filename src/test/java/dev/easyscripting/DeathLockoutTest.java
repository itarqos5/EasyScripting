package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.players.DeathLockout;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeathLockoutTest {
  private final UUID player = UUID.randomUUID(), other = UUID.randomUUID();

  @Test
  void aDeathKeepsThePlayerOutForTheWholeMinute() {
    var lockout = new DeathLockout();
    lockout.start(player, 1_000, 60_000);
    assertEquals(60, lockout.seconds(player, 1_000));
    assertEquals(1, lockout.seconds(player, 60_500), "a partial second still has to be waited out");
    assertEquals(60_000, lockout.remaining(player, 1_000));
    assertEquals(0, lockout.remaining(other, 1_000), "one player's death locks out nobody else");
  }

  @Test
  void theWaitEndsExactlyWhenItRunsOutAndIsForgotten() {
    var lockout = new DeathLockout();
    lockout.start(player, 0, 60_000);
    assertEquals(0, lockout.remaining(player, 60_000));
    assertEquals(0, lockout.size(), "an elapsed wait is dropped instead of being kept forever");
  }

  @Test
  void aSecondDeathExtendsAnActiveWaitAndNeverShortensIt() {
    var lockout = new DeathLockout();
    lockout.start(player, 0, 60_000);
    lockout.start(player, 10_000, 5_000);
    assertEquals(50_000, lockout.remaining(player, 10_000));
    lockout.start(player, 10_000, 60_000);
    assertEquals(60_000, lockout.remaining(player, 10_000));
  }

  @Test
  void aZeroSecondLockoutLetsThePlayerComeStraightBack() {
    var lockout = new DeathLockout();
    lockout.start(player, 0, 0);
    assertEquals(0, lockout.remaining(player, 0));
    lockout.start(player, 0, 60_000);
    lockout.clear(player);
    assertEquals(0, lockout.remaining(player, 0));
  }

  @Test
  void expiredEntriesOfOtherPlayersAreNotKeptOnEveryDeath() {
    var lockout = new DeathLockout();
    lockout.start(other, 0, 1_000);
    lockout.start(player, 5_000, 60_000);
    assertEquals(1, lockout.size());
  }
}
