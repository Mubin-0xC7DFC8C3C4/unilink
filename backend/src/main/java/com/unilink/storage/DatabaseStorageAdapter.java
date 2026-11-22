package com.unilink.storage;
import com.unilink.common.Exceptions;
import com.unilink.identity.domain.StoredFile;
import com.unilink.identity.repo.StoredFileRepository;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.io.*; import java.util.UUID;

/** Database-backed storage (D-168), active when app.storage.provider=db.
 *  For hosts with an ephemeral filesystem (Render Free): uploaded files
 *  persist in the database (MySQL/TiDB) and survive restarts and
 *  redeploys. Keys are identical in shape to the local adapter's
 *  (namespace/uuid-filename), so switching providers needs no data
 *  model change. Intended for small files (profile/cover/ID images and
 *  post attachments, all ≤ 10 MB by the upload caps). */
@Component
@ConditionalOnProperty(name="app.storage.provider", havingValue="db")
public class DatabaseStorageAdapter implements StorageGateway {
  private static final Logger log = LoggerFactory.getLogger(DatabaseStorageAdapter.class);
  private final StoredFileRepository files;
  public DatabaseStorageAdapter(StoredFileRepository files){ this.files = files; }

  @Override public String store(String namespace, String fileName, String contentType, byte[] bytes){
    if (namespace==null || !namespace.matches("[A-Za-z0-9][A-Za-z0-9_-]*"))
      throw new Exceptions.BadRequest("INVALID_NAMESPACE","Invalid storage namespace");
    String safeName = UploadValidator.sanitizeFileName(fileName);
    String key = namespace + "/" + UUID.randomUUID() + "-" + safeName;
    StoredFile f = new StoredFile();
    f.setStorageKey(key); f.setNamespace(namespace); f.setContentType(contentType);
    f.setSizeBytes(bytes.length); f.setData(bytes);
    files.save(f);
    return key;
  }
  @Override public InputStream open(String key){
    StoredFile f = files.findById(key).orElseThrow(() -> new Exceptions.NotFound("File"));
    return new ByteArrayInputStream(f.getData());
  }
  @Override public boolean exists(String key){ return files.existsById(key); }
  @Override public void delete(String key){
    try { files.deleteById(key); }
    catch (Exception e){ log.warn("Failed to delete stored file {}", key, e); }
  }
  @Override public long size(String key){
    return files.findById(key).map(StoredFile::getSizeBytes).orElse(-1L);
  }
}
