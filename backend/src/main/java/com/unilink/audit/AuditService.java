package com.unilink.audit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper; import com.unilink.common.Hashing;
import com.unilink.identity.domain.AuditLog; import com.unilink.identity.repo.AuditLogRepository;
import org.slf4j.*; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime; import java.time.temporal.ChronoUnit;
import java.util.*;
/** Append-only audit with hash chain (D-037). Written in the same transaction as the action.
 *  D-204 (F): the row hash covers metadata and ip as well as the action
 *  fields. The hash input is built ONLY from values stored on the row, so a
 *  stored row can be re-verified after a database round-trip:
 *   - createdAt is set explicitly (truncated to milliseconds, the DATETIME(3)
 *     precision MySQL/TiDB stores) BEFORE hashing, never a fresh timestamp;
 *   - metadata is hashed in canonical form (object keys sorted, compact) so
 *     MySQL's JSON normalisation (spacing/key order) cannot break the check.
 *  Rows written BEFORE this change cannot be recomputed: their hash covered
 *  a LocalDateTime.now() string that was never persisted (nanosecond
 *  precision, lost on the DATETIME(3) round-trip). Those rows stay
 *  "legacy" — verified by prev-hash linkage only, exactly as before — and
 *  verifyChain() tells them apart from new rows by recompute: the first
 *  row whose hash matches the new formula marks the cutover, and every row
 *  from there on must recompute. A tampered new row therefore reports as
 *  BROKEN, never as legacy. Known limit: tampering ONLY the first
 *  post-change row(s) evades the recompute, because no intact new row
 *  remains to locate the cutover (linkage is still checked); in
 *  production the chain is long and the cutover is established by many
 *  intact new rows, so this needs the earliest new rows to be the
 *  tampered ones. Re-hashing old rows was rejected: the audit log
 *  is append-only (D-037) and rewriting history would destroy the evidence
 *  the chain exists to protect. */
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
    LocalDateTime createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    String rowHash = rowHash(prev, action, targetType, targetId, actorAdminId, reason, metaJson, ip, createdAt);
    AuditLog row = new AuditLog();
    row.setActorAdminId(actorAdminId); row.setAction(action); row.setTargetType(targetType); row.setTargetId(targetId);
    row.setReason(reason); row.setMetadata(metaJson); row.setIp(ip); row.setPrevHash(prev); row.setRowHash(rowHash);
    row.setCreatedAt(createdAt);
    AuditLog saved = repo.save(row);
    log.info("AUDIT action={} actor={} target={}:{}", action, actorAdminId, targetType, targetId);
    return saved;
  }
  /** D-204: the v2 hash input — every field is a stored column value. */
  static String rowHash(String prev, String action, String targetType, Long targetId, Long actorAdminId,
                        String reason, String metaJson, String ip, LocalDateTime createdAt){
    return Hashing.sha256(prev+"|"+action+"|"+targetType+"|"+targetId+"|"+actorAdminId+"|"+reason
      +"|"+canonicalMetadata(metaJson)+"|"+ip+"|"+createdAt);
  }
  /** Recompute a stored row's v2 hash from its stored columns (public so
   *  tests and tooling can check a single row without a full chain run). */
  public static String recompute(AuditLog row){
    return rowHash(row.getPrevHash(), row.getAction(), row.getTargetType(), row.getTargetId(),
      row.getActorAdminId(), row.getReason(), row.getMetadata(), row.getIp(), row.getCreatedAt());
  }
  /** Canonical metadata JSON: object keys sorted recursively, compact
   *  separators — the same string at write time and after a MySQL JSON
   *  round-trip. Unparseable metadata falls back to the raw string (both
   *  paths use this function, so the fallback is consistent). */
  static String canonicalMetadata(String metaJson){
    if (metaJson==null) return "";
    try { return canonical(new ObjectMapper().readTree(metaJson)); }
    catch (Exception e){ return metaJson; }
  }
  private static String canonical(JsonNode node){
    if (node.isObject()){
      List<String> names = new ArrayList<>(); node.fieldNames().forEachRemaining(names::add);
      Collections.sort(names);
      StringBuilder sb = new StringBuilder("{");
      for (int i=0;i<names.size();i++){
        if (i>0) sb.append(",");
        sb.append("\"").append(names.get(i).replace("\\","\\\\").replace("\"","\\\"")).append("\":");
        sb.append(canonical(node.get(names.get(i))));
      }
      return sb.append("}").toString();
    }
    if (node.isArray()){
      StringBuilder sb = new StringBuilder("[");
      for (int i=0;i<node.size();i++){ if (i>0) sb.append(","); sb.append(canonical(node.get(i))); }
      return sb.append("]").toString();
    }
    return node.toString();
  }
  public record VerifyResult(boolean ok, Long brokenRowId, long totalRows, long verifiedRows, long legacyRows) {}
  /** D-204: full chain verification. Returns the detailed result; the
   *  daily job uses verifyChain() (first broken row id, or null). */
  @Transactional(readOnly=true)
  public VerifyResult verifyDetailed(){
    List<AuditLog> rows = repo.findAll(org.springframework.data.domain.Sort.by("id"));
    // Pass 1: which rows recompute under the v2 formula? The first such
    // row marks the cutover — every row before it predates the change.
    Long firstV2 = null;
    Map<Long,Boolean> recomputes = new HashMap<>();
    for (AuditLog row : rows){
      boolean ok = row.getCreatedAt()!=null && recompute(row).equals(row.getRowHash());
      recomputes.put(row.getId(), ok);
      if (ok && firstV2==null) firstV2 = row.getId();
    }
    // Pass 2: linkage for every row; recompute for every row at/after cutover.
    String prev = "GENESIS";
    Long broken = null; long verified = 0; long legacy = 0;
    for (AuditLog row : rows){
      boolean v2Row = firstV2!=null && row.getId()>=firstV2;
      boolean rowOk = prev.equals(row.getPrevHash()) && (!v2Row || recomputes.get(row.getId()));
      if (!rowOk && broken==null) broken = row.getId();
      if (v2Row && rowOk) verified++;
      else if (!v2Row && rowOk) legacy++;
      prev = row.getRowHash();
    }
    return new VerifyResult(broken==null, broken, rows.size(), verified, legacy);
  }
  /** Daily verifier job helper: recompute the chain and return the first broken row id, or null. */
  @Transactional(readOnly=true)
  public Long verifyChain(){
    return verifyDetailed().brokenRowId();
  }
}
