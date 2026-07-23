package com.company.sage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SageApplication {

    public static void main(String[] args) {
        SpringApplication.run(SageApplication.class, args);
    }
}
