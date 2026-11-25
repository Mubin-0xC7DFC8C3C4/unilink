package com.unilink.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.net.http.HttpClient;
import java.time.Duration;
/** D-203: the GitHub HTTP client as a bean (not constructed inside
 *  GithubService) so tests can substitute the transport without touching
 *  production behaviour — same 10 s connect timeout as before. */
@Configuration
public class GithubHttpConfig {
  @Bean
  public HttpClient githubHttpClient(){
    return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  }
}
