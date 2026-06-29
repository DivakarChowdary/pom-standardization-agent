package com.divakarchowdary.pom;

import com.divakarchowdary.pom.config.PomMappingLoader;
import com.divakarchowdary.pom.config.RepoFileLoader;
import com.divakarchowdary.pom.model.PomUpdaterProperties;
import com.divakarchowdary.pom.service.BulkApprovePRs;
import com.divakarchowdary.pom.service.PomUpdaterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;

@Slf4j
@SpringBootApplication
@RequiredArgsConstructor
public class Application implements CommandLineRunner {

    private final PomUpdaterService service;
    private final PomUpdaterProperties props;
    private final RepoFileLoader repoFileLoader;
    private final PomMappingLoader mappingLoader;
    private final BulkApprovePRs bulkApprovePRs;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        if (args.length == 0) {
            log.error("Usage: java -jar pom-standardization-agent.jar <profile> [approve]");
            log.error("Example: java -jar pom-standardization-agent.jar abc");
            log.error("Example: java -jar pom-standardization-agent.jar abc approve");
            System.exit(1);
        }

        String profile = args[0].trim().toLowerCase();
        boolean approve = Arrays.asList(args).contains("approve");

        log.info("=========================================");
        log.info("  POM Standardization Agent");
        log.info("  Profile : {}", profile);
        log.info("  Mode    : {}", approve ? "APPROVE" : "UPDATE");
        log.info("=========================================");

        props.setReposFile("repos/" + profile + "-repos.json");
        props.setMappingFile("pom-mapping/" + profile + "-pom-mapping.json");
        props.setProfile(profile);

        repoFileLoader.load();
        mappingLoader.load();

        if (approve) {
            log.info("Running bulk PR approval...");
            bulkApprovePRs.approveLatestExcel();
        } else {
            log.info("Running POM standardization...");
            service.run();
        }
    }
}
