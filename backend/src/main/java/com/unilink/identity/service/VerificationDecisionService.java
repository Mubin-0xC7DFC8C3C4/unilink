package com.unilink.identity.service;
import com.unilink.identity.domain.*;
import com.unilink.identity.repo.*;
import com.unilink.integration.ocr.OcrProvider;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
/** Applies an OCR outcome to the verification request and the account
 *  (D-176/D-180/D-183). One call, one transaction: request row, account
 *  state and the audit attempt row move together, so a crash cannot leave
 *  the request and the account disagreeing. AUTO_VERIFIED deliberately
 *  leaves the request status alone (registration: AWAITING_VERIFICATION,
 *  resubmit: PENDING_REVIEW — the admin queue stays a fallback for a
 *  resubmitted request until the email OTP completes and marks it
 *  APPROVED); the account itself does not change until the email OTP
 *  proves control of the inbox. */
@Service
public class VerificationDecisionService {
  private final VerificationRequestRepository requests; private final UserRepository users;
  private final VerificationAttemptRepository attempts; private final IdCardMatcher matcher;
  public VerificationDecisionService(VerificationRequestRepository requests, UserRepository users,
      VerificationAttemptRepository attempts, IdCardMatcher matcher){
    this.requests=requests; this.users=users; this.attempts=attempts; this.matcher=matcher;
  }
  public record AppliedOutcome(IdCardMatcher.Outcome outcome, Long userId, boolean sendEmailOtp) {}
  /** OCR engine failure (missing binary, timeout, unreadable image): the
   *  matcher treats empty text as "unclear image", so this is the same
   *  MANUAL_REVIEW path — an account is never left stuck in VERIFYING. */
  @Transactional
  public AppliedOutcome applyOcrFailure(Long requestId, IdCardMatcher.Subject subject){
    return applyOcrOutcome(requestId, new OcrProvider.Result("", 0.0, "FAILED", null), subject);
  }
  @Transactional
  public AppliedOutcome applyOcrOutcome(Long requestId, OcrProvider.Result result, IdCardMatcher.Subject subject){
    VerificationRequest req = requests.findById(requestId).orElseThrow(() -> new IllegalArgumentException("Unknown verification request " + requestId));
    IdCardMatcher.Decision decision = matcher.match(result.text(), result.confidence(), subject);
    req.setOcrText(result.text());
    req.setOcrMatchScore(decision.compositeScore());
    req.setOcrStatus(decision.outcome().name());
    return applyDecision(req, decision);
  }
  /** D-191: the two-sided path. The front and back OCR texts are both
   *  stored on the request (the admin queue exposes both), and the
   *  matcher combines them with the batch and validity evidence. The
   *  state machine is unchanged: AUTO_VERIFIED leaves the request alone
   *  until the email OTP completes, MANUAL_REVIEW moves it to
   *  PENDING_REVIEW, VERIFICATION_FAILED rejects it. */
  @Transactional
  public AppliedOutcome applyTwoSidedOcrOutcome(Long requestId, OcrProvider.TwoSidedResult result,
                                                IdCardMatcher.Subject subject, String batch){
    VerificationRequest req = requests.findById(requestId).orElseThrow(() -> new IllegalArgumentException("Unknown verification request " + requestId));
    IdCardMatcher.Decision decision = matcher.matchTwoSided(
        result.front().text(), result.front().confidence(),
        result.back().text(), result.back().confidence(),
        result.validity(), batch, subject);
    req.setOcrText(result.front().text());
    req.setOcrTextBack(result.back().text());
    req.setOcrMatchScore(decision.compositeScore());
    req.setOcrStatus(decision.outcome().name());
    return applyDecision(req, decision);
  }
  /** One call, one transaction: request row, account state and the audit
   *  attempt row move together (shared by the single-side and two-sided
   *  paths). */
  private AppliedOutcome applyDecision(VerificationRequest req, IdCardMatcher.Decision decision){
    User u = users.findById(req.getUserId()).orElseThrow(() -> new IllegalArgumentException("Unknown user " + req.getUserId()));
    VerificationAttempt att = new VerificationAttempt();
    att.setUserId(u.getId()); att.setVerificationRequestId(req.getId());
    switch (decision.outcome()){
      case AUTO_VERIFIED -> {
        att.setOutcome("AUTO_VERIFIED");
        requests.save(req); attempts.save(att);
        return new AppliedOutcome(decision.outcome(), u.getId(), true);
      }
      case MANUAL_REVIEW -> {
        req.setStatus("PENDING_REVIEW"); req.setReason(decision.reason());
        att.setOutcome("MANUAL_REVIEW"); att.setReason(decision.reason());
        if ("REGISTERED".equals(u.getState()))
          u.setState(AccountState.REGISTERED.transitionTo(AccountState.PENDING_REVIEW).name());
        users.save(u); requests.save(req); attempts.save(att);
        return new AppliedOutcome(decision.outcome(), u.getId(), false);
      }
      case VERIFICATION_FAILED -> {
        req.setStatus("REJECTED"); req.setReason(decision.reason());
        att.setOutcome("AUTO_FAILED"); att.setReason(decision.reason());
        // REGISTERED cannot go to REJECTED directly: two legal steps.
        AccountState cur = AccountState.of(u.getState());
        if (cur == AccountState.REGISTERED){
          u.setState(cur.transitionTo(AccountState.PENDING_REVIEW).name());
          cur = AccountState.PENDING_REVIEW;
        }
        if (cur == AccountState.PENDING_REVIEW)
          u.setState(cur.transitionTo(AccountState.REJECTED).name());
        users.save(u); requests.save(req); attempts.save(att);
        return new AppliedOutcome(decision.outcome(), u.getId(), false);
      }
    }
    throw new IllegalStateException("Unhandled OCR outcome " + decision.outcome());
  }
}
