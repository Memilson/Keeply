package com.keeply.backend.service;

import java.time.Instant;

public interface TemporaryCredentialIssuer {
    IssuedCredential issue(String policy, Instant expiresAt);
    void revoke(String accessKey);

    record IssuedCredential(String accessKey, String secretKey, String sessionToken) {
    }
}
