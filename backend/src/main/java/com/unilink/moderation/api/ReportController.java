package com.unilink.moderation.api;
import com.unilink.identity.domain.*; import com.unilink.moderation.ModerationService;
import com.unilink.security.AccessPolicy;
import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api")
public class ReportController {
  private final ModerationService mod; private final AccessPolicy policy;
  public ReportController(ModerationService m, AccessPolicy p){ mod=m; policy=p; }
  public record ReportBody(String targetType, Long targetId, String reason) {}
  public record AppealBody(String targetType, Long targetId, String reason) {}
  @PostMapping("/reports") public Report report(Authentication a, @RequestBody ReportBody r){
    var p = policy.requireStudent(a.getPrincipal());
    return mod.report(p, r.targetType(), r.targetId(), r.reason());
  }
  @PostMapping("/appeals") public Appeal appeal(Authentication a, @RequestBody AppealBody r){
    var p = policy.requireStudent(a.getPrincipal());
    return mod.appeal(p, r.targetType(), r.targetId(), r.reason());
  }
  /** D-019: suspended/banned users appeal without a session, via the emailed single-use link. */
  @PostMapping("/appeals/public") public Map<String,String> appealPublic(@RequestParam String token, @RequestBody AppealBody r){
    mod.appealByToken(token, r.targetType(), r.targetId(), r.reason());
    return Map.of("status","Appeal submitted");
  }
}
