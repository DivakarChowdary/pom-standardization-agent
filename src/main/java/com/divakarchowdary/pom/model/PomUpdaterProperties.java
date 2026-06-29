package com.divakarchowdary.pom.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "pom.updater")
public class PomUpdaterProperties {

    private String workDir;
    private String prDir;
    private String githubBaseUrl = "https://github.com/";
    private String githubApiBaseUrl = "https://api.github.com";
    private String githubToken;
    private String githubUser;
    private String baseBranch = "main";
    private String featureBranch;
    private String reposFile;
    private String mappingFile;
    private String profile;
    private String owner;
    private String mavenHome;
    private String approverToken;
    private int threadPoolSize = 7;
    private int repoTimeoutMinutes = 10;

    private Map<String, String> versions = new HashMap<>();
    private List<Repo> repos = new ArrayList<>();
    private List<String> mavenGoals = new ArrayList<>();
    private List<String> reviewerUsernames = new ArrayList<>();

    // Email
    private String emailFrom;
    private String emailSmtpHost;
    private int emailSmtpPort = 587;
    private boolean emailSmtpAuth = false;
    private List<String> emailRecipients = new ArrayList<>();
    private List<String> emailCcRecipients = new ArrayList<>();

    @Data
    @NoArgsConstructor
    public static class Repo {
        private String name;

        public Repo(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
