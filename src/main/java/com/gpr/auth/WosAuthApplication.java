package com.gpr.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.gpr.auth", "com.gpr.common"})
@EntityScan(basePackages = "com.gpr.common.entity")
@EnableJpaRepositories(basePackages = "com.gpr.auth.repository")
public class WosAuthApplication {

    static void main(String[] args) {
        SpringApplication.run(WosAuthApplication.class, args);
    }
}
