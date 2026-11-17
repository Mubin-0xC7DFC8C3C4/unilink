package com.unilink.common;
import org.springframework.http.HttpStatus;
public class Exceptions {
  public static class AppException extends RuntimeException {
    private final HttpStatus status; private final String code;
    public AppException(HttpStatus s, String code, String msg){ super(msg); status=s; this.code=code; }
    public HttpStatus status(){ return status; } public String code(){ return code; }
  }
  public static class NotFound extends AppException { public NotFound(String what){ super(HttpStatus.NOT_FOUND,"NOT_FOUND", what+" not found"); } }
  public static class BadRequest extends AppException { public BadRequest(String code,String msg){ super(HttpStatus.BAD_REQUEST,code,msg); } }
  public static class Forbidden extends AppException { public Forbidden(String msg){ super(HttpStatus.FORBIDDEN,"FORBIDDEN",msg); } }
  public static class Conflict extends AppException { public Conflict(String code,String msg){ super(HttpStatus.CONFLICT,code,msg); } }
  public static class TooManyRequests extends AppException { public TooManyRequests(String msg){ super(HttpStatus.TOO_MANY_REQUESTS,"RATE_LIMITED",msg); } }
  public static class Unprocessable extends AppException { public Unprocessable(String code,String msg){ super(HttpStatus.UNPROCESSABLE_ENTITY,code,msg); } }
}
