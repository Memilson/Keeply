package com.keeply.agent.daemon;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class CronScheduler {
    private static final Logger log = LoggerFactory.getLogger(CronScheduler.class);
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final ExecutionTime executionTime;
    private final Runnable task;
    private final Supplier<ZonedDateTime> nowSupplier;

    public CronScheduler(String cronExpression, Runnable task) {
        this(cronExpression, task, ZonedDateTime::now);
    }

    CronScheduler(String cronExpression, Runnable task, Supplier<ZonedDateTime> nowSupplier) {
        CronParser parser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));
        Cron cron = parser.parse(cronExpression);
        cron.validate();

        this.executionTime = ExecutionTime.forCron(cron);
        this.task = task;
        this.nowSupplier = nowSupplier;
    }

    public void start() {
        scheduleNext();
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    long delayToNextRunSeconds() {
        ZonedDateTime now = nowSupplier.get();
        return executionTime.nextExecution(now)
                .map(next -> Math.max(1L, Duration.between(now, next).getSeconds()))
                .orElseThrow(() -> new IllegalStateException("Cron sem próxima execução"));
    }

    private void scheduleNext() {
        long delay = delayToNextRunSeconds();
        log.info("Próxima execução agendada em {}s", delay);
        executor.schedule(() -> {
            try {
                task.run();
            } finally {
                scheduleNext();
            }
        }, delay, TimeUnit.SECONDS);
    }
}
