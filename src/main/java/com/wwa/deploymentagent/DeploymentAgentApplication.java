package com.wwa.deploymentagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DeploymentAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeploymentAgentApplication.class, args);
    }
}
