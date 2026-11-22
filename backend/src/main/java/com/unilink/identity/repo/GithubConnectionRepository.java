package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface GithubConnectionRepository extends JpaRepository<GithubConnection, Long> {
  Optional<GithubConnection> findByUserId(Long userId);
  void deleteByUserId(Long userId);
}
