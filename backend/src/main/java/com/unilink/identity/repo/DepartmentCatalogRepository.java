package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.LocalDateTime;

public interface DepartmentCatalogRepository extends JpaRepository<DepartmentCatalog, Long> {
  List<DepartmentCatalog> findByActiveTrue();
  Optional<DepartmentCatalog> findByName(String name);
  /** D-198: server-side catalog search across name, short_name and
   *  aliases, case-insensitive substring. LIKE wildcards in the query
   *  are escaped by the caller (PublicCatalogService). */
  @Query("SELECT DISTINCT c FROM DepartmentCatalog c LEFT JOIN DepartmentAlias a ON a.catalogId = c.id AND a.active = true "
      + "WHERE c.active = true AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\\' "
      + "OR LOWER(c.shortName) LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\\' "
      + "OR LOWER(a.alias) LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\\')")
  List<DepartmentCatalog> searchActive(@Param("q") String q);
}
