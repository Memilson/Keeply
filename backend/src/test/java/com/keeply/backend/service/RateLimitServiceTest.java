package com.keeply.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RateLimitServiceTest {

    @Test
    void fileDownloadLimitBlocksAfterConfiguredThreshold() {
        RateLimitService service = new RateLimitService(5, 15, 5, 15, 30, 2, 5, 5, 10);

        assertDoesNotThrow(() -> service.checkAndRecordFileDownloadAttempt("user-1"));
        assertDoesNotThrow(() -> service.checkAndRecordFileDownloadAttempt("user-1"));
        assertThrows(RateLimitService.RateLimitException.class,
                () -> service.checkAndRecordFileDownloadAttempt("user-1"));
    }

    @Test
    void archiveDownloadLimitBlocksAfterConfiguredThreshold() {
        RateLimitService service = new RateLimitService(5, 15, 5, 15, 30, 20, 5, 1, 10);

        assertDoesNotThrow(() -> service.checkAndRecordArchiveDownloadAttempt("user-1"));
        assertThrows(RateLimitService.RateLimitException.class,
                () -> service.checkAndRecordArchiveDownloadAttempt("user-1"));
    }

    @Test
    void loginLimitBlocksAfterFiveFailuresForIpAndEmail() {
        RateLimitService service = new RateLimitService(5, 15, 5, 15, 30, 10, 5, 1, 10);

        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> service.checkRateLimit("127.0.0.1", "user@example.com"));
            service.recordFailure("127.0.0.1", "user@example.com");
        }

        assertThrows(RateLimitService.RateLimitException.class,
                () -> service.checkRateLimit("127.0.0.1", "user@example.com"));
    }
}
