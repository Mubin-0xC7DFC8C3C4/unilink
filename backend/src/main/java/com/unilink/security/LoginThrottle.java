package com.unilink.security;
import org.springframework.stereotype.Service;
import java.time.*; import java.util.concurrent.*;
/** In-process token-bucket-ish throttle (D-003: no Redis in v1.0). 5 failures -> 15 min lock. */
@Service
public class LoginThrottle {
  private record Bucket(int failures, Instant lockedUntil) {}
  private final ConcurrentHashMap<String,Bucket> buckets = new ConcurrentHashMap<>();
  private static final int MAX = 5; private static final Duration LOCK = Duration.ofMinutes(15);
  private static final Duration WINDOW = Duration.ofMinutes(15);
  public boolean isLocked(String key){
    Bucket b = buckets.get(key); return b!=null && b.lockedUntil()!=null && Instant.now().isBefore(b.lockedUntil());
  }
  public void recordFailure(String key){
    buckets.compute(key,(k,v)->{
      int f = (v==null||Instant.now().isAfter(v.lockedUntil()==null?Instant.now().minus(WINDOW):v.lockedUntil())) ? 1 : v.failures()+1;
      return new Bucket(f, f>=MAX ? Instant.now().plus(LOCK) : null);
    });
  }
  public void recordSuccess(String key){ buckets.remove(key); }
  public void resetForTests(){ buckets.clear(); }
}
