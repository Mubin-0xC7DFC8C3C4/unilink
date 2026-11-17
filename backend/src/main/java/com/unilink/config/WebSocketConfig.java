package com.unilink.config;
import com.unilink.identity.repo.ChatRoomRepository; import com.unilink.identity.repo.RoomMemberRepository;
import com.unilink.security.Principals;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message; import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.*; import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.ChannelInterceptor; import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.*;
/** STOMP /ws authenticated by the HTTP session; every SUBSCRIBE/SEND is authorised (ARCH §6). */
@Configuration @EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
  private final RoomMemberRepository roomMembers; private final ChatRoomRepository rooms;
  public WebSocketConfig(RoomMemberRepository roomMembers, ChatRoomRepository rooms){ this.roomMembers=roomMembers; this.rooms=rooms; }
  @Override public void registerStompEndpoints(StompEndpointRegistry r){
    r.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
  }
  @Override public void configureMessageBroker(MessageBrokerRegistry r){
    r.enableSimpleBroker("/topic","/queue"); r.setApplicationDestinationPrefixes("/app"); r.setUserDestinationPrefix("/user");
  }
  @Override public void configureClientInboundChannel(ChannelRegistration reg){
    reg.interceptors(new ChannelInterceptor(){
      @Override public Message<?> preSend(Message<?> message, MessageChannel channel){
        StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (acc==null) return message;
        StompCommand cmd = acc.getCommand();
        if (StompCommand.SUBSCRIBE.equals(cmd) || StompCommand.SEND.equals(cmd)){
          String dest = acc.getDestination();
          if (dest!=null && dest.startsWith("/topic/room.")){
            String publicId = dest.substring("/topic/room.".length());
            Object principal = acc.getUser();
            if (!(principal instanceof Principals.UnilinkUserPrincipal p))
              throw new IllegalStateException("Not authenticated");
            var room = rooms.findByPublicId(publicId).orElseThrow(() -> new IllegalStateException("Room not found"));
            if (roomMembers.findByRoomIdAndUserId(room.getId(), p.userId()).isEmpty())
              throw new IllegalStateException("Not a member of this room");
          }
        }
        return message;
      }
    });
  }
}
