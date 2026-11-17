package com.unilink.security;
import org.springframework.stereotype.Service;
import java.time.*; import java.util.*;
import java.util.concurrent.*;
/** In-process token-bucket-ish throttle (D-003: no Redis in v1.0). 5 failures -> 15 min lock.
 *
 *  <p>Atomicity (D-101): every mutation runs under a single lock, so a
 *  bucket update is never a lost update under concurrency, and
 *  {@link #tryAcquireAll} checks and consumes <i>all</i> of its keys in
 *  one critical section. The old code did two sequential
 *  {@code tryAcquire} calls in the OTP resend path, so a resend that
 *  passed the per-user cap but hit the per-IP cap still consumed one unit
 *  of the per-user quota without sending anything; the two
 *  {@code ConcurrentHashMap.compute} calls were each atomic but the pair
 *  was not. Reads ({@code isLocked}, {@code isCoolingDown}) stay
 *  lock-free: a slightly stale read is harmless because every decision
 *  is re-validated inside the lock by the acquire itself. */
@Service
public class LoginThrottle {
  private record Bucket(int failures, Instant lockedUntil, Instant lastFailure) {}
  private final ConcurrentHashMap<String,Bucket> buckets = new ConcurrentHashMap<>();
  /** Single monitor for all bucket mutations (see class Javadoc). */
  private final Object lock = new Object();
  private static final int MAX = 5; private static final Duration LOCK = Duration.ofMinutes(15);
  private static final Duration WINDOW = Duration.ofMinutes(15);
  public boolean isLocked(String key){
    Bucket b = buckets.get(key); return b!=null && b.lockedUntil()!=null && Instant.now().isBefore(b.lockedUntil());
  }
  public void recordFailure(String key){
    synchronized (lock){
      Instant now = Instant.now();
      Bucket v = buckets.get(key);
      // Already locked: fixed 15-minute lock, do not extend it.
      if (v!=null && v.lockedUntil()!=null && now.isBefore(v.lockedUntil())) return;
      // Fresh bucket: first failure, previous lock expired, or the 15-minute
      // window since the last failure elapsed. Otherwise accumulate.
      // (Previous code compared now against now.minus(WINDOW), which is
      // always true, so the count reset to 1 on every failure and the lock
      // could never engage.)
      boolean fresh = v==null
        || (v.lockedUntil()!=null && now.isAfter(v.lockedUntil()))
        || (v.lastFailure()!=null && now.isAfter(v.lastFailure().plus(WINDOW)));
      int f = fresh ? 1 : v.failures()+1;
      buckets.put(key, new Bucket(f, f>=MAX ? now.plus(LOCK) : null, now));
    }
  }
  public void recordSuccess(String key){ synchronized (lock){ buckets.remove(key); } }
  public void resetForTests(){ synchronized (lock){ buckets.clear(); } }

  /** Generic attempt limiter, same bucket pattern as the login throttle
   *  (FR-AUTH-04 / NFR-SEC-05, OTP resend): at most {@code maxAttempts}
   *  attempts within {@code window}; the attempt that exceeds the cap locks
   *  the key for {@code lockDuration}. Returns true when the attempt is
   *  allowed (and recorded), false when the key is locked or the cap is hit. */
  public boolean tryAcquire(String key, int maxAttempts, Duration window, Duration lockDuration){
    synchronized (lock){
      Instant now = Instant.now();
      Bucket v = buckets.get(key);
      if (v!=null && v.lockedUntil()!=null && now.isBefore(v.lockedUntil())) return false;
      boolean fresh = v==null
        || (v.lockedUntil()!=null && now.isAfter(v.lockedUntil()))
        || (v.lastFailure()!=null && now.isAfter(v.lastFailure().plus(window)));
      int f = fresh ? 1 : v.failures()+1;
      buckets.put(key, new Bucket(f, f>maxAttempts ? now.plus(lockDuration) : null, now));
      return f<=maxAttempts;
    }
  }

  /** Atomically acquires one attempt on every key in {@code caps} (D-101).
   *  The caps are evaluated in map order (pass a {@link LinkedHashMap}
   *  when the order matters). Either every key is consumed and the call
   *  returns true, or nothing is consumed — except that the first key
   *  whose cap is exceeded is locked for {@code lockDuration}, mirroring
   *  what a single-key {@link #tryAcquire} does on the exceeding attempt.
   *  When {@code cooldownKey} is non-null, the call also fails (consuming
   *  nothing) while that key was used within {@code cooldown}. */
  public boolean tryAcquireAll(Map<String,Integer> caps, Duration window, Duration lockDuration){
    return tryAcquireAll(caps, window, lockDuration, null, null);
  }
  public boolean tryAcquireAll(Map<String,Integer> caps, Duration window, Duration lockDuration,
                               String cooldownKey, Duration cooldown){
    synchronized (lock){
      Instant now = Instant.now();
      if (cooldownKey != null && cooldown != null){
        Bucket c = buckets.get(cooldownKey);
        if (c!=null && c.lastFailure()!=null && now.isBefore(c.lastFailure().plus(cooldown))) return false;
      }
      Map<String,Bucket> pending = new LinkedHashMap<>();
      for (Map.Entry<String,Integer> e : caps.entrySet()){
        String key = e.getKey(); int maxAttempts = e.getValue();
        Bucket v = buckets.get(key);
        if (v!=null && v.lockedUntil()!=null && now.isBefore(v.lockedUntil())) return false;
        boolean fresh = v==null
          || (v.lockedUntil()!=null && now.isAfter(v.lockedUntil()))
          || (v.lastFailure()!=null && now.isAfter(v.lastFailure().plus(window)));
        int f = fresh ? 1 : v.failures()+1;
        if (f > maxAttempts){
          buckets.put(key, new Bucket(f, now.plus(lockDuration), now));
          return false;
        }
        pending.put(key, new Bucket(f, null, now));
      }
      buckets.putAll(pending);
      return true;
    }
  }
  /** True when the key was used within the last {@code cooldown} (resend cooldown). */
  public boolean isCoolingDown(String key, Duration cooldown){
    Bucket b = buckets.get(key);
    return b!=null && b.lastFailure()!=null && Instant.now().isBefore(b.lastFailure().plus(cooldown));
  }
}
