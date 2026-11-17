package com.unilink.content;
import com.unilink.common.Exceptions; import org.springframework.stereotype.Component;
import java.util.List; import java.util.regex.Pattern;
/** Chain of Responsibility (ARCH §8): size, links, flood, banned terms — each handler alone, ordered chain. */
@Component
public class ContentValidator {
  public interface Handler { void check(String body, String title); }
  static class SizeHandler implements Handler {
    public void check(String body, String title){
      if (body==null || body.isBlank()) throw new Exceptions.BadRequest("EMPTY_CONTENT","Content cannot be empty");
      if (body.length()>10000) throw new Exceptions.BadRequest("CONTENT_TOO_LONG","Content exceeds 10,000 characters");
      if (title!=null && title.length()>255) throw new Exceptions.BadRequest("TITLE_TOO_LONG","Title exceeds 255 characters");
    }
  }
  static class LinkHandler implements Handler {
    private static final Pattern BAD = Pattern.compile("(?i)javascript:|data:text/html");
    public void check(String body, String title){
      if (body!=null && BAD.matcher(body).find()) throw new Exceptions.BadRequest("BAD_LINK","Links must use http(s)");
      if (title!=null && BAD.matcher(title).find()) throw new Exceptions.BadRequest("BAD_LINK","Links must use http(s)");
    }
  }
  static class BannedTermsHandler implements Handler {
    private static final List<String> BANNED = List.of("buy followers","crypto doubling");
    public void check(String body, String title){
      String b = (body==null?"":body).toLowerCase();
      for (String t : BANNED) if (b.contains(t)) throw new Exceptions.BadRequest("BANNED_CONTENT","Content contains disallowed text");
    }
  }
  private final List<Handler> chain = List.of(new SizeHandler(), new LinkHandler(), new BannedTermsHandler());
  public void validate(String body, String title){ chain.forEach(h -> h.check(body, title)); }
}
