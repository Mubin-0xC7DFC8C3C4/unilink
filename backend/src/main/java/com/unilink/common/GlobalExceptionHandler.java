package com.unilink.common;
import jakarta.servlet.http.HttpServletRequest; import jakarta.validation.ConstraintViolationException;
import org.slf4j.*; import org.springframework.http.*; import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException; import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private String traceId(){ return Optional.ofNullable(org.slf4j.MDC.get("traceId")).orElse(UUID.randomUUID().toString()); }
  private ResponseEntity<ApiError> err(HttpStatus st, String code, String msg, HttpServletRequest req, List<ApiError.FieldError> fe){
    return ResponseEntity.status(st).body(ApiError.builder().status(st.value()).code(code).message(msg)
      .path(req.getRequestURI()).traceId(traceId()).fieldErrors(fe==null?List.of():fe).build());
  }
  @ExceptionHandler(Exceptions.AppException.class)
  public ResponseEntity<ApiError> app(Exceptions.AppException e, HttpServletRequest req){
    return err(e.status(), e.code(), e.getMessage(), req, null); }
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> validation(MethodArgumentNotValidException e, HttpServletRequest req){
    List<ApiError.FieldError> fe = e.getBindingResult().getFieldErrors().stream()
      .map(f -> new ApiError.FieldError(f.getField(), f.getDefaultMessage())).toList();
    return err(HttpStatus.UNPROCESSABLE_ENTITY,"VALIDATION_FAILED","Validation failed", req, fe); }
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiError> cv(ConstraintViolationException e, HttpServletRequest req){
    return err(HttpStatus.UNPROCESSABLE_ENTITY,"VALIDATION_FAILED","Validation failed", req, null); }
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiError> denied(AccessDeniedException e, HttpServletRequest req){
    return err(HttpStatus.FORBIDDEN,"FORBIDDEN","Access denied", req, null); }
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> other(Exception e, HttpServletRequest req){
    log.error("Unhandled error on {}", req.getRequestURI(), e);
    return err(HttpStatus.INTERNAL_SERVER_ERROR,"INTERNAL_ERROR","Something went wrong", req, null); }
}
