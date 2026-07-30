package com.harvest.chef.knowledge.manager;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared thread pool the Knowledge Provider Manager uses to run providers in parallel. */
@Configuration
public class KnowledgeExecutorConfig {

    @Bean
    public ExecutorService knowledgeProviderExecutor() {
        return Executors.newFixedThreadPool(8, runnable -> {
            Thread thread = new Thread(runnable, "knowledge-provider-worker");
            thread.setDaemon(true);
            return thread;
        });
    }
}
