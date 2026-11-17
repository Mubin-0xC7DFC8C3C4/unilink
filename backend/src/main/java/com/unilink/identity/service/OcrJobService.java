package com.unilink.identity.service;
import com.unilink.identity.repo.VerificationRequestRepository;
import com.unilink.integration.ocr.OcrProvider; import com.unilink.storage.StorageGateway;
import org.slf4j.*; import org.springframework.retry.annotation.*; import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
/** Async OCR (NFR-FLT-03): failure never blocks the queue; result stored on the request. */
@Service
public class OcrJobService {
  private static final Logger log = LoggerFactory.getLogger(OcrJobService.class);
  private final OcrProvider ocr; private final StorageGateway storage; private final VerificationRequestRepository requests;
  public OcrJobService(OcrProvider ocr, StorageGateway storage, VerificationRequestRepository requests){
    this.ocr=ocr; this.storage=storage; this.requests=requests; }
  @Async("ocrExecutor")
  @Retryable(retryFor = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 5000, multiplier = 2))
  public void runOcr(Long requestId, String idCardKey, String fullName, String studentId){
    runOcr(requestId, idCardKey, fullName, studentId, null);
  }
  /** I-12: the university name is passed through so the scorer can compare
   *  it (FR-AUTH-06). */
  @Async("ocrExecutor")
  @Retryable(retryFor = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 5000, multiplier = 2))
  public void runOcr(Long requestId, String idCardKey, String fullName, String studentId, String universityName){
    try {
      byte[] bytes = storage.open(idCardKey).readAllBytes();
      OcrProvider.Result r = ocr.extract(bytes, idCardKey, fullName, studentId, universityName);
      update(requestId, r);
    } catch (Exception e){
      log.warn("OCR job failed for request {}", requestId, e);
      update(requestId, new OcrProvider.Result("", 0.0, "FAILED"));
    }
  }
  @Transactional
  void update(Long requestId, OcrProvider.Result r){
    requests.findById(requestId).ifPresent(req -> {
      req.setOcrText(r.text()); req.setOcrMatchScore(r.matchScore()); req.setOcrStatus(r.status());
      requests.save(req);
    });
  }
}
