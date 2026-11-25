package com.unilink.integration.email;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.TestPropertySource;
import com.unilink.integration.TestProviders;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D-171 (2026-11-25): the SMTP timeouts in application.yml were spelled in
 * camelCase (connectionTimeout / writeTimeout). JavaMail property names are
 * lowercase, so those entries were silently ignored — the production log
 * showed "timeout -1" and a registration hung 120 s+ against
 * smtp.gmail.com:587. This test boots the real mail sender from
 * application.yml and asserts the exact lowercase keys JavaMail reads.
 */
@SpringBootTest
@Import(TestProviders.class)
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:unilink-mail-timeouts;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.flyway.enabled=false",
  "spring.jpa.hibernate.ddl-auto=create-drop",
  "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
  "app.storage.local-path=./target/test-storage",
  "app.encryption-key=test-mail-timeouts-key"
})
class MailTimeoutPropertiesTest {

  @Autowired JavaMailSender sender;

  @Test void smtpTimeoutsAreAppliedWithLowercaseKeys() {
    assertInstanceOf(JavaMailSenderImpl.class, sender);
    Properties props = ((JavaMailSenderImpl) sender).getJavaMailProperties();
    assertEquals("10000", props.getProperty("mail.smtp.connectiontimeout"),
      "mail.smtp.connectiontimeout must be set (lowercase — camelCase is ignored by JavaMail, D-171)");
    assertEquals("10000", props.getProperty("mail.smtp.timeout"));
    assertEquals("10000", props.getProperty("mail.smtp.writetimeout"),
      "mail.smtp.writetimeout must be set (lowercase — camelCase is ignored by JavaMail, D-171)");
    assertNull(props.getProperty("mail.smtp.connectionTimeout"),
      "camelCase connectionTimeout must not be present");
    assertNull(props.getProperty("mail.smtp.writeTimeout"),
      "camelCase writeTimeout must not be present");
  }
}
