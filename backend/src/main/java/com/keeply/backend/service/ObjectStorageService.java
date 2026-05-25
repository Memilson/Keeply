/* Interface para abstrair as operações básicas de armazenamento de objetos em um serviço provedor de storage. */
package com.keeply.backend.service;

import java.io.InputStream;

public interface ObjectStorageService {
    void put(String key, InputStream data, long length, String contentType);
    InputStream getStream(String key);
    void delete(String key);
    boolean exists(String key);
}
