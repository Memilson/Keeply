/* Interface para abstrair as operações básicas de armazenamento de objetos em um serviço provedor de storage. */
package com.keeply.backend.service;

public interface ObjectStorageService {
    void put(String key, byte[] data, String contentType);
    byte[] get(String key);
    void delete(String key);
    boolean exists(String key);
}
