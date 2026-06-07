package com.shop.modules.stock;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.scheduler.expiry.enabled", havingValue = "true", matchIfMissing = true)
public class ExpiryScheduler {

    private final StockBatchRepository batchRepository;
    private final StockService stockService;

    @Value("${app.scheduler.expiry.enabled:true}")
    private boolean enabled;

    @Value("${app.scheduler.expiry.cron:0 0 1 * * ?}")
    private String cronExpression;

    // ── Run tracking fields ──
    @Getter private LocalDateTime lastRunTime;
    @Getter private int lastRunBatchesProcessed;
    @Getter private String lastRunStatus = "Never run";

    // Configurable schedule (defaults to every day at 1:00 AM)
    @Scheduled(cron = "${app.scheduler.expiry.cron:0 0 1 * * ?}")
    public void autoWriteOffExpiredStock() {
        log.info("Starting automated write-off of expired stock batches...");
        runSweep("Scheduler (Auto)");
    }

    // ── Manual trigger (callable from SchedulerController) ──
    public int manualRunNow() {
        log.info("Manual expiry sweep triggered...");
        return runSweep("Manual Trigger");
    }

    // ── Shared sweep logic ──
    private int runSweep(String triggeredBy) {
        LocalDate today = LocalDate.now();
        List<StockBatch> expiredBatches = batchRepository.findExpiringBefore(today);

        int count = 0;
        String errorMsg = null;
        for (StockBatch batch : expiredBatches) {
            if (batch.getSecondaryRemaining() > 0) {
                try {
                    stockService.writeOffExpiredBatch(batch.getId(), "System");
                    count++;
                } catch (Exception e) {
                    errorMsg = e.getMessage();
                    log.error("Failed to write off expired batch {}: {}", batch.getBatchNumber(), e.getMessage());
                }
            }
        }

        lastRunTime = LocalDateTime.now();
        lastRunBatchesProcessed = count;
        lastRunStatus = errorMsg != null
                ? "Completed with errors. Last error: " + errorMsg
                : "Success — " + count + " batch(es) written off";

        log.info("Completed expiry sweep ({}). Total batches processed: {}", triggeredBy, count);
        return count;
    }

    // ── Status info for frontend ──
    public SchedulerStatus getStatus() {
        return new SchedulerStatus(enabled, cronExpression, lastRunTime, lastRunBatchesProcessed, lastRunStatus);
    }

    // ── Status DTO ──
    @Getter
    public static class SchedulerStatus {
        private final boolean enabled;
        private final String cronExpression;
        private final LocalDateTime lastRunTime;
        private final int lastRunBatchesProcessed;
        private final String lastRunStatus;

        public SchedulerStatus(boolean enabled, String cronExpression,
                               LocalDateTime lastRunTime, int lastRunBatchesProcessed, String lastRunStatus) {
            this.enabled = enabled;
            this.cronExpression = cronExpression;
            this.lastRunTime = lastRunTime;
            this.lastRunBatchesProcessed = lastRunBatchesProcessed;
            this.lastRunStatus = lastRunStatus;
        }
    }
}
