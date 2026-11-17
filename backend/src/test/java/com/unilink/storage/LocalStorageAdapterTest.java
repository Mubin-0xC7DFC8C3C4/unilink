package com.unilink.storage;
import com.unilink.common.Exceptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** delete() must apply the same root-normalisation check as store/open/exists:
 *  a key escaping the root is rejected, and a failed delete is logged, not
 *  silently swallowed. */
class LocalStorageAdapterTest {
  @TempDir Path tmp;

  @Test void deleteRejectsTraversalKeys() throws Exception {
    Path root = tmp.resolve("root");
    Files.createDirectories(root);
    Path outside = tmp.resolve("outside.txt");
    Files.writeString(outside, "keep me");
    var adapter = new LocalStorageAdapter(root.toString());
    assertThrows(Exceptions.BadRequest.class, () -> adapter.delete("../outside.txt"));
    assertTrue(Files.exists(outside), "traversal delete must not remove files outside the root");
    assertThrows(Exceptions.BadRequest.class, () -> adapter.delete("../../etc/passwd"));
  }

  @Test void deleteRemovesOwnFileAndIgnoresMissing() throws Exception {
    var adapter = new LocalStorageAdapter(tmp.resolve("root2").toString());
    String key = adapter.store("ns", "a.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
    assertTrue(adapter.exists(key));
    adapter.delete(key);
    assertFalse(adapter.exists(key));
    assertDoesNotThrow(() -> adapter.delete(key)); // deleting a missing file is a no-op
  }
}
