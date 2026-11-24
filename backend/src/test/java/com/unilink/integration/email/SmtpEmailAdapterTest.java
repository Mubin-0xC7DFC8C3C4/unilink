package com.unilink.integration.email;

import com.unilink.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** SMTP delivery regression tests (2026-11-24, real Gmail delivery):
 *  success, authentication failure, timeout, server unavailable, and the
 *  fail-fast startup validation that stops a half-configured SMTP from
 *  silently swallowing every send. Uses a mocked JavaMailSender — no real
 *  SMTP server, no credentials, no network. */
class SmtpEmailAdapterTest {

  private AppProperties props(String from){
    return new AppProperties(null, null, null, null,
      new AppProperties.Mail("smtp", from, "smtp.gmail.com"), null, "test-key", null);
  }

  private JavaMailSenderImpl senderImpl(String host, String username, String password){
    JavaMailSenderImpl impl = new JavaMailSenderImpl();
    impl.setHost(host);
    if (username != null) impl.setUsername(username);
    if (password != null) impl.setPassword(password);
    return impl;
  }

  @Test void sendsVerificationLinkWithCorrectEnvelope() {
    JavaMailSender sender = mock(JavaMailSender.class);
    SmtpEmailAdapter adapter = new SmtpEmailAdapter(sender, props("unilink@gmail.com"));
    adapter.sendVerificationLink("student@example.com", "https://unilink-5x2.pages.dev/api/auth/verify-email?token=abc");
    ArgumentCaptor<SimpleMailMessage> cap = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(sender).send(cap.capture());
    SimpleMailMessage m = cap.getValue();
    assertEquals("unilink@gmail.com", m.getFrom());
    assertArrayEquals(new String[]{"student@example.com"}, m.getTo());
    assertTrue(m.getSubject().contains("Verify"));
    assertTrue(m.getText().contains("https://unilink-5x2.pages.dev/api/auth/verify-email?token=abc"));
  }

  @Test void sendsOtpWithCorrectEnvelope() {
    JavaMailSender sender = mock(JavaMailSender.class);
    SmtpEmailAdapter adapter = new SmtpEmailAdapter(sender, props("unilink@gmail.com"));
    adapter.sendOtp("student@example.com", "123456");
    ArgumentCaptor<SimpleMailMessage> cap = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(sender).send(cap.capture());
    assertTrue(cap.getValue().getText().contains("123456"));
  }

  @Test void authenticationFailureBecomesProviderException() {
    JavaMailSender sender = mock(JavaMailSender.class);
    doThrow(new MailAuthenticationException("535-5.7.8 Username and Password not accepted"))
      .when(sender).send(any(SimpleMailMessage.class));
    SmtpEmailAdapter adapter = new SmtpEmailAdapter(sender, props("unilink@gmail.com"));
    EmailProvider.ProviderException ex = assertThrows(EmailProvider.ProviderException.class,
      () -> adapter.sendOtp("student@example.com", "123456"));
    assertTrue(ex.getMessage().contains("SMTP send failed"));
  }

  @Test void smtpTimeoutBecomesProviderException() {
    JavaMailSender sender = mock(JavaMailSender.class);
    doThrow(new MailSendException("Mail server connection failed", new SocketTimeoutException("Read timed out")))
      .when(sender).send(any(SimpleMailMessage.class));
    SmtpEmailAdapter adapter = new SmtpEmailAdapter(sender, props("unilink@gmail.com"));
    assertThrows(EmailProvider.ProviderException.class,
      () -> adapter.sendVerificationLink("student@example.com", "https://example.com/verify?token=x"));
  }

  @Test void smtpServerUnavailableBecomesProviderException() {
    JavaMailSender sender = mock(JavaMailSender.class);
    doThrow(new MailSendException("Mail server connection failed", new java.net.ConnectException("Connection refused")))
      .when(sender).send(any(SimpleMailMessage.class));
    SmtpEmailAdapter adapter = new SmtpEmailAdapter(sender, props("unilink@gmail.com"));
    assertThrows(EmailProvider.ProviderException.class,
      () -> adapter.sendSimple("student@example.com", "Subject", "Body"));
  }

  @Test void gmailConfigPassesValidation() {
    SmtpEmailAdapter adapter = new SmtpEmailAdapter(
      senderImpl("smtp.gmail.com", "unilink@gmail.com", "abcd efgh ijkl mnop"), props("unilink@gmail.com"));
    assertDoesNotThrow(adapter::validate);
  }

  @Test void missingPasswordForGmailFailsFast() {
    SmtpEmailAdapter adapter = new SmtpEmailAdapter(
      senderImpl("smtp.gmail.com", "unilink@gmail.com", null), props("unilink@gmail.com"));
    IllegalStateException ex = assertThrows(IllegalStateException.class, adapter::validate);
    assertTrue(ex.getMessage().contains("SMTP_PASSWORD"));
  }

  @Test void missingUsernameForGmailFailsFast() {
    SmtpEmailAdapter adapter = new SmtpEmailAdapter(
      senderImpl("smtp.gmail.com", null, "secret"), props("unilink@gmail.com"));
    assertThrows(IllegalStateException.class, adapter::validate);
  }

  @Test void missingFromFailsFast() {
    SmtpEmailAdapter adapter = new SmtpEmailAdapter(
      senderImpl("smtp.gmail.com", "unilink@gmail.com", "secret"), props(null));
    IllegalStateException ex = assertThrows(IllegalStateException.class, adapter::validate);
    assertTrue(ex.getMessage().contains("MAIL_FROM"));
  }

  @Test void localhostWithoutCredentialsIsAllowedForLocalCatchers() {
    SmtpEmailAdapter adapter = new SmtpEmailAdapter(
      senderImpl("localhost", null, null), props("noreply@unilink.dev"));
    assertDoesNotThrow(adapter::validate);
  }
}
