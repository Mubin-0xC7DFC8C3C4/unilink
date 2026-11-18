package com.unilink.common;
import jakarta.servlet.http.HttpServletRequest; import jakarta.validation.ConstraintViolationException;
import org.slf4j.*; import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*; import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException; import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpMediaTypeNotSupportedException; import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException; import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import java.util.*;
/** D-137: the catch-all {@code Exception} handler used to swallow Spring
 *  MVC's own client-error signals, so a malformed body, a missing required
 *  parameter, a wrong path-variable type, an unknown path or a wrong method
 *  all answered 500 INTERNAL_ERROR. Each signal now has its own handler
 *  with the status Spring itself would have used (400/404/405), in the
 *  ApiError shape. The generic handler stays last-resort only, for genuine
 *  server faults — it never leaks the exception message or stack trace. */
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
    // D-137: previously the violation details were dropped (fieldErrors
    // omitted), unlike the @Valid handler above which lists them.
    List<ApiError.FieldError> fe = e.getConstraintViolations().stream()
      .map(v -> new ApiError.FieldError(String.valueOf(v.getPropertyPath()), v.getMessage())).toList();
    return err(HttpStatus.UNPROCESSABLE_ENTITY,"VALIDATION_FAILED","Validation failed", req, fe); }
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiError> denied(AccessDeniedException e, HttpServletRequest req){
    return err(HttpStatus.FORBIDDEN,"FORBIDDEN","Access denied", req, null); }
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException e, HttpServletRequest req){
    return err(HttpStatus.PAYLOAD_TOO_LARGE,"FILE_TOO_LARGE",
      "Uploaded file exceeds the 10 MB limit", req, null); }
  /** Malformed JSON or a missing request body. The Jackson detail is never
   *  echoed — it can name internal types — so the message stays generic. */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException e, HttpServletRequest req){
    return err(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","Request body is missing or malformed", req, null); }
  @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
  public ResponseEntity<ApiError> missingParam(Exception e, HttpServletRequest req){
    String name = e instanceof MissingServletRequestParameterException m ? m.getParameterName()
      : e instanceof MissingServletRequestPartException m ? m.getRequestPartName() : "";
    List<ApiError.FieldError> fe = name.isBlank() ? List.of()
      : List.of(new ApiError.FieldError(name, "Parameter is required"));
    return err(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","A required request parameter is missing", req, fe); }
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest req){
    Class<?> type = e.getRequiredType();
    List<ApiError.FieldError> fe = List.of(new ApiError.FieldError(e.getName(),
      "Invalid value '" + e.getValue() + "'" + (type==null ? "" : " for " + type.getSimpleName())));
    return err(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","A request value has the wrong type", req, fe); }
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiError> noResource(NoResourceFoundException e, HttpServletRequest req){
    return err(HttpStatus.NOT_FOUND,"NOT_FOUND","Resource not found", req, null); }
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiError> wrongMethod(HttpRequestMethodNotSupportedException e, HttpServletRequest req){
    return err(HttpStatus.METHOD_NOT_ALLOWED,"METHOD_NOT_ALLOWED","Method not allowed for this resource", req, null); }
  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ApiError> wrongMediaType(HttpMediaTypeNotSupportedException e, HttpServletRequest req){
    return err(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"UNSUPPORTED_MEDIA_TYPE","Unsupported media type", req, null); }
  /** Null ids reaching a Spring Data call (findById(null)) and negative
   *  PageRequest sizes both surface as IllegalArgumentException — a client
   *  error (400), not a server fault. Logged at warn: a genuine programming
   *  error that throws IAE inside a service would also land here, so the
   *  log line is the backstop that keeps it visible. */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiError> illegalArg(IllegalArgumentException e, HttpServletRequest req){
    log.warn("Illegal argument on {}: {}", req.getRequestURI(), e.getMessage());
    return err(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","Invalid request", req, null); }
  /** Same cause as above, after Spring's persistence-exception translation:
   *  {@code findById(null)} is translated to InvalidDataAccessApiUsageException
   *  ("The given id must not be null") by the repository proxy, so the IAE
   *  handler never sees it. Only this API-misuse subclass is mapped — other
   *  DataAccessExceptions stay 500, as a genuine database fault should. */
  @ExceptionHandler(org.springframework.dao.InvalidDataAccessApiUsageException.class)
  public ResponseEntity<ApiError> invalidDataAccess(org.springframework.dao.InvalidDataAccessApiUsageException e, HttpServletRequest req){
    log.warn("Invalid data access on {}: {}", req.getRequestURI(), e.getMessage());
    return err(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","Invalid request", req, null); }
  /** Unique-key / not-null races that reach the database surface as
   *  DataIntegrityViolationException — a conflict (409), not a 500. The
   *  constraint detail is never echoed (it names internal columns). */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiError> integrity(DataIntegrityViolationException e, HttpServletRequest req){
    log.warn("Data integrity violation on {}: {}", req.getRequestURI(), e.getMessage());
    return err(HttpStatus.CONFLICT,"CONFLICT","The request conflicts with existing data", req, null); }
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> other(Exception e, HttpServletRequest req){
    log.error("Unhandled error on {}", req.getRequestURI(), e);
    return err(HttpStatus.INTERNAL_SERVER_ERROR,"INTERNAL_ERROR","Something went wrong", req, null); }
}
