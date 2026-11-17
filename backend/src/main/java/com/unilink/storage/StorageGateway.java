package com.unilink.storage;
import java.io.InputStream;
/** Adapter pattern: private storage behind one interface (ARCH §8). */
public interface StorageGateway {
  String store(String namespace, String fileName, String contentType, byte[] bytes);
  InputStream open(String storageKey);
  boolean exists(String storageKey);
  void delete(String storageKey);
  long size(String storageKey);
}
