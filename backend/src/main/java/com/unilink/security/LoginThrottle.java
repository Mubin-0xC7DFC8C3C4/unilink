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
}
