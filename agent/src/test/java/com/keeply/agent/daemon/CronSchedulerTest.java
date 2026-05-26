package com.keeply.agent.daemon;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CronSchedulerTest {
    @Test
    void computesPositiveDelayForValidCron() {
        ZonedDateTime now = ZonedDateTime.of(2026, 5, 23, 10, 2, 0, 0, ZoneId.of("UTC"));
        CronScheduler scheduler = new CronScheduler("*/5 * * * *", () -> { }, () -> now);

        long delay = scheduler.delayToNextRunSeconds();

        assertTrue(delay >= 60 && delay <= 300);
        scheduler.shutdown();
    }
}
