package com.unilink.identity.service;
import com.unilink.identity.repo.UserRepository;
import com.unilink.integration.ocr.OcrProvider; import com.unilink.storage.StorageGateway;
import org.slf4j.*; import org.springframework.retry.annotation.*; import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
/** Async OCR (NFR-FLT-03): failure never blocks the queue; the decision
 *  is stored on the request. D-181/D-183: every path out of the job ends
 *  in a terminal ocrStatus (AUTO_VERIFIED / VERIFICATION_FAILED /
 *  MANUAL_REVIEW) — an engine failure becomes MANUAL_REVIEW, so no
 *  account is ever left stuck in VERIFYING. The email OTP is sent only
 *  after AUTO_VERIFIED, from its own transaction, so a mail outage can
 *  never roll the decision back — the frontend offers a resend instead.
 *  D-191: both sides of the card are OCR'd (front + back + the back's
 *  rotated validity strip) and the matcher combines them; the back is
 *  required evidence, so a missing or unreadable back can never
 *  auto-verify. */
@Service
public class OcrJobService {
  private static final Logger log = LoggerFactory.getLogger(OcrJobService.class);
  private final OcrProvider ocr; private final StorageGateway storage;
  private final VerificationDecisionService decisions; private final OtpService otp; private final UserRepository users;
  public OcrJobService(OcrProvider ocr, StorageGateway storage, VerificationDecisionService decisions,
      OtpService otp, UserRepository users){
    this.ocr=ocr; this.storage=storage; this.decisions=decisions; this.otp=otp; this.users=users; }
  /** Legacy single-side entry point: the back is absent, so the matcher
   *  sees an empty back side and the decision degrades to MANUAL_REVIEW.
   *  Kept for callers that only have a front image (none in production —
   *  registration and resubmit both pass two sides). */
  @Async("ocrExecutor")
  @Retryable(retryFor = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 5000, multiplier = 2))
  public void runOcr(Long requestId, String idCardKey, IdCardMatcher.Subject subject){
    runOcr(requestId, idCardKey, null, subject, null);
  }
  @Async("ocrExecutor")
  @Retryable(retryFor = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 5000, multiplier = 2))
  public void runOcr(Long requestId, String frontKey, String backKey, IdCardMatcher.Subject subject, String batch){
    try {
      byte[] frontBytes = storage.open(frontKey).readAllBytes();
      byte[] backBytes = backKey == null ? null : storage.open(backKey).readAllBytes();
      OcrProvider.TwoSidedResult r = ocr.extractTwoSided(frontBytes, backBytes, frontKey, backKey,
          subject.fullName(), subject.studentId(), subject.universityName());
      VerificationDecisionService.AppliedOutcome outcome = decisions.applyTwoSidedOcrOutcome(requestId, r, subject, batch);
      if (outcome.sendEmailOtp()){
        users.findById(outcome.userId()).ifPresent(user -> {
          if (!otp.sendEmailOtp(user))
            log.warn("Email OTP send failed for auto-verified user {}; the frontend will offer a resend", user.getId());
        });
      }
    } catch (Exception e){
      log.warn("OCR job failed for request {}", requestId, e);
      try { decisions.applyOcrFailure(requestId, subject); }
      catch (Exception inner){ log.warn("OCR failure handling failed for request {}", requestId, inner); }
    }
  }
}
