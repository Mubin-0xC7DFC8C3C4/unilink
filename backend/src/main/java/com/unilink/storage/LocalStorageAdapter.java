package com.unilink.storage;
import com.unilink.common.Exceptions; import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.*; import java.nio.file.*; import java.util.UUID;
/** Private local-volume adapter (dev). Files live under a non-public directory; never served directly. */
@Component
public class LocalStorageAdapter implements StorageGateway {
  private final Path root;
  public LocalStorageAdapter(@Value("${app.storage.local-path:./storage-dev}") String path){ this.root = Path.of(path); }
  @Override public String store(String namespace, String fileName, String contentType, byte[] bytes){
    try { Path dir = root.resolve(namespace); Files.createDirectories(dir);
      String key = namespace + "/" + UUID.randomUUID() + "-" + fileName.replaceAll("[^A-Za-z0-9._-]","_");
      Path target = root.resolve(key); if (!target.normalize().startsWith(root.normalize())) throw new Exceptions.BadRequest("INVALID_FILENAME","Invalid file name");
      Files.write(target, bytes); return key;
    } catch (Exceptions.AppException e){ throw e; } catch (IOException e){ throw new IllegalStateException("Storage unavailable", e); }
  }
  @Override public InputStream open(String key){
    try { Path p = root.resolve(key); if (!p.normalize().startsWith(root.normalize()) || !Files.exists(p)) throw new Exceptions.NotFound("File");
      return Files.newInputStream(p); } catch (Exceptions.AppException e){ throw e; } catch (IOException e){ throw new IllegalStateException(e); }
  }
  @Override public boolean exists(String key){ try { Path p=root.resolve(key); return p.normalize().startsWith(root.normalize()) && Files.exists(p); } catch (Exception e){ return false; } }
  @Override public void delete(String key){ try { Files.deleteIfExists(root.resolve(key)); } catch (IOException ignored){} }
  @Override public long size(String key){ try { return Files.size(root.resolve(key)); } catch (IOException e){ return -1; } }
}
