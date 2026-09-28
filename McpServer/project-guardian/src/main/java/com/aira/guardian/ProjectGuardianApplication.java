package com.aira.guardian;

import com.aira.guardian.proc.WorkspaceGuard;
import com.aira.guardian.rules.RuleSetLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;

@SpringBootApplication
@EnableConfigurationProperties(GuardianProperties.class)
public class ProjectGuardianApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProjectGuardianApplication.class, args);
    }

    @Bean
    public WorkspaceGuard workspaceGuard(GuardianProperties properties) {
        return new WorkspaceGuard(Path.of(properties.getWorkspaceRoot()));
    }

    @Bean
    public RuleSetLoader ruleSetLoader() {
        return new RuleSetLoader();
    }
}
