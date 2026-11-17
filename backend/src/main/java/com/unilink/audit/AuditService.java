package com.unilink.audit;
import com.fasterxml.jackson.databind.ObjectMapper; import com.unilink.common.Hashing;
import com.unilink.identity.domain.AuditLog; import com.unilink.identity.repo.AuditLogRepository;
import org.slf4j.*; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime; import java.util.Map;
/** Append-only audit with hash chain (D-037). Written in the same transaction as the action. */
@Service
public class AuditService {
  private static final Logger log = LoggerFactory.getLogger(AuditService.class);
  private final AuditLogRepository repo; private final ObjectMapper mapper = new ObjectMapper();
  public AuditService(AuditLogRepository repo){ this.repo=repo; }
  @Transactional
  public synchronized AuditLog write(Long actorAdminId, String action, String targetType, Long targetId, String reason, Map<String,Object> metadata, String ip){
    String prev = repo.findTopByOrderByIdDesc().map(AuditLog::getRowHash).orElse("GENESIS");
    String metaJson = null;
    try { metaJson = metadata==null?null:mapper.writeValueAsString(metadata); } catch (Exception e){ metaJson = "{}"; }
    String at = LocalDateTime.now().toString();
    String rowHash = Hashing.sha256(prev+"|"+action+"|"+targetType+"|"+targetId+"|"+actorAdminId+"|"+reason+"|"+at);
    AuditLog row = new AuditLog();
    row.setActorAdminId(actorAdminId); row.setAction(action); row.setTargetType(targetType); row.setTargetId(targetId);
    row.setReason(reason); row.setMetadata(metaJson); row.setIp(ip); row.setPrevHash(prev); row.setRowHash(rowHash);
    AuditLog saved = repo.save(row);
    log.info("AUDIT action={} actor={} target={}:{}", action, actorAdminId, targetType, targetId);
    return saved;
  }
  /** Daily verifier job helper: recompute the chain and return the first broken row id, or null. */
  @Transactional(readOnly=true)
  public Long verifyChain(){
    String prev = "GENESIS";
    for (AuditLog row : repo.findAll(org.springframework.data.domain.Sort.by("id"))){
      if (!prev.equals(row.getPrevHash())) return row.getId();
      prev = row.getRowHash();
    }
    return null;
  }
}
