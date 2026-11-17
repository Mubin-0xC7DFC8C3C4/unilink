package com.unilink.common;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.time.Instant; import java.util.*;
/** Single error shape (Master Guide §8): timestamp,status,code,message,path,traceId,fieldErrors. */
@Getter @JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {
  private final Instant timestamp = Instant.now();
  private final int status; private final String code; private final String message;
  private final String path; private final String traceId;
  private final List<FieldError> fieldErrors;
  private ApiError(Builder b){ status=b.status; code=b.code; message=b.message; path=b.path; traceId=b.traceId; fieldErrors=b.fieldErrors; }
  public record FieldError(String field, String message) {}
  public static Builder builder(){ return new Builder(); }
  public static class Builder {
    private int status; private String code; private String message; private String path; private String traceId;
    private List<FieldError> fieldErrors = List.of();
    public Builder status(int s){status=s;return this;} public Builder code(String c){code=c;return this;}
    public Builder message(String m){message=m;return this;} public Builder path(String p){path=p;return this;}
    public Builder traceId(String t){traceId=t;return this;}
    public Builder fieldErrors(List<FieldError> f){fieldErrors=f;return this;}
    public ApiError build(){ return new ApiError(this); }
  }
}
