package com.unilink.integration;

import com.unilink.config.AppProperties;
import com.unilink.integration.email.EmailProvider;
import com.unilink.integration.email.LoggingEmailProvider;
import com.unilink.integration.email.SmtpEmailAdapter;
import com.unilink.integration.sms.GatewaySmsAdapter;
import com.unilink.integration.sms.LoggingSmsProvider;
import com.unilink.integration.sms.SmsProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Provider selection regression test.
 *
 *  Before this fix the SMS/email adapters were fenced by @Profile:
 *  logging adapters existed only in dev/default, so a prod deployment
 *  without a configured SMS gateway / SMTP server could not register a
 *  user — the gateway adapter threw "not configured", the email fallback
 *  threw "SMTP send failed", and POST /api/auth/register returned 500.
 *  Selection is now property-driven (app.sms.provider / app.mail.provider,
 *  log by default in every profile), matching the storage adapters. */
class ProviderSelectionTest {

  @Configuration
  @EnableConfigurationProperties(AppProperties.class)
  @Import({LoggingSmsProvider.class, GatewaySmsAdapter.class,
           LoggingEmailProvider.class, SmtpEmailAdapter.class})
  static class Providers {
    @Bean JavaMailSender javaMailSender() { return mock(JavaMailSender.class); }
  }

  // The bare runner has no application.yml, so the one @NotBlank
  // property (app.encryption-key) must be supplied for binding to pass.
  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withUserConfiguration(Providers.class)
      .withPropertyValues("app.encryption-key=test-key");

  @Test
  void defaultsToLogProvidersInAnyProfile() {
    runner.run(ctx -> {
      assertThat(ctx).hasSingleBean(SmsProvider.class);
      assertThat(ctx.getBean(SmsProvider.class)).isInstanceOf(LoggingSmsProvider.class);
      assertThat(ctx).hasSingleBean(EmailProvider.class);
      assertThat(ctx.getBean(EmailProvider.class)).isInstanceOf(LoggingEmailProvider.class);
    });
  }

  @Test
  void gatewaySmsSelectedOnlyWhenConfigured() {
    runner.withPropertyValues("app.sms.provider=gateway").run(ctx -> {
      assertThat(ctx).hasSingleBean(SmsProvider.class);
      assertThat(ctx.getBean(SmsProvider.class)).isInstanceOf(GatewaySmsAdapter.class);
    });
  }

  @Test
  void smtpEmailSelectedOnlyWhenConfigured() {
    runner.withPropertyValues("app.mail.provider=smtp").run(ctx -> {
      assertThat(ctx).hasSingleBean(EmailProvider.class);
      assertThat(ctx.getBean(EmailProvider.class)).isInstanceOf(SmtpEmailAdapter.class);
    });
  }
}
