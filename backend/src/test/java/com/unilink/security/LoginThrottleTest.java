package com.unilink.security;
import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
/** Login throttle: 5 failures within 15 minutes -> 15-minute lock.
 *  Regression test for the bug where recordFailure reset the count to 1 on
 *  every failure while unlocked, so the lock could never engage. */
class LoginThrottleTest {
  @Test void locksAfterFiveConsecutiveFailures(){
    LoginThrottle t = new LoginThrottle();
    for (int i=1;i<=4;i++){ t.recordFailure("user"); assertFalse(t.isLocked("user"), "must not be locked after "+i+" failures"); }
    t.recordFailure("user");
    assertTrue(t.isLocked("user"), "must be locked after 5 failures");
  }
  @Test void furtherFailuresWhileLockedKeepTheLock(){
    LoginThrottle t = new LoginThrottle();
    for (int i=0;i<5;i++) t.recordFailure("user");
    assertTrue(t.isLocked("user"));
    t.recordFailure("user"); t.recordFailure("user");
    assertTrue(t.isLocked("user"));
  }
  @Test void successClearsTheBucket(){
    LoginThrottle t = new LoginThrottle();
    for (int i=0;i<5;i++) t.recordFailure("user");
    assertTrue(t.isLocked("user"));
    t.recordSuccess("user");
    assertFalse(t.isLocked("user"));
    t.recordFailure("user");
    assertFalse(t.isLocked("user"), "count restarts after a success");
  }
  @Test void keysAreIndependent(){
    LoginThrottle t = new LoginThrottle();
    for (int i=0;i<5;i++) t.recordFailure("a");
    assertTrue(t.isLocked("a"));
    assertFalse(t.isLocked("b"));
  }
  /** Generic limiter used by OTP resend (I-5): 5 attempts in the window, then locked. */
  @Test void tryAcquireAllowsUpToCapThenLocks(){
    LoginThrottle t = new LoginThrottle();
    for (int i=1;i<=5;i++) assertTrue(t.tryAcquire("k", 5, Duration.ofHours(1), Duration.ofHours(1)), "attempt "+i+" must be allowed");
    assertFalse(t.tryAcquire("k", 5, Duration.ofHours(1), Duration.ofHours(1)), "6th attempt must be denied");
    assertTrue(t.isLocked("k"));
  }
  @Test void tryAcquireWindowResetsAfterExpiry() throws InterruptedException {
    LoginThrottle t = new LoginThrottle();
    assertTrue(t.tryAcquire("k", 1, Duration.ofMillis(50), Duration.ofMillis(50)));
    assertFalse(t.tryAcquire("k", 1, Duration.ofMillis(50), Duration.ofMillis(50)));
    Thread.sleep(150); // window (50 ms) and lock (50 ms) both elapsed
    assertTrue(t.tryAcquire("k", 1, Duration.ofMillis(50), Duration.ofMillis(50)), "cap must reset after the window");
  }
  @Test void coolingDown(){
    LoginThrottle t = new LoginThrottle();
    assertFalse(t.isCoolingDown("k", Duration.ofMinutes(1)), "unused key is not cooling down");
    t.recordFailure("k");
    assertTrue(t.isCoolingDown("k", Duration.ofMinutes(1)), "just-used key is cooling down");
    assertFalse(t.isCoolingDown("other", Duration.ofMinutes(1)), "other keys are unaffected");
    assertFalse(t.isCoolingDown("k", Duration.ZERO), "zero cooldown never blocks");
  }
}
