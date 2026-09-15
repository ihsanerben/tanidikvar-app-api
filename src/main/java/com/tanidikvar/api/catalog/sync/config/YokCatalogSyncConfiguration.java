package com.tanidikvar.api.catalog.sync.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class YokCatalogSyncConfiguration {
    @Bean
    ThreadPoolTaskExecutor yokCatalogSyncExecutor(){
        var executor=new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("dataset-catalog-sync-");
        executor.initialize();
        return executor;
    }
}
