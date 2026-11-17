package com.unilink.security;
import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
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
}
