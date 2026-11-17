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

  @Test void sizeRejectsTraversalAndAbsoluteKeys() throws Exception {
    Path root = tmp.resolve("root3");
    Files.createDirectories(root);
    Path outside = tmp.resolve("secret.txt");
    Files.writeString(outside, "top secret, 10 bytes long-ish");
    var adapter = new LocalStorageAdapter(root.toString());
    assertEquals(-1, adapter.size("../secret.txt"), "size() must not leak an outside file's size");
    assertEquals(-1, adapter.size(outside.toString()), "size() must not leak an absolute path's size");
    String key = adapter.store("ns", "a.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
    assertEquals(5, adapter.size(key));
  }

  @Test void storeSanitisesNullAndTraversalFileNames() throws Exception {
    var adapter = new LocalStorageAdapter(tmp.resolve("root4").toString());
    String nullName = adapter.store("ns", null, "text/plain", "x".getBytes(StandardCharsets.UTF_8));
    assertTrue(nullName.startsWith("ns/") && !nullName.contains(".."), "null file name must get a safe default");
    assertTrue(adapter.exists(nullName));
    String traversal = adapter.store("ns", "../../evil.txt", "text/plain", "x".getBytes(StandardCharsets.UTF_8));
    assertFalse(traversal.contains(".."), "traversal file name must not escape the namespace");
    assertTrue(adapter.exists(traversal));
    assertFalse(Files.exists(tmp.resolve("evil.txt")), "nothing may be written outside the root");
  }

  @Test void storeRejectsBadNamespaceAndOpenRejectsTraversal() throws Exception {
    var adapter = new LocalStorageAdapter(tmp.resolve("root5").toString());
    assertThrows(Exceptions.BadRequest.class,
      () -> adapter.store("../escape", "a.txt", "text/plain", "x".getBytes(StandardCharsets.UTF_8)));
    assertThrows(Exceptions.NotFound.class, () -> adapter.open("../outside.txt"));
    assertThrows(Exceptions.NotFound.class, () -> adapter.open(tmp.resolve("abs.txt").toString()));
  }
}
