package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface DepartmentAliasRepository extends JpaRepository<DepartmentAlias, Long> {
  List<DepartmentAlias> findByCatalogIdAndActiveTrue(Long catalogId);
  List<DepartmentAlias> findByCatalogIdInAndActiveTrue(Collection<Long> catalogIds);
  // IgnoreCase: the MySQL unique key is case-insensitive (utf8mb4
  // 0900_ai_ci) and the H2 test database is not, so the check must not
  // depend on the database collation.
  Optional<DepartmentAlias> findByAliasIgnoreCase(String alias);
  boolean existsByAliasIgnoreCase(String alias);
}
