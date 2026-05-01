package com.example.ssoj.judge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "run.docker.executor.tests", matches = "true")
class DockerRuntimeDependencyTest {

    @Test
    void runnerImages_provideUsrBinTimeVerbose() throws Exception {
        assertImageSupportsTimeVerbose("ssoj-java-runner:17");
        assertImageSupportsTimeVerbose("ssoj-python-runner:3.11");
        assertImageSupportsTimeVerbose("ssoj-cpp-runner:13");
    }

    private static void assertImageSupportsTimeVerbose(String image) throws Exception {
        Process process = new ProcessBuilder(
                List.of("docker", "run", "--rm", image, "/usr/bin/bash", "-lc", "/usr/bin/time -v echo hi")
        ).start();

        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }

        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(finished).isTrue();
        assertThat(process.exitValue()).isEqualTo(0);
        assertThat(stdout).contains("hi");
        assertThat(stderr).contains("Elapsed (wall clock) time");
        assertThat(stderr).contains("Maximum resident set size");
    }
}
