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

  // ---- D-101: atomic multi-key acquire ----

  @Test void tryAcquireAllConsumesAllKeysOrNone(){
    LoginThrottle t = new LoginThrottle();
    var caps = new java.util.LinkedHashMap<String,Integer>();
    caps.put("a", 1); caps.put("b", 100);
    assertTrue(t.tryAcquireAll(caps, Duration.ofHours(1), Duration.ofHours(1)), "first acquire must succeed");
    assertFalse(t.tryAcquireAll(caps, Duration.ofHours(1), Duration.ofHours(1)), "second acquire must fail on key a");
    assertTrue(t.isLocked("a"), "the exceeded key is locked, as with single-key tryAcquire");
    // Key b must have exactly 1 unit consumed (the failed call leaked nothing):
    // 99 more single acquires bring it to its cap of 100, the 100th fails.
    for (int i = 0; i < 99; i++)
      assertTrue(t.tryAcquire("b", 100, Duration.ofHours(1), Duration.ofHours(1)), "b must have room for 99 more");
    assertFalse(t.tryAcquire("b", 100, Duration.ofHours(1), Duration.ofHours(1)), "b must be exactly at its cap");
  }
  @Test void tryAcquireAllCooldownConsumesNothing(){
    LoginThrottle t = new LoginThrottle();
    var caps = new java.util.LinkedHashMap<String,Integer>();
    caps.put("a", 5); caps.put("b", 5);
    assertTrue(t.tryAcquireAll(caps, Duration.ofHours(1), Duration.ofHours(1), "a", Duration.ofHours(1)));
    assertFalse(t.tryAcquireAll(caps, Duration.ofHours(1), Duration.ofHours(1), "a", Duration.ofHours(1)),
      "second call inside the cooldown must fail");
    // Key b was not consumed by the failed call: 4 more acquires fit, the 5th fails.
    for (int i = 0; i < 4; i++)
      assertTrue(t.tryAcquire("b", 5, Duration.ofHours(1), Duration.ofHours(1)), "b must have room for 4 more");
    assertFalse(t.tryAcquire("b", 5, Duration.ofHours(1), Duration.ofHours(1)), "b must be exactly at its cap");
  }

  // ---- Concurrency: no lost updates, no cap bypass ----

  private static void runParallel(int threads, Runnable work) throws InterruptedException {
    var ready = new java.util.concurrent.CountDownLatch(threads);
    var go = new java.util.concurrent.CountDownLatch(1);
    var done = new java.util.concurrent.CountDownLatch(threads);
    var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
    try {
      for (int i = 0; i < threads; i++) pool.submit(() -> {
        ready.countDown();
        try { go.await(); } catch (InterruptedException e){ Thread.currentThread().interrupt(); return; }
        try { work.run(); } finally { done.countDown(); }
      });
      assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS), "threads must all be ready");
      go.countDown();
      assertTrue(done.await(10, java.util.concurrent.TimeUnit.SECONDS), "threads must all finish");
    } finally { pool.shutdownNow(); }
  }
  @Test void concurrentFailuresStillLockAfterFive() throws InterruptedException {
    LoginThrottle t = new LoginThrottle();
    runParallel(20, () -> t.recordFailure("user"));
    assertTrue(t.isLocked("user"),
      "20 parallel failures must still engage the lock — no update may be lost");
    t.recordSuccess("user");
    assertFalse(t.isLocked("user"));
    t.recordFailure("user");
    assertFalse(t.isLocked("user"), "count restarts at 1 after a success");
  }
  @Test void concurrentTryAcquireNeverExceedsCap() throws InterruptedException {
    LoginThrottle t = new LoginThrottle();
    var allowed = new java.util.concurrent.atomic.AtomicInteger();
    runParallel(20, () -> { if (t.tryAcquire("k", 5, Duration.ofHours(1), Duration.ofHours(1))) allowed.incrementAndGet(); });
    assertEquals(5, allowed.get(), "exactly 5 of 20 parallel attempts must be allowed");
    assertTrue(t.isLocked("k"));
  }
  @Test void concurrentTryAcquireAllIsAtomic() throws InterruptedException {
    LoginThrottle t = new LoginThrottle();
    var caps = new java.util.LinkedHashMap<String,Integer>();
    caps.put("a", 5); caps.put("b", 5);
    var allowed = new java.util.concurrent.atomic.AtomicInteger();
    runParallel(20, () -> { if (t.tryAcquireAll(caps, Duration.ofHours(1), Duration.ofHours(1))) allowed.incrementAndGet(); });
    assertEquals(5, allowed.get(), "exactly 5 of 20 parallel multi-key attempts must be allowed");
    // All-or-nothing: 5 successes consumed exactly 5 on each key, so the
    // next acquire on either key exceeds its cap (and a failed multi-key
    // call must not have consumed the other key).
    assertFalse(t.tryAcquire("a", 5, Duration.ofHours(1), Duration.ofHours(1)), "a must be exactly at its cap");
    assertFalse(t.tryAcquire("b", 5, Duration.ofHours(1), Duration.ofHours(1)), "b must be exactly at its cap");
  }
}
