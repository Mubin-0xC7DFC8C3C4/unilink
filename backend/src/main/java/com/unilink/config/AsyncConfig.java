package com.unilink.config;
import org.springframework.context.annotation.*; import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.*; import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;
@Configuration @EnableAsync @EnableScheduling @EnableRetry
public class AsyncConfig {
  @Bean("ocrExecutor") public Executor ocrExecutor(){
    ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
    ex.setCorePoolSize(2); ex.setMaxPoolSize(4); ex.setQueueCapacity(50); ex.setThreadNamePrefix("ocr-"); ex.initialize(); return ex;
  }
  @Bean("mailExecutor") public Executor mailExecutor(){
    ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
    ex.setCorePoolSize(2); ex.setMaxPoolSize(4); ex.setQueueCapacity(100); ex.setThreadNamePrefix("mail-"); ex.initialize(); return ex;
  }
}
