package com.example.ssoj.judge;

import com.example.ssoj.judge.executor.DockerProcessExecutor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DockerProcessExecutorOutputNormalizationTest {

    private final DockerProcessExecutor dockerProcessExecutor = new DockerProcessExecutor();

    @Test
    void normalizeOutput_treatsMissingFinalNewlineAsEqual() {
        assertThat(normalizedEquals("15", "15\n")).isTrue();
        assertThat(normalizedEquals("15\n", "15")).isTrue();
    }

    @Test
    void normalizeOutput_ignoresTrailingWhitespaceAndTrailingBlankLines() {
        assertThat(normalizedEquals("15  \n", "15")).isTrue();
        assertThat(normalizedEquals("15\n\n", "15")).isTrue();
        assertThat(normalizedEquals("15\r\n", "15\n")).isTrue();
    }

    @Test
    void normalizeOutput_preservesMeaningfulInternalWhitespace() {
        assertThat(normalizedEquals("1 2", "12")).isFalse();
    }

    private boolean normalizedEquals(String expected, String actual) {
        return dockerProcessExecutor.normalizeOutputForComparison(expected)
                .equals(dockerProcessExecutor.normalizeOutputForComparison(actual));
    }
}
