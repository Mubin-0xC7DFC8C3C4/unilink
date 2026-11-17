package com.unilink.admin;
import com.unilink.identity.domain.AdminAccount; import com.unilink.identity.repo.AdminAccountRepository;
import org.slf4j.*; import org.springframework.boot.ApplicationArguments; import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile; import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.stereotype.Component;
/** One-time bootstrap of the first Platform Admin from env (D-068). Runs once: only when no admin exists.
 *  Rotate the password and enrol TOTP at first login. */
@Component @Profile("!test") @Order(1)
public class AdminBootstrap implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
  private final AdminAccountRepository admins; private final PasswordEncoder encoder;
  public AdminBootstrap(AdminAccountRepository admins, PasswordEncoder encoder){ this.admins=admins; this.encoder=encoder; }
  @Override public void run(ApplicationArguments args){
    if (admins.count() > 0) return;
    String email = System.getenv("INITIAL_ADMIN_EMAIL");
    String password = System.getenv("INITIAL_ADMIN_PASSWORD");
    if (email==null || email.isBlank() || password==null || password.isBlank()){
      log.warn("No admin accounts exist and INITIAL_ADMIN_EMAIL/INITIAL_ADMIN_PASSWORD are not set — admin bootstrap skipped. Set them to create the first Platform Admin.");
      return;
    }
    if (password.length()<12){ log.error("INITIAL_ADMIN_PASSWORD must be at least 12 characters; bootstrap refused."); return; }
    AdminAccount a = new AdminAccount(); a.setEmail(email); a.setPasswordHash(encoder.encode(password));
    a.setRole("PLATFORM_ADMIN"); a.setActive(true); a.setTotpEnrolled(false);
    admins.save(a);
    log.warn("Bootstrap Platform Admin created for {} — rotate the password and enrol TOTP at first login.", email);
  }
}
