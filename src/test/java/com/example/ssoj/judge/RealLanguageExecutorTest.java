package com.example.ssoj.judge;

import com.example.ssoj.judge.domain.model.HiddenTestCaseSnapshot;
import com.example.ssoj.judge.domain.model.JudgeRunContext;
import com.example.ssoj.judge.domain.model.JudgeRunResult;
import com.example.ssoj.judge.executor.CppExecutor;
import com.example.ssoj.judge.executor.DockerProcessExecutor;
import com.example.ssoj.judge.executor.JavaExecutor;
import com.example.ssoj.judge.executor.PythonExecutor;
import com.example.ssoj.judge.executor.WorkspaceDirectoryFactory;
import com.example.ssoj.submission.domain.SubmissionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "run.real.executor.tests", matches = "true")
class RealLanguageExecutorTest {

    private static final long COMPILE_TIMEOUT_MS = 15000L;

    private final DockerProcessExecutor dockerProcessExecutor = new DockerProcessExecutor();
    private final WorkspaceDirectoryFactory workspaceDirectoryFactory = new WorkspaceDirectoryFactory("/tmp/ssoj-runner-workspaces");

    @Test
    void javaCppPython_executeMultipleHiddenCasesWithSingleSubmission() {
        assertAc(new CppExecutor("ssoj-cpp-runner:13", COMPILE_TIMEOUT_MS, "g++ main.cpp -O2 -std=c++17 -o main", "./main", dockerProcessExecutor, workspaceDirectoryFactory)
                .executeSubmission(cppContext()));
        assertAc(new JavaExecutor("ssoj-java-runner:17", COMPILE_TIMEOUT_MS, dockerProcessExecutor, workspaceDirectoryFactory)
                .executeSubmission(javaContext()));
        assertAc(new PythonExecutor("ssoj-python-runner:3.11", dockerProcessExecutor, workspaceDirectoryFactory)
                .executeSubmission(pythonContext()));
    }

    @Test
    void pythonExecutor_returnsTleWhenOneTestCaseExceedsLimit() {
        JudgeRunResult result = new PythonExecutor("ssoj-python-runner:3.11", dockerProcessExecutor, workspaceDirectoryFactory)
                .executeSubmission(new JudgeRunContext(
                        1004L,
                        2004L,
                        "python",
                        """
                        import time
                        time.sleep(2)
                        print("done")
                        """,
                        List.of(new HiddenTestCaseSnapshot(1L, 1, "", "done\n")),
                        1000,
                        128
                ));

        assertThat(result.finalResult()).isEqualTo(SubmissionResult.TLE);
        assertThat(result.failedTestcaseOrder()).isEqualTo(1);
        assertThat(result.executionTimeMs()).isNotNull();
    }

    private void assertAc(JudgeRunResult result) {
        assertThat(result.finalResult()).isEqualTo(SubmissionResult.AC);
        assertThat(result.executionTimeMs()).isNotNull();
        assertThat(result.memoryKb()).isNotNull();
        assertThat(result.failedTestcaseOrder()).isNull();
    }

    private JudgeRunContext cppContext() {
        return new JudgeRunContext(
                1001L,
                2001L,
                "cpp",
                """
                #include <iostream>
                int main() { long long a,b; std::cin >> a >> b; std::cout << (a+b) << "\\n"; }
                """,
                List.of(
                        new HiddenTestCaseSnapshot(1L, 1, "1 2\n", "3\n"),
                        new HiddenTestCaseSnapshot(2L, 2, "2 3\n", "5\n")
                ),
                3000,
                128
        );
    }

    private JudgeRunContext javaContext() {
        return new JudgeRunContext(
                1002L,
                2002L,
                "java",
                """
                import java.io.*; import java.util.*;
                public class Main { public static void main(String[] args) throws Exception {
                  BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                  StringTokenizer st = new StringTokenizer(br.readLine());
                  long a = Long.parseLong(st.nextToken()); long b = Long.parseLong(st.nextToken());
                  System.out.println(a + b);
                }}
                """,
                List.of(
                        new HiddenTestCaseSnapshot(1L, 1, "1 2\n", "3\n"),
                        new HiddenTestCaseSnapshot(2L, 2, "2 3\n", "5\n")
                ),
                3000,
                128
        );
    }

    private JudgeRunContext pythonContext() {
        return new JudgeRunContext(
                1003L,
                2003L,
                "python",
                "a, b = map(int, input().split())\nprint(a + b)\n",
                List.of(
                        new HiddenTestCaseSnapshot(1L, 1, "1 2\n", "3\n"),
                        new HiddenTestCaseSnapshot(2L, 2, "2 3\n", "5\n")
                ),
                3000,
                128
        );
    }
}
