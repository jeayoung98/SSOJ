package com.example.ssoj.judge.executor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class WorkspaceDirectoryFactory {

    private final Path workspaceRoot;

    public WorkspaceDirectoryFactory(@Value("${worker.executor.workspace-root:/tmp/ssoj-runner-workspaces}") String workspaceRoot) {
        this.workspaceRoot = Path.of(workspaceRoot);
    }

    public Path create(String prefix) throws IOException {
        Files.createDirectories(workspaceRoot);
        return Files.createTempDirectory(workspaceRoot, prefix);
    }
}
