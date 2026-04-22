package com.gpm.wos_auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.gpm.auth", "com.gpm.common", "com.gpm.wos_auth"})
@EntityScan(basePackages = {"com.gpm.common.entity", "com.gpm.auth.entity"})
@EnableJpaRepositories(basePackages = "com.gpm.auth.repository")
public class WosAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(WosAuthApplication.class, args);
    }
}
