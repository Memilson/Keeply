package com.keeply.backend.service;

public interface ObjectStorageService {
    void put(String key, byte[] data, String contentType);
    byte[] get(String key);
    void delete(String key);
    boolean exists(String key);
}
