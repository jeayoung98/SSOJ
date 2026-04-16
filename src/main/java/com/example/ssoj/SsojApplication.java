package com.example.ssoj;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

@EnableScheduling
@SpringBootApplication
public class SsojApplication {

    private static final Logger log = LoggerFactory.getLogger(SsojApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(SsojApplication.class, args);
    }

    @Bean
    CommandLineRunner dataSourceConnectionCheck(DataSource dataSource) {
        return args -> {
            try (var connection = dataSource.getConnection()) {
                var metadata = connection.getMetaData();
                log.info(
                        "Connected to PostgreSQL: url={} user={}",
                        metadata.getURL(),
                        metadata.getUserName()
                );
            }
        };
    }
}
