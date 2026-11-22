package com.unilink.identity.repo;
import com.unilink.identity.domain.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredFileRepository extends JpaRepository<StoredFile, String> {
}
