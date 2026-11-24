package com.unilink.integration.email;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/** Regression (2026-11-24): the log email provider used to print the full
 *  verification link and the plaintext OTP, which is how codes ended up in
 *  the production Render logs. It must never log a secret, whatever the
 *  caller passes it. */
class LoggingEmailProviderTest {

  @Test void neverLogsSecrets() {
    Logger logger = (Logger) LoggerFactory.getLogger(LoggingEmailProvider.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      LoggingEmailProvider provider = new LoggingEmailProvider();
      provider.sendOtp("student@example.com", "654321");
      provider.sendVerificationLink("student@example.com", "https://unilink-5x2.pages.dev/api/auth/verify-email?token=SECRETTOKEN123");
      provider.sendSimple("student@example.com", "Your code", "secret body 987654");
      String all = appender.list.stream()
        .map(e -> e.getFormattedMessage())
        .reduce("", (a, b) -> a + "\n" + b);
      assertFalse(all.contains("654321"), "OTP must not appear in logs");
      assertFalse(all.contains("SECRETTOKEN123"), "verification token must not appear in logs");
      assertFalse(all.contains("secret body 987654"), "email body must not appear in logs");
      assertFalse(all.contains("verify-email?token="), "verification link must not appear in logs");
      assertTrue(all.contains("student@example.com"), "recipient may still be logged");
    } finally {
      logger.detachAppender(appender);
    }
  }
}
