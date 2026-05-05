package com.example.ssoj.judge.executor;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class WarmContainerManager {

    static final String WARM_CONTAINER_LABEL = "ssoj.warm-container=true";
    private static final Logger log = LoggerFactory.getLogger(WarmContainerManager.class);

    private final WarmContainerProperties properties;
    private final Map<String, WarmContainerPool> pools = new ConcurrentHashMap<>();

    public WarmContainerManager(WarmContainerProperties properties) {
        this.properties = properties;
    }

    public boolean enabled() {
        return properties.enabled();
    }

    public boolean fallbackToDockerRun() {
        return properties.fallbackToDockerRun();
    }

    @PostConstruct
    public void cleanupStaleWarmContainers() {
        if (!properties.enabled()) {
            return;
        }

        try {
            Process listProcess = new ProcessBuilder(
                    "docker",
                    "ps",
                    "-aq",
                    "--filter",
                    "label=" + WARM_CONTAINER_LABEL
            ).start();
            boolean listed = listProcess.waitFor(5, TimeUnit.SECONDS);
            if (!listed) {
                listProcess.destroyForcibly();
                log.warn("Timed out while listing stale warm containers");
                return;
            }
            String stdout = new String(listProcess.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (stdout.isBlank()) {
                return;
            }
            for (String containerId : stdout.split("\\R")) {
                if (!containerId.isBlank()) {
                    removeContainer(containerId.trim());
                }
            }
        } catch (Exception exception) {
            log.warn("Failed to clean stale warm containers", exception);
        }
    }

    public long cleanupTimeoutMs() {
        return properties.cleanupTimeoutMs();
    }

    public WarmContainerPool.WarmContainerAcquireResult acquire(
            Long submissionId,
            String language,
            String dockerImage,
            int dockerMemoryMb,
            String cpuLimit,
            String pidsLimit
    ) throws IOException, InterruptedException {
        return pool(dockerImage, dockerMemoryMb, cpuLimit, pidsLimit)
                .acquire(submissionId, language);
    }

    public void release(Long submissionId, WarmContainer container, String cpuLimit, String pidsLimit) {
        pool(container.dockerImage(), container.dockerMemoryMb(), cpuLimit, pidsLimit)
                .release(submissionId, container);
    }

    public void discardAndReplace(WarmContainer container, String reason, String cpuLimit, String pidsLimit) {
        pool(container.dockerImage(), container.dockerMemoryMb(), cpuLimit, pidsLimit)
                .discardAndReplace(container, reason);
    }

    private WarmContainerPool pool(String dockerImage, int dockerMemoryMb, String cpuLimit, String pidsLimit) {
        String key = dockerImage + ":" + dockerMemoryMb;
        return pools.computeIfAbsent(
                key,
                ignored -> new WarmContainerPool(dockerImage, dockerMemoryMb, properties, cpuLimit, pidsLimit)
        );
    }

    @PreDestroy
    public void shutdown() {
        pools.values().forEach(WarmContainerPool::stopAll);
    }

    private void removeContainer(String containerId) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("docker", "rm", "-f", containerId).start();
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            log.warn("Timed out while removing stale warm container {}", containerId);
            return;
        }
        if (process.exitValue() == 0) {
            log.info("Removed stale warm container {}", containerId);
        }
    }
}
