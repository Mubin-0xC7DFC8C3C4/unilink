package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="system_settings") @Getter @Setter @NoArgsConstructor
public class SystemSetting {
  @Id @Column(name="setting_key") private String settingKey;
  @Column(name="setting_value") private String settingValue;
  @Column(name="description") private String description;
  @PrePersist void onCreate(){}
}
