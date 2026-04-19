package com.example.ssoj.judge.infrastructure.cloudtasks;

import com.google.cloud.tasks.v2.CloudTasksClient;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CloudTasksClientFactory {

    public CloudTasksClient create() throws IOException {
        return CloudTasksClient.create();
    }
}
