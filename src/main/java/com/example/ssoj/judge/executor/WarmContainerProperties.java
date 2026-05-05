package com.example.ssoj.judge.executor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WarmContainerProperties {

    private final boolean enabled;
    private final int poolSize;
    private final boolean fallbackToDockerRun;
    private final int maxUseCount;
    private final long startupTimeoutMs;
    private final long acquireTimeoutMs;
    private final long cleanupTimeoutMs;
    private final String workspaceRoot;

    public WarmContainerProperties(
            @Value("${worker.executor.warm-container.enabled:false}") boolean enabled,
            @Value("${worker.executor.warm-container.pool-size:5}") int poolSize,
            @Value("${worker.executor.warm-container.fallback-to-docker-run:true}") boolean fallbackToDockerRun,
            @Value("${worker.executor.warm-container.max-use-count:100}") int maxUseCount,
            @Value("${worker.executor.warm-container.startup-timeout-ms:10000}") long startupTimeoutMs,
            @Value("${worker.executor.warm-container.acquire-timeout-ms:3000}") long acquireTimeoutMs,
            @Value("${worker.executor.warm-container.cleanup-timeout-ms:3000}") long cleanupTimeoutMs,
            @Value("${worker.executor.workspace-root:/tmp/ssoj-runner-workspaces}") String workspaceRoot
    ) {
        this.enabled = enabled;
        this.poolSize = poolSize;
        this.fallbackToDockerRun = fallbackToDockerRun;
        this.maxUseCount = maxUseCount;
        this.startupTimeoutMs = startupTimeoutMs;
        this.acquireTimeoutMs = acquireTimeoutMs;
        this.cleanupTimeoutMs = cleanupTimeoutMs;
        this.workspaceRoot = workspaceRoot;
    }

    public boolean enabled() {
        return enabled;
    }

    public int poolSize() {
        return poolSize;
    }

    public boolean fallbackToDockerRun() {
        return fallbackToDockerRun;
    }

    public int maxUseCount() {
        return maxUseCount;
    }

    public long startupTimeoutMs() {
        return startupTimeoutMs;
    }

    public long acquireTimeoutMs() {
        return acquireTimeoutMs;
    }

    public long cleanupTimeoutMs() {
        return cleanupTimeoutMs;
    }

    public String workspaceRoot() {
        return workspaceRoot;
    }
}
