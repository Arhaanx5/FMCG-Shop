package com.shop.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CacheEvictionScheduler {

    private final CacheManager cacheManager;

    // Daily at midnight, evict all AI generated messages to prevent memory buildup
    @Scheduled(cron = "0 0 0 * * ?")
    public void evictAiRemindersCacheDaily() {
        log.info("Executing scheduled daily cache eviction for 'aiReminders'");
        Cache cache = cacheManager.getCache("aiReminders");
        if (cache != null) {
            cache.clear();
            log.info("Successfully evicted 'aiReminders' cache.");
        }
    }
}
