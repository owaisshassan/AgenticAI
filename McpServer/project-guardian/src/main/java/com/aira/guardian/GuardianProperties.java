package com.aira.guardian;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "guardian")
public class GuardianProperties {

    private String workspaceRoot = ".";
    private String rulesFile = "classpath:guardian-rules.yaml";
    private Process process = new Process();

    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public String getRulesFile() {
        return rulesFile;
    }

    public void setRulesFile(String rulesFile) {
        this.rulesFile = rulesFile;
    }

    public Process getProcess() {
        return process;
    }

    public void setProcess(Process process) {
        this.process = process;
    }

    public static class Process {
        private int mvnTestTimeoutSeconds = 120;
        private int mvnPackageTimeoutSeconds = 180;
        private int gitTimeoutSeconds = 30;

        public Duration mvnTestTimeout() {
            return Duration.ofSeconds(mvnTestTimeoutSeconds);
        }

        public Duration mvnPackageTimeout() {
            return Duration.ofSeconds(mvnPackageTimeoutSeconds);
        }

        public Duration gitTimeout() {
            return Duration.ofSeconds(gitTimeoutSeconds);
        }

        public int getMvnTestTimeoutSeconds() {
            return mvnTestTimeoutSeconds;
        }

        public void setMvnTestTimeoutSeconds(int mvnTestTimeoutSeconds) {
            this.mvnTestTimeoutSeconds = mvnTestTimeoutSeconds;
        }

        public int getMvnPackageTimeoutSeconds() {
            return mvnPackageTimeoutSeconds;
        }

        public void setMvnPackageTimeoutSeconds(int mvnPackageTimeoutSeconds) {
            this.mvnPackageTimeoutSeconds = mvnPackageTimeoutSeconds;
        }

        public int getGitTimeoutSeconds() {
            return gitTimeoutSeconds;
        }

        public void setGitTimeoutSeconds(int gitTimeoutSeconds) {
            this.gitTimeoutSeconds = gitTimeoutSeconds;
        }
    }
}
