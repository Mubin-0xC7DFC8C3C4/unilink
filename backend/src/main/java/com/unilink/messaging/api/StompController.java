package com.unilink.messaging.api;
import com.unilink.messaging.MessagingService;
import com.unilink.security.AccessPolicy;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
/** STOMP: /app/rooms.{publicId}.send — authenticated by the HTTP session, membership-checked in the service. */
@Controller
public class StompController {
  private final MessagingService svc; private final AccessPolicy policy;
  StompController(MessagingService s, AccessPolicy p){ svc=s; policy=p; }
  public record StompSend(String body, String idempotencyKey) {}
  @MessageMapping("/rooms.{publicId}.send")
  public void send(@DestinationVariable String publicId, @Payload StompSend b, Authentication auth){
    var p = policy.requireStudent(auth.getPrincipal());
    svc.sendMessage(p, publicId, b.body(), b.idempotencyKey());
  }
}
