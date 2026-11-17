package com.unilink.identity;
import com.unilink.common.Exceptions; import com.unilink.identity.domain.AccountState;
import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
/** UT-PAT-STATE: full transition table; illegal transitions throw. */
class AccountStateTest {
  @Test void happyPath(){
    assertEquals(AccountState.PENDING_REVIEW, AccountState.REGISTERED.transitionTo(AccountState.PENDING_REVIEW));
    assertEquals(AccountState.VERIFIED, AccountState.PENDING_REVIEW.transitionTo(AccountState.VERIFIED));
    assertEquals(AccountState.SUSPENDED, AccountState.VERIFIED.transitionTo(AccountState.SUSPENDED));
    assertEquals(AccountState.VERIFIED, AccountState.SUSPENDED.transitionTo(AccountState.VERIFIED));
    assertEquals(AccountState.REGISTERED, AccountState.VERIFIED.transitionTo(AccountState.REGISTERED)); // revoke
    assertEquals(AccountState.PENDING_REVIEW, AccountState.REJECTED.transitionTo(AccountState.PENDING_REVIEW));
  }
  @Test void illegalTransitionsThrow(){
    assertThrows(Exceptions.Unprocessable.class, () -> AccountState.REGISTERED.transitionTo(AccountState.VERIFIED));
    assertThrows(Exceptions.Unprocessable.class, () -> AccountState.VERIFIED.transitionTo(AccountState.PENDING_REVIEW));
    assertThrows(Exceptions.Unprocessable.class, () -> AccountState.BANNED.transitionTo(AccountState.VERIFIED));
    assertThrows(Exceptions.Unprocessable.class, () -> AccountState.SUSPENDED.transitionTo(AccountState.BANNED));
  }
  @Test void deletionAlwaysAllowedExceptFromDeleted(){
    assertTrue(AccountState.VERIFIED.canTransitionTo(AccountState.DELETED));
    assertFalse(AccountState.DELETED.canTransitionTo(AccountState.VERIFIED));
  }
}
