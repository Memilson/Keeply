package com.keeply.agent.daemon;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class CronScheduler {
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final ExecutionTime executionTime;
    private final Runnable task;
    private final Supplier<ZonedDateTime> nowSupplier;
    private final DaemonLogger logger;

    public CronScheduler(String cronExpression, Runnable task, DaemonLogger logger) {
        this(cronExpression, task, logger, ZonedDateTime::now);
    }

    CronScheduler(String cronExpression, Runnable task, DaemonLogger logger, Supplier<ZonedDateTime> nowSupplier) {
        CronParser parser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));
        Cron cron = parser.parse(cronExpression);
        cron.validate();

        this.executionTime = ExecutionTime.forCron(cron);
        this.task = task;
        this.nowSupplier = nowSupplier;
        this.logger = logger;
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
        logger.info("Próxima execução agendada em " + delay + "s");
        executor.schedule(() -> {
            try {
                task.run();
            } finally {
                scheduleNext();
            }
        }, delay, TimeUnit.SECONDS);
    }
}
