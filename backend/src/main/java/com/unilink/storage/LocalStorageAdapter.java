package com.unilink.storage;
import com.unilink.common.Exceptions; import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.io.*; import java.nio.file.*; import java.util.UUID;
/** Private local-volume adapter (default). Files live under a non-public
 *  directory; never served directly. Replaced by DatabaseStorageAdapter
 *  when app.storage.provider=db (D-168). */
@Component
@ConditionalOnProperty(name="app.storage.provider", havingValue="local", matchIfMissing=true)
public class LocalStorageAdapter implements StorageGateway {
  private static final Logger log = LoggerFactory.getLogger(LocalStorageAdapter.class);
  private final Path root;
  public LocalStorageAdapter(@Value("${app.storage.local-path:./storage-dev}") String path){ this.root = Path.of(path); }
  @Override public String store(String namespace, String fileName, String contentType, byte[] bytes){
    if (namespace==null || !namespace.matches("[A-Za-z0-9][A-Za-z0-9_-]*"))
      throw new Exceptions.BadRequest("INVALID_NAMESPACE","Invalid storage namespace");
    String safeName = UploadValidator.sanitizeFileName(fileName);
    try { Path dir = root.resolve(namespace); Files.createDirectories(dir);
      String key = namespace + "/" + UUID.randomUUID() + "-" + safeName;
      Path target = root.resolve(key); if (!target.normalize().startsWith(root.normalize())) throw new Exceptions.BadRequest("INVALID_FILENAME","Invalid file name");
      Files.write(target, bytes); return key;
    } catch (Exceptions.AppException e){ throw e; } catch (IOException e){ throw new IllegalStateException("Storage unavailable", e); }
  }
  @Override public InputStream open(String key){
    try { Path p = root.resolve(key); if (!p.normalize().startsWith(root.normalize()) || !Files.exists(p)) throw new Exceptions.NotFound("File");
      return Files.newInputStream(p); } catch (Exceptions.AppException e){ throw e; } catch (IOException e){ throw new IllegalStateException(e); }
  }
  @Override public boolean exists(String key){ try { Path p=root.resolve(key); return p.normalize().startsWith(root.normalize()) && Files.exists(p); } catch (Exception e){ return false; } }
  @Override public void delete(String key){
    Path p = root.resolve(key);
    if (!p.normalize().startsWith(root.normalize())) throw new Exceptions.BadRequest("INVALID_FILENAME","Invalid file name");
    try { Files.deleteIfExists(p); } catch (IOException e){ log.warn("Failed to delete stored file {}", key, e); }
  }
  @Override public long size(String key){
    try { Path p = root.resolve(key);
      // Same root-normalisation guard as open/exists/delete: without it an
      // absolute or escaping key leaks the size (hence existence) of an
      // arbitrary file. -1 means "no such file inside the root".
      if (!p.normalize().startsWith(root.normalize())) return -1;
      return Files.size(p);
    } catch (IOException e){ return -1; }
  }
}
