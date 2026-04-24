package com.example.ssoj.judge;

import com.example.ssoj.judge.domain.model.JudgeContext;
import com.example.ssoj.judge.domain.model.JudgeExecutionResult;
import com.example.ssoj.judge.executor.CppExecutor;
import com.example.ssoj.judge.executor.DockerProcessExecutor;
import com.example.ssoj.judge.executor.JavaExecutor;
import com.example.ssoj.judge.executor.PythonExecutor;
import com.example.ssoj.judge.executor.WorkspaceDirectoryFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "run.real.executor.tests", matches = "true")
class RealLanguageExecutorTest {

    private static final int TIME_LIMIT_MS = 3000;
    private static final int MEMORY_LIMIT_MB = 128;
    private static final long COMPILE_TIMEOUT_MS = 15000L;

    private final DockerProcessExecutor dockerProcessExecutor = new DockerProcessExecutor();
    private final WorkspaceDirectoryFactory workspaceDirectoryFactory = new WorkspaceDirectoryFactory("/tmp/ssoj-runner-workspaces");

    @Test
    void cppExecutor_executesLongRunningButSuccessfulSubmission() {
        // 1초 정도 대기한 뒤 정답을 출력하는 실제 제출 코드다.
        CppExecutor cppExecutor = new CppExecutor(
                "gcc:13",
                COMPILE_TIMEOUT_MS,
                "g++ main.cpp -O2 -std=c++17 -o main",
                "./main",
                dockerProcessExecutor,
                workspaceDirectoryFactory
        );

        JudgeExecutionResult result = cppExecutor.execute(new JudgeContext(
                1001L,
                2001L,
                "cpp",
                """
                #include <chrono>
                #include <iostream>
                #include <thread>

                int main() {
                    std::ios::sync_with_stdio(false);
                    std::cin.tie(nullptr);

                    long long a, b;
                    std::cin >> a >> b;
                    std::this_thread::sleep_for(std::chrono::milliseconds(1000));
                    std::cout << (a + b) << "\\n";
                    return 0;
                }
                """,
                "1 2\n",
                TIME_LIMIT_MS,
                MEMORY_LIMIT_MB
        ));

        assertSuccessfulLongRunningResult(result, "3\n");
    }

    @Test
    void javaExecutor_executesLongRunningButSuccessfulSubmission() {
        // 1초 정도 대기한 뒤 정답을 출력하는 실제 제출 코드다.
        JavaExecutor javaExecutor = new JavaExecutor(
                "eclipse-temurin:17-jdk",
                COMPILE_TIMEOUT_MS,
                dockerProcessExecutor,
                workspaceDirectoryFactory
        );

        JudgeExecutionResult result = javaExecutor.execute(new JudgeContext(
                1002L,
                2002L,
                "java",
                """
                import java.io.BufferedReader;
                import java.io.InputStreamReader;
                import java.util.StringTokenizer;

                public class Main {
                    public static void main(String[] args) throws Exception {
                        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                        StringTokenizer st = new StringTokenizer(br.readLine());
                        long a = Long.parseLong(st.nextToken());
                        long b = Long.parseLong(st.nextToken());

                        Thread.sleep(1000);
                        System.out.println(a + b);
                    }
                }
                """,
                "1 2\n",
                TIME_LIMIT_MS,
                MEMORY_LIMIT_MB
        ));

        assertSuccessfulLongRunningResult(result, "3\n");
    }

    @Test
    void pythonExecutor_executesLongRunningButSuccessfulSubmission() {
        // 1초 정도 대기한 뒤 정답을 출력하는 실제 제출 코드다.
        PythonExecutor pythonExecutor = new PythonExecutor("python:3.11", dockerProcessExecutor, workspaceDirectoryFactory);

        JudgeExecutionResult result = pythonExecutor.execute(new JudgeContext(
                1003L,
                2003L,
                "python",
                """
                import time

                a, b = map(int, input().split())
                time.sleep(1)
                print(a + b)
                """,
                "1 2\n",
                TIME_LIMIT_MS,
                MEMORY_LIMIT_MB
        ));

        assertSuccessfulLongRunningResult(result, "3\n");
    }

    @Test
    void pythonExecutor_marksSleepLongerThanProblemLimitAsTle() {
        PythonExecutor pythonExecutor = new PythonExecutor("python:3.11", dockerProcessExecutor, workspaceDirectoryFactory);

        JudgeExecutionResult result = pythonExecutor.execute(new JudgeContext(
                1004L,
                2004L,
                "python",
                """
                import time
                time.sleep(2)
                print("done")
                """,
                "",
                1000,
                MEMORY_LIMIT_MB
        ));

        assertThat(result.systemError()).isFalse();
        assertThat(result.timedOut()).isTrue();
        assertThat(result.exitCode()).isEqualTo(124);
        assertThat(result.executionTimeMs()).isGreaterThanOrEqualTo(1000);
    }

    @Test
    void pythonExecutor_acceptsSleepWithinProblemLimit() {
        PythonExecutor pythonExecutor = new PythonExecutor("python:3.11", dockerProcessExecutor, workspaceDirectoryFactory);

        JudgeExecutionResult result = pythonExecutor.execute(new JudgeContext(
                1005L,
                2005L,
                "python",
                """
                import time
                time.sleep(2)
                print("done")
                """,
                "",
                3000,
                MEMORY_LIMIT_MB
        ));

        assertThat(result.systemError()).isFalse();
        assertThat(result.timedOut()).isFalse();
        assertThat(result.success()).isTrue();
        assertThat(result.stdout()).isEqualTo("done\n");
        assertThat(result.executionTimeMs()).isGreaterThanOrEqualTo(1900);
        assertThat(result.executionTimeMs()).isLessThan(3000);
    }

    private static void assertSuccessfulLongRunningResult(JudgeExecutionResult result, String expectedOutput) {
        assertThat(result.systemError()).isFalse();
        assertThat(result.timedOut()).isFalse();
        assertThat(result.success()).isTrue();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stderr()).isBlank();
        assertThat(result.stdout()).isEqualTo(expectedOutput);
        assertThat(result.executionTimeMs()).isNotNull();
        assertThat(result.executionTimeMs()).isGreaterThanOrEqualTo(900);
        assertThat(result.executionTimeMs()).isLessThan(TIME_LIMIT_MS);
        assertThat(result.memoryUsageKb()).isNotNull();
        assertThat(result.memoryUsageKb()).isGreaterThan(0);
    }
}
