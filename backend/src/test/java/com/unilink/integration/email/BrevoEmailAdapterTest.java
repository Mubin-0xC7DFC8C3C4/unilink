package com.unilink.integration.email;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unilink.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Brevo HTTPS API delivery tests (D-189): success, unverified-sender
 *  and server errors, timeout, fail-fast startup validation, and the
 *  no-secrets-in-logs rule. The HttpClient is mocked — no Brevo account,
 *  no API key, no network. (A real local HTTP server cannot be used
 *  here: the sandbox intercepts Java TCP connections, the same reason
 *  Java cannot reach MySQL directly.) */
class BrevoEmailAdapterTest {

  private AppProperties props(String from, String apiKey){
    return new AppProperties(null, null, null, null,
      new AppProperties.Mail("brevo", from, null, apiKey), null, "test-key", null);
  }

  @SuppressWarnings("unchecked")
  private HttpClient clientReturning(int status, String body){
    try {
      HttpClient client = mock(HttpClient.class);
      HttpResponse<String> response = mock(HttpResponse.class);
      when(response.statusCode()).thenReturn(status);
      when(response.body()).thenReturn(body);
      when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
      return client;
    } catch (Exception e){ throw new RuntimeException(e); }
  }

  private BrevoEmailAdapter adapter(HttpClient client){
    return new BrevoEmailAdapter(props("unilink@gmail.com", "xkeysib-test-key"), client);
  }

  private static String readBody(HttpRequest req) throws Exception {
    var publisher = req.bodyPublisher().orElseThrow();
    var future = new CompletableFuture<String>();
    publisher.subscribe(new Flow.Subscriber<ByteBuffer>() {
      Flow.Subscription sub;
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      @Override public void onSubscribe(Flow.Subscription s){ sub = s; s.request(Long.MAX_VALUE); }
      @Override public void onNext(ByteBuffer b){ byte[] arr = new byte[b.remaining()]; b.get(arr); out.write(arr, 0, arr.length); }
      @Override public void onError(Throwable t){ future.completeExceptionally(t); }
      @Override public void onComplete(){ future.complete(out.toString(StandardCharsets.UTF_8)); }
    });
    return future.get(5, TimeUnit.SECONDS);
  }

  @SuppressWarnings("unchecked")
  private HttpRequest sentRequest(HttpClient client) throws Exception {
    ArgumentCaptor<HttpRequest> cap = ArgumentCaptor.forClass(HttpRequest.class);
    verify(client).send(cap.capture(), any(HttpResponse.BodyHandler.class));
    return cap.getValue();
  }

  private JsonNode bodyJson(HttpRequest req) throws Exception {
    return new ObjectMapper().readTree(readBody(req));
  }

  @Test void sendsOtpWithCorrectEnvelopeAndTrueExpiry() throws Exception {
    HttpClient client = clientReturning(201, "{\"messageId\":\"msg-123\"}");
    adapter(client).sendOtp("student@example.com", "123456", 10);
    HttpRequest req = sentRequest(client);
    assertEquals("POST", req.method());
    assertEquals("https://api.brevo.com/v3/smtp/email", req.uri().toString());
    assertEquals("xkeysib-test-key", req.headers().firstValue("api-key").orElse(""));
    assertEquals("application/json", req.headers().firstValue("content-type").orElse(""));
    assertEquals(Duration.ofSeconds(10), req.timeout().orElse(null));
    JsonNode body = bodyJson(req);
    assertEquals("unilink@gmail.com", body.path("sender").path("email").asText());
    assertEquals("UniLink", body.path("sender").path("name").asText());
    assertEquals("student@example.com", body.path("to").get(0).path("email").asText());
    assertTrue(body.path("subject").asText().contains("code"));
    assertTrue(body.path("textContent").asText().contains("123456"));
    assertTrue(body.path("textContent").asText().contains("expires in 10 minutes"));
  }

  @Test void sendsVerificationLinkWithCorrectEnvelope() throws Exception {
    HttpClient client = clientReturning(201, "{\"messageId\":\"msg-124\"}");
    adapter(client).sendVerificationLink("student@example.com", "https://unilink-5x2.pages.dev/api/auth/verify-email?token=abc");
    JsonNode body = bodyJson(sentRequest(client));
    assertEquals("student@example.com", body.path("to").get(0).path("email").asText());
    assertTrue(body.path("subject").asText().contains("Verify"));
    assertTrue(body.path("textContent").asText().contains("https://unilink-5x2.pages.dev/api/auth/verify-email?token=abc"));
  }

  @Test void unverifiedSender400BecomesProviderException() {
    HttpClient client = clientReturning(400, "{\"message\":\"Sender not verified\",\"code\":\"invalid_parameter\"}");
    EmailProvider.ProviderException ex = assertThrows(EmailProvider.ProviderException.class,
      () -> adapter(client).sendOtp("student@example.com", "123456", 10));
    assertTrue(ex.getMessage().contains("400"), ex.getMessage());
    assertTrue(ex.getMessage().contains("Sender not verified"), ex.getMessage());
    assertFalse(ex.getMessage().contains("123456"), "OTP must never appear in an exception message");
  }

  @Test void serverError500BecomesProviderException() {
    HttpClient client = clientReturning(500, "{\"message\":\"Internal error\"}");
    assertThrows(EmailProvider.ProviderException.class,
      () -> adapter(client).sendSimple("student@example.com", "Subject", "Body"));
  }

  @Test void timeoutBecomesProviderException() throws Exception {
    HttpClient client = mock(HttpClient.class);
    when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
      .thenThrow(new HttpTimeoutException("timed out"));
    assertThrows(EmailProvider.ProviderException.class,
      () -> adapter(client).sendOtp("student@example.com", "123456", 10));
  }

  @Test void neverLogsSecrets() {
    Logger logger = (Logger) LoggerFactory.getLogger(BrevoEmailAdapter.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      HttpClient client = clientReturning(201, "{\"messageId\":\"msg-123\"}");
      BrevoEmailAdapter adapter = adapter(client);
      adapter.sendOtp("student@example.com", "654321", 10);
      adapter.sendVerificationLink("student@example.com", "https://unilink-5x2.pages.dev/api/auth/verify-email?token=SECRETTOKEN123");
      String all = appender.list.stream()
        .map(e -> e.getFormattedMessage())
        .reduce("", (a, b) -> a + "\n" + b);
      assertFalse(all.contains("654321"), "OTP must not appear in logs");
      assertFalse(all.contains("SECRETTOKEN123"), "verification token must not appear in logs");
      assertFalse(all.contains("verify-email?token="), "verification link must not appear in logs");
      assertTrue(all.contains("student@example.com"), "recipient may still be logged");
      assertTrue(all.contains("msg-123"), "Brevo messageId may be logged for delivery tracing");
    } finally {
      logger.detachAppender(appender);
    }
  }

  @Test void missingApiKeyFailsFast() {
    BrevoEmailAdapter adapter = new BrevoEmailAdapter(props("unilink@gmail.com", null), HttpClient.newHttpClient());
    IllegalStateException ex = assertThrows(IllegalStateException.class, adapter::validate);
    assertTrue(ex.getMessage().contains("BREVO_API_KEY"), ex.getMessage());
    assertFalse(ex.getMessage().contains("xkeysib"), "no key value is ever in the message");
  }

  @Test void placeholderFromFailsFast() {
    BrevoEmailAdapter adapter = new BrevoEmailAdapter(props("noreply@unilink.dev", "xkeysib-test-key"), HttpClient.newHttpClient());
    IllegalStateException ex = assertThrows(IllegalStateException.class, adapter::validate);
    assertTrue(ex.getMessage().contains("MAIL_FROM"), ex.getMessage());
  }

  @Test void missingFromFailsFast() {
    BrevoEmailAdapter adapter = new BrevoEmailAdapter(props(null, "xkeysib-test-key"), HttpClient.newHttpClient());
    IllegalStateException ex = assertThrows(IllegalStateException.class, adapter::validate);
    assertTrue(ex.getMessage().contains("MAIL_FROM"), ex.getMessage());
  }

  @Test void validConfigPassesValidation() {
    BrevoEmailAdapter adapter = new BrevoEmailAdapter(props("unilink@gmail.com", "xkeysib-test-key"), HttpClient.newHttpClient());
    assertDoesNotThrow(adapter::validate);
  }
}
