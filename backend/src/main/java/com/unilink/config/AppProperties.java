package com.unilink.config;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;
@Validated @ConfigurationProperties(prefix = "app")
public record AppProperties(
  String baseUrl,
  Storage storage,
  Ocr ocr,
  Sms sms,
  Mail mail,
  Security security,
  @NotBlank String encryptionKey
) {
  public record Storage(String provider, String localPath) {}
  public record Ocr(String provider, String dataPath, String languages) {}
  public record Sms(String provider, String apiUrl, String apiKey, String senderId) {}
  public record Mail(String from, String host) {}
  public record Security(Duration studentIdle, Duration adminIdle, int loginMaxAttempts, Duration loginLock, boolean csrfEnabled) {}
}
