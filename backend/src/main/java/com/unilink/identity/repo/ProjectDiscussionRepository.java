package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ProjectDiscussionRepository extends JpaRepository<ProjectDiscussion, Long> {
  List<ProjectDiscussion> findByRepoFullNameOrderByCreatedAtAsc(String repoFullName);
}
