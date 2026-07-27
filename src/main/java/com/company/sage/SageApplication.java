package com.company.sage;

import com.company.sage.config.SageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SageProperties.class)
public class SageApplication {

    public static void main(String[] args) {
        SpringApplication.run(SageApplication.class, args);
    }
}

