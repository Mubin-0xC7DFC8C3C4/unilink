package com.unilink.storage;
import com.unilink.common.Exceptions;
import com.unilink.identity.repo.StoredFileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

/** D-168: the database storage adapter must honour the same StorageGateway
 *  contract as the local adapter — store returns an opaque namespaced key,
 *  bytes round-trip exactly, and delete/exists/size behave. Runs on H2 in
 *  MySQL mode like the other repository slices. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:unilink-storage;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.flyway.enabled=false",
  "spring.jpa.hibernate.ddl-auto=create-drop",
  "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class DatabaseStorageAdapterTest {
  @Autowired StoredFileRepository repo;

  @Test void roundTripsBytesAndMetadata() throws Exception {
    var adapter = new DatabaseStorageAdapter(repo);
    byte[] bytes = "fake-jpeg-bytes".getBytes(StandardCharsets.UTF_8);
    String key = adapter.store("profile-photos", "My Photo.jpg", "image/jpeg", bytes);
    assertTrue(key.startsWith("profile-photos/"), key);
    assertTrue(adapter.exists(key));
    assertEquals(bytes.length, adapter.size(key));
    assertArrayEquals(bytes, adapter.open(key).readAllBytes());
    // The row is really in the database, not held in memory by the adapter.
    var fresh = new DatabaseStorageAdapter(repo);
    assertArrayEquals(bytes, fresh.open(key).readAllBytes());
    adapter.delete(key);
    assertFalse(adapter.exists(key));
    assertEquals(-1, adapter.size(key));
    assertThrows(Exceptions.NotFound.class, () -> adapter.open(key));
  }

  @Test void rejectsInvalidNamespace() {
    var adapter = new DatabaseStorageAdapter(repo);
    assertThrows(Exceptions.BadRequest.class,
      () -> adapter.store("../escape", "a.txt", "text/plain", new byte[]{1}));
  }
}
