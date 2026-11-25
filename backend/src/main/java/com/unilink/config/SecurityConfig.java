package com.unilink.config;
import com.unilink.common.ApiErrorWriter;
import com.unilink.identity.repo.*; import com.unilink.security.MustChangePasswordFilter; import com.unilink.security.Principals;
import jakarta.servlet.*; import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*; import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.*; import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*; import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*; import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException; import java.time.*;
/**
 * Two filter chains (ARCH §6): @Order(1) admin /api/admin/** requiring MFA_VERIFIED
 * AdminPrincipal; @Order(2) student /api/** with UnilinkUserPrincipal. A student principal
 * can never satisfy the admin chain (different type, different table, D-030).
 */
@Configuration @EnableWebSecurity
public class SecurityConfig {
  @Value("${app.security.csrf-enabled:true}") private boolean csrfEnabled;

  @Bean public PasswordEncoder passwordEncoder(){
    // Argon2id, OWASP params: 19 MiB, 2 iterations, 1 lane (D-014)
    return new Argon2PasswordEncoder(16, 32, 1, 19*1024, 2);
  }
  @Bean("studentUserDetailsService") public UserDetailsService studentUserDetailsService(UserRepository users, UserCredentialRepository creds, DepartmentRepository depts){
    return username -> {
      var user = users.findByEmail(username).orElseThrow(() -> new UsernameNotFoundException(username));
      var cred = creds.findById(user.getId()).orElseThrow(() -> new UsernameNotFoundException(username));
      // D-193: departmentId is nullable now (free-text department) —
      // Spring Data findById(null) throws, so guard it. A null department
      // simply means no catalog id on the principal.
      Long catalogId = user.getDepartmentId() == null ? null : depts.findById(user.getDepartmentId()).map(d->d.getCatalogId()).orElse(null);
      return new Principals.UnilinkUserPrincipal(user.getId(), user.getEmail(), cred.getPasswordHash(),
        user.getUniversityId(), user.getDepartmentId(), catalogId, user.getState(), true);
    };
  }
  @Bean("adminUserDetailsService") public UserDetailsService adminUserDetailsService(AdminAccountRepository admins){
    return username -> {
      var a = admins.findByEmail(username).orElseThrow(() -> new UsernameNotFoundException(username));
      return new Principals.AdminPrincipal(a.getId(), a.getEmail(), a.getPasswordHash(), a.getRole(), a.getUniversityId(), false, Boolean.TRUE.equals(a.getActive()));
    };
  }
  @Bean("studentAuthProvider") public DaoAuthenticationProvider studentAuthProvider(@Qualifier("studentUserDetailsService") UserDetailsService uds, PasswordEncoder enc){
    DaoAuthenticationProvider p = new DaoAuthenticationProvider(); p.setUserDetailsService(uds); p.setPasswordEncoder(enc); return p;
  }
  @Bean("adminAuthProvider") public DaoAuthenticationProvider adminAuthProvider(@Qualifier("adminUserDetailsService") UserDetailsService uds, PasswordEncoder enc){
    DaoAuthenticationProvider p = new DaoAuthenticationProvider(); p.setUserDetailsService(uds); p.setPasswordEncoder(enc); return p;
  }
  // @Primary: HttpSecurityConfiguration autowires a single AuthenticationManager
  // by type (as the parent manager for the filter chains) and fails the whole
  // context when two exist with none primary. The student manager is the
  // default; the admin chain and AdminAuthService use adminAuthManager via
  // @Qualifier, and each chain sets its own provider explicitly.
  @Bean("studentAuthManager") @Primary public AuthenticationManager studentAuthManager(@Qualifier("studentAuthProvider") DaoAuthenticationProvider p){ return new ProviderManager(p); }
  @Bean("adminAuthManager") public AuthenticationManager adminAuthManager(@Qualifier("adminAuthProvider") DaoAuthenticationProvider p){ return new ProviderManager(p); }

  private void csrf(HttpSecurity http) throws Exception {
    if (!csrfEnabled){ http.csrf(c -> c.disable()); return; }
    http.csrf(c -> c.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
      .ignoringRequestMatchers("/api/auth/**","/api/admin/auth/**","/api/public/**","/api/appeals/public","/actuator/**","/ws/**"));
  }
  private void headers(HttpSecurity http) throws Exception {
    http.headers(h -> h
      .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; frame-ancestors 'none'"))
      .frameOptions(f -> f.deny())
      .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
      .referrerPolicy(r -> r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
      .contentTypeOptions(c -> {})
      .permissionsPolicy(p -> p.policy("camera=(), microphone=(), geolocation=()")));
  }
  /** Admin idle timeout 30 min (D-031): tracked per session; the student timeout is 2 h (D-013). */
  static class AdminIdleFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
      HttpSession s = req.getSession(false);
      if (s != null && req.getRequestURI().startsWith("/api/admin/auth/") == false){
        Object last = s.getAttribute("ADMIN_LAST_ACTIVE");
        if (last instanceof Long l && Instant.now().toEpochMilli() - l > Duration.ofMinutes(30).toMillis()){
          // D-138: was bare setStatus(401) with an empty body; the admin
          // client parses ApiError JSON, so answer in that shape.
          s.invalidate();
          ApiErrorWriter.write(res, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED",
            "Admin session expired", req.getRequestURI());
          return;
        }
        s.setAttribute("ADMIN_LAST_ACTIVE", Instant.now().toEpochMilli());
      }
      chain.doFilter(req, res);
    }
  }

  @Bean @Order(1) public SecurityFilterChain adminChain(HttpSecurity http, @Qualifier("adminAuthProvider") DaoAuthenticationProvider provider) throws Exception {
    http.securityMatcher("/api/admin/**")
      .authenticationProvider(provider)
      .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED).sessionFixation(sf -> sf.migrateSession()))
      .authorizeHttpRequests(a -> a.requestMatchers("/api/admin/auth/**").permitAll().anyRequest().hasAuthority("MFA_VERIFIED"))
      // D-138: was sendError(401/403) — container HTML error page, not the
      // ApiError JSON every controller error uses. Same for the student chain.
      .exceptionHandling(e -> e.authenticationEntryPoint((req,res,ex)->ApiErrorWriter.write(res, 401, "UNAUTHORIZED", "Admin authentication required", req.getRequestURI()))
        .accessDeniedHandler((req,res,ex)->ApiErrorWriter.write(res, 403, "FORBIDDEN", "Access denied", req.getRequestURI())))
      .addFilterBefore(new AdminIdleFilter(), BasicAuthenticationFilter.class)
      .addFilterBefore(new MustChangePasswordFilter(), BasicAuthenticationFilter.class);
    csrf(http); headers(http);
    return http.build();
  }
  @Bean @Order(2) public SecurityFilterChain studentChain(HttpSecurity http, @Qualifier("studentAuthProvider") DaoAuthenticationProvider provider) throws Exception {
    http.securityMatcher("/api/**","/ws/**")
      .authenticationProvider(provider)
      .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED).sessionFixation(sf -> sf.migrateSession()))
      .authorizeHttpRequests(a -> a
        // GET /api/health is a liveness probe (returns only {status, service},
        // no user data): public, like /actuator/health. It was previously
        // authenticated-only because it sits under /api/** — an unauthenticated
        // health check 401'd (audit-03 I-15 / inventory G-9).
        .requestMatchers("/api/auth/**","/api/public/**","/api/csrf","/api/health","/api/appeals/public").permitAll()
        .requestMatchers("/actuator/health","/actuator/info").permitAll()
        .requestMatchers("/v3/api-docs/**","/swagger-ui/**","/swagger-ui.html").permitAll()
        // GitHub OAuth callback: GitHub's redirect carries no session; the
        // HMAC-signed state parameter binds the exchange to the student.
        .requestMatchers("/api/workspace/github/callback").permitAll()
        .anyRequest().authenticated())
      .exceptionHandling(e -> e.authenticationEntryPoint((req,res,ex)->ApiErrorWriter.write(res, 401, "UNAUTHORIZED", "Authentication required", req.getRequestURI()))
        .accessDeniedHandler((req,res,ex)->ApiErrorWriter.write(res, 403, "FORBIDDEN", "Access denied", req.getRequestURI())));
    csrf(http); headers(http);
    // D-094: a session flagged must-change-password may only reach the
    // change-password escape hatches (see MustChangePasswordFilter).
    http.addFilterBefore(new MustChangePasswordFilter(), BasicAuthenticationFilter.class);
    return http.build();
  }
  @Bean @Order(3) public SecurityFilterChain fallbackChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/**").authorizeHttpRequests(a -> a.anyRequest().permitAll());
    headers(http);
    return http.build();
  }
}
