package com.unilink.identity.domain;
import com.unilink.common.Exceptions;
import java.util.*;
/** State pattern (ARCH §8): legal account transitions, enforced everywhere. */
public enum AccountState {
  REGISTERED, PENDING_REVIEW, VERIFIED, REJECTED, SUSPENDED, BANNED, DELETED;
  private static final Map<AccountState, Set<AccountState>> ALLOWED = Map.of(
    REGISTERED, Set.of(PENDING_REVIEW),
    PENDING_REVIEW, Set.of(VERIFIED, REJECTED),
    REJECTED, Set.of(PENDING_REVIEW),
    VERIFIED, Set.of(SUSPENDED, BANNED, REGISTERED), // REGISTERED = verification revoked (D-015)
    SUSPENDED, Set.of(VERIFIED),
    BANNED, Set.of(),
    DELETED, Set.of());
  public boolean canTransitionTo(AccountState next){
    if (this==DELETED) return false;
    if (next==DELETED) return true;
    return ALLOWED.getOrDefault(this, Set.of()).contains(next);
  }
  public AccountState transitionTo(AccountState next){
    if (!canTransitionTo(next)) throw new Exceptions.Unprocessable("ILLEGAL_STATE_TRANSITION", "Cannot move account from "+this+" to "+next);
    return next;
  }
  public static AccountState of(String s){
    try { return valueOf(s); } catch (Exception e){ throw new Exceptions.BadRequest("INVALID_STATE","Unknown account state"); }
  }
}
