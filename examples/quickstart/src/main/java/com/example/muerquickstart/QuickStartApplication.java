package com.example.muerquickstart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the standalone Document System quick-start application.
 *
 * <p>This is a plain Spring Boot application. Muer is brought in through the
 * aggregate Starter ({@code cloud.muer:muer-spring-boot-starter}); all the
 * IAM engine, the {@code /iam/**} REST API and the Flyway schema migration
 * arrive automatically. The host only needs to provide the SPI beans in the
 * {@code account}, {@code document} and {@code security} packages.</p>
 */
@SpringBootApplication
public class QuickStartApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuickStartApplication.class, args);
    }
}
