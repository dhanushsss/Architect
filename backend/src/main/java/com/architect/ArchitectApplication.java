package com.architect;

import com.architect.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties(AppProperties.class)
public class ArchitectApplication {
    public static void main(String[] args) {
        SpringApplication.run(ArchitectApplication.class, args);
    }
}
