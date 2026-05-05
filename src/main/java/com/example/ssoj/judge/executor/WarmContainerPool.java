package com.example.ssoj.judge.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

class WarmContainerPool {

    private static final Logger log = LoggerFactory.getLogger(WarmContainerPool.class);

    private final String dockerImage;
    private final int dockerMemoryMb;
    private final WarmContainerProperties properties;
    private final String cpuLimit;
    private final String pidsLimit;
    private final ArrayBlockingQueue<WarmContainer> idleContainers;
    private final List<WarmContainer> allContainers = new ArrayList<>();

    WarmContainerPool(
            String dockerImage,
            int dockerMemoryMb,
            WarmContainerProperties properties,
            String cpuLimit,
            String pidsLimit
    ) {
        this.dockerImage = dockerImage;
        this.dockerMemoryMb = dockerMemoryMb;
        this.properties = properties;
        this.cpuLimit = cpuLimit;
        this.pidsLimit = pidsLimit;
        this.idleContainers = new ArrayBlockingQueue<>(Math.max(1, properties.poolSize()));
    }

    WarmContainerAcquireResult acquire(Long submissionId, String language) throws IOException, InterruptedException {
        long startedAt = System.nanoTime();
        WarmContainer container = idleContainers.poll();
        if (container == null) {
            container = createIfCapacityAvailable();
        }
        if (container == null) {
            container = idleContainers.poll(properties.acquireTimeoutMs(), TimeUnit.MILLISECONDS);
        }
        if (container == null) {
            throw new WarmContainerException("Timed out while acquiring warm container");
        }
        container.markBusy();
        long acquireWaitMs = elapsedMillis(startedAt);
        log.info(
                "Warm container acquired submissionId={} language={} dockerImage={} containerId={} acquireWaitMs={} poolSize={}",
                submissionId,
                language,
                dockerImage,
                container.containerId(),
                acquireWaitMs,
                properties.poolSize()
        );
        return new WarmContainerAcquireResult(container, acquireWaitMs, properties.poolSize());
    }

    void release(Long submissionId, WarmContainer container) {
        int useCount = container.incrementUseCount();
        if (useCount >= properties.maxUseCount()) {
            destroyAndMaybeReplace(container, "max-use-count");
            log.info(
                    "Warm container recreated reason=max-use-count containerId={} dockerImage={}",
                    container.containerId(),
                    dockerImage
            );
            return;
        }

        container.markIdle();
        if (!idleContainers.offer(container)) {
            destroyAndMaybeReplace(container, "idle-queue-full");
            return;
        }

        log.info(
                "Warm container released submissionId={} containerId={} useCount={}",
                submissionId,
                container.containerId(),
                useCount
        );
    }

    void discardAndReplace(WarmContainer container, String reason) {
        container.markBroken();
        destroyAndMaybeReplace(container, reason);
        log.info(
                "Warm container recreated reason={} containerId={} dockerImage={}",
                reason,
                container.containerId(),
                dockerImage
        );
    }

    synchronized void stopAll() {
        for (WarmContainer container : List.copyOf(allContainers)) {
            destroy(container);
        }
        allContainers.clear();
        idleContainers.clear();
    }

    private synchronized WarmContainer createIfCapacityAvailable() throws IOException, InterruptedException {
        if (allContainers.size() >= properties.poolSize()) {
            return null;
        }

        WarmContainer container = createContainer();
        allContainers.add(container);
        return container;
    }

    private synchronized void destroyAndMaybeReplace(WarmContainer container, String reason) {
        allContainers.remove(container);
        idleContainers.remove(container);
        destroy(container);
        try {
            WarmContainer replacement = createContainer();
            allContainers.add(replacement);
            if (!idleContainers.offer(replacement)) {
                destroy(replacement);
                allContainers.remove(replacement);
            }
        } catch (Exception exception) {
            log.warn("Failed to recreate warm container reason={} dockerImage={}", reason, dockerImage, exception);
        }
    }

    private WarmContainer createContainer() throws IOException, InterruptedException {
        List<String> command = List.of(
                "docker",
                "run",
                "-d",
                "--network",
                "none",
                "-m",
                dockerMemoryMb + "m",
                "--cpus",
                cpuLimit,
                "--pids-limit",
                pidsLimit,
                "--label",
                WarmContainerManager.WARM_CONTAINER_LABEL,
                "-v",
                properties.workspaceRoot() + ":" + properties.workspaceRoot(),
                "-w",
                properties.workspaceRoot(),
                dockerImage,
                "sh",
                "-lc",
                "sleep infinity"
        );

        ProcessResult result = runDockerCommand(command, properties.startupTimeoutMs());
        if (result.systemError() || result.exitCode() == null || result.exitCode() != 0) {
            throw new WarmContainerException("Failed to start warm container: " + result.stderr());
        }
        String containerId = result.stdout().trim();
        if (containerId.isBlank()) {
            throw new WarmContainerException("Docker did not return warm container id");
        }
        log.info(
                "Warm container started dockerImage={} containerId={} dockerMemoryMb={} poolSize={}",
                dockerImage,
                containerId,
                dockerMemoryMb,
                properties.poolSize()
        );
        return new WarmContainer(containerId, dockerImage, dockerMemoryMb);
    }

    private void destroy(WarmContainer container) {
        container.markStopped();
        try {
            runDockerCommand(List.of("docker", "rm", "-f", container.containerId()), Duration.ofSeconds(10).toMillis());
        } catch (Exception exception) {
            log.warn("Failed to destroy warm container containerId={} dockerImage={}", container.containerId(), dockerImage, exception);
        }
    }

    private ProcessResult runDockerCommand(List<String> command, long timeoutMs) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).start();
        boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            return new ProcessResult("", "Docker command timed out", null, true);
        }
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ProcessResult(stdout, stderr, process.exitValue(), false);
    }

    private long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    record WarmContainerAcquireResult(WarmContainer container, long acquireWaitMs, int poolSize) {
    }

    private record ProcessResult(String stdout, String stderr, Integer exitCode, boolean systemError) {
    }
}
