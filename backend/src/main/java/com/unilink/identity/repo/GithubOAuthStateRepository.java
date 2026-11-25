package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.*;
public interface GithubOAuthStateRepository extends JpaRepository<GithubOAuthState, Long> {
  Optional<GithubOAuthState> findByNonce(String nonce);
  void deleteByExpiresAtBefore(LocalDateTime cutoff);
}
