package com.unilink.common;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
/** D-138: writes an ApiError-shaped JSON body from a servlet filter, where
 *  GlobalExceptionHandler cannot run. The security chains used
 *  {@code res.sendError(401/403)}, which produces the container's HTML
 *  error page (or an empty body under MockMvc) — every other API error is
 *  ApiError JSON, so a client parsing errors had to special-case the
 *  security layer. Field set matches ApiError (timestamp, status, code,
 *  message, path, traceId); fieldErrors is omitted, as the handler does
 *  for non-validation errors. Values are JSON-escaped; the message is a
 *  fixed literal from the caller, never an exception detail. */
public final class ApiErrorWriter {
  private ApiErrorWriter(){}
  public static void write(HttpServletResponse res, int status, String code, String message, String path) throws IOException {
    res.setStatus(status);
    res.setContentType("application/json");
    res.setCharacterEncoding("UTF-8");
    String body = "{\"timestamp\":\"" + Instant.now() + "\",\"status\":" + status
      + ",\"code\":\"" + escape(code) + "\",\"message\":\"" + escape(message)
      + "\",\"path\":\"" + escape(path) + "\",\"traceId\":\"" + UUID.randomUUID() + "\"}";
    res.getWriter().write(body);
  }
  static String escape(String s){
    if (s==null) return "";
    StringBuilder out = new StringBuilder(s.length());
    for (int i=0; i<s.length(); i++){
      char c = s.charAt(i);
      switch (c){
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> { if (c < 0x20) out.append(String.format("\\u%04x", (int) c)); else out.append(c); }
      }
    }
    return out.toString();
  }
}
