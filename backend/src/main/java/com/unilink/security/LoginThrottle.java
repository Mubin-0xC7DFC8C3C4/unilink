package com.unilink.security;
import org.springframework.stereotype.Service;
import java.time.*; import java.util.concurrent.*;
/** In-process token-bucket-ish throttle (D-003: no Redis in v1.0). 5 failures -> 15 min lock. */
@Service
public class LoginThrottle {
  private record Bucket(int failures, Instant lockedUntil, Instant lastFailure) {}
  private final ConcurrentHashMap<String,Bucket> buckets = new ConcurrentHashMap<>();
  private static final int MAX = 5; private static final Duration LOCK = Duration.ofMinutes(15);
  private static final Duration WINDOW = Duration.ofMinutes(15);
  public boolean isLocked(String key){
    Bucket b = buckets.get(key); return b!=null && b.lockedUntil()!=null && Instant.now().isBefore(b.lockedUntil());
  }
  public void recordFailure(String key){
    buckets.compute(key,(k,v)->{
      Instant now = Instant.now();
      // Already locked: fixed 15-minute lock, do not extend it.
      if (v!=null && v.lockedUntil()!=null && now.isBefore(v.lockedUntil())) return v;
      // Fresh bucket: first failure, previous lock expired, or the 15-minute
      // window since the last failure elapsed. Otherwise accumulate.
      // (Previous code compared now against now.minus(WINDOW), which is
      // always true, so the count reset to 1 on every failure and the lock
      // could never engage.)
      boolean fresh = v==null
        || (v.lockedUntil()!=null && now.isAfter(v.lockedUntil()))
        || (v.lastFailure()!=null && now.isAfter(v.lastFailure().plus(WINDOW)));
      int f = fresh ? 1 : v.failures()+1;
      return new Bucket(f, f>=MAX ? now.plus(LOCK) : null, now);
    });
  }
  public void recordSuccess(String key){ buckets.remove(key); }
  public void resetForTests(){ buckets.clear(); }

  /** Generic attempt limiter, same bucket pattern as the login throttle
   *  (FR-AUTH-04 / NFR-SEC-05, OTP resend): at most {@code maxAttempts}
   *  attempts within {@code window}; the attempt that exceeds the cap locks
   *  the key for {@code lockDuration}. Returns true when the attempt is
   *  allowed (and recorded), false when the key is locked or the cap is hit. */
  public boolean tryAcquire(String key, int maxAttempts, Duration window, Duration lockDuration){
    Instant now = Instant.now();
    Bucket out = buckets.compute(key,(k,v)->{
      if (v!=null && v.lockedUntil()!=null && now.isBefore(v.lockedUntil())) return v;
      boolean fresh = v==null
        || (v.lockedUntil()!=null && now.isAfter(v.lockedUntil()))
        || (v.lastFailure()!=null && now.isAfter(v.lastFailure().plus(window)));
      int f = fresh ? 1 : v.failures()+1;
      return new Bucket(f, f>maxAttempts ? now.plus(lockDuration) : null, now);
    });
    return out.lockedUntil()==null || now.isAfter(out.lockedUntil());
  }
  /** True when the key was used within the last {@code cooldown} (resend cooldown). */
  public boolean isCoolingDown(String key, Duration cooldown){
    Bucket b = buckets.get(key);
    return b!=null && b.lastFailure()!=null && Instant.now().isBefore(b.lastFailure().plus(cooldown));
  }
}
