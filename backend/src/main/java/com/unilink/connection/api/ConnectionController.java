package com.unilink.connection.api;
import com.unilink.connection.ConnectionDtos; import com.unilink.connection.ConnectionService;
import com.unilink.security.AccessPolicy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/connections")
public class ConnectionController {
  private final ConnectionService service; private final AccessPolicy policy;
  public ConnectionController(ConnectionService s, AccessPolicy p){ service=s; policy=p; }
  public record ConnectionRequestBody(Long addresseeId, String message) {}
  @PostMapping("/requests")
  public ResponseEntity<ConnectionDtos.ConnectionRequestDto> request(Authentication a, @RequestBody ConnectionRequestBody r){
    var p = policy.requireStudent(a.getPrincipal());
    return ResponseEntity.status(201).body(service.requestConnection(p, r.addresseeId(), r.message()));
  }
  @GetMapping("/requests/incoming")
  public List<ConnectionDtos.ConnectionRequestDto> incoming(Authentication a){
    var p = policy.requireStudent(a.getPrincipal()); return service.incoming(p);
  }
  @GetMapping("/requests/outgoing")
  public List<ConnectionDtos.ConnectionRequestDto> outgoing(Authentication a){
    var p = policy.requireStudent(a.getPrincipal()); return service.outgoing(p);
  }
  @GetMapping public List<ConnectionDtos.ConnectionDto> connections(Authentication a){
    var p = policy.requireStudent(a.getPrincipal()); return service.connections(p);
  }
  @GetMapping("/status/{userId}")
  public ConnectionDtos.ConnectionStatusDto status(Authentication a, @PathVariable Long userId){
    var p = policy.requireStudent(a.getPrincipal()); return service.status(p, userId);
  }
  @PostMapping("/requests/{id}/accept")
  public ConnectionDtos.ConnectionRequestDto accept(Authentication a, @PathVariable Long id){
    var p = policy.requireStudent(a.getPrincipal()); return service.decide(p, id, true);
  }
  @PostMapping("/requests/{id}/reject")
  public ConnectionDtos.ConnectionRequestDto reject(Authentication a, @PathVariable Long id){
    var p = policy.requireStudent(a.getPrincipal()); return service.decide(p, id, false);
  }
  @PostMapping("/requests/{id}/cancel")
  public ConnectionDtos.ConnectionRequestDto cancel(Authentication a, @PathVariable Long id){
    var p = policy.requireStudent(a.getPrincipal()); return service.cancel(p, id);
  }
  @DeleteMapping("/{userId}")
  public Map<String,String> remove(Authentication a, @PathVariable Long userId){
    var p = policy.requireStudent(a.getPrincipal()); service.remove(p, userId); return Map.of("status","removed");
  }
}
