package com.aigo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.aigo.config")
public class AiGoApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiGoApplication.class, args);
    }
}
