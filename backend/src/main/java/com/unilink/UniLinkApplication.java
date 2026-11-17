package com.unilink;
import com.unilink.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class UniLinkApplication {
  public static void main(String[] args){ SpringApplication.run(UniLinkApplication.class, args); }
}
