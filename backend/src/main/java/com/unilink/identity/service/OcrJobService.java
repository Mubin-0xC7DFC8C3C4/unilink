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
 *  never roll the decision back — the frontend offers a resend instead. */
@Service
public class OcrJobService {
  private static final Logger log = LoggerFactory.getLogger(OcrJobService.class);
  private final OcrProvider ocr; private final StorageGateway storage;
  private final VerificationDecisionService decisions; private final OtpService otp; private final UserRepository users;
  public OcrJobService(OcrProvider ocr, StorageGateway storage, VerificationDecisionService decisions,
      OtpService otp, UserRepository users){
    this.ocr=ocr; this.storage=storage; this.decisions=decisions; this.otp=otp; this.users=users; }
  @Async("ocrExecutor")
  @Retryable(retryFor = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 5000, multiplier = 2))
  public void runOcr(Long requestId, String idCardKey, IdCardMatcher.Subject subject){
    try {
      byte[] bytes = storage.open(idCardKey).readAllBytes();
      OcrProvider.Result r = ocr.extract(bytes, idCardKey, subject.fullName(), subject.studentId(), subject.universityName());
      VerificationDecisionService.AppliedOutcome outcome = decisions.applyOcrOutcome(requestId, r, subject);
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
