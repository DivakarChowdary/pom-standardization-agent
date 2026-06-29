package com.divakarchowdary.pom.maven;

import lombok.extern.slf4j.Slf4j;
import org.apache.maven.shared.invoker.*;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;


@Slf4j
@Component
public class MavenRunner {

    public boolean run(File workingDir, List<String> goals, String mavenHome) {
        try {
            InvocationRequest request = new DefaultInvocationRequest();
            request.setPomFile(new File(workingDir, "pom.xml"));
            request.setGoals(goals);
            request.setBatchMode(true);

            Invoker invoker = new DefaultInvoker();
            if (mavenHome != null && !mavenHome.isBlank()) {
                invoker.setMavenHome(new File(mavenHome));
            }

            invoker.setLogger(new PrintStreamLogger(System.out, InvokerLogger.INFO));

            log.info("Running Maven {} in {}", goals, workingDir.getName());
            InvocationResult result = invoker.execute(request);

            if (result.getExitCode() != 0) {
                log.error("Maven build FAILED (exit {}) in {}", result.getExitCode(), workingDir.getName());
                if (result.getExecutionException() != null) {
                    log.error("Maven exception", result.getExecutionException());
                }
                return false;
            }

            log.info("Maven build PASSED in {}", workingDir.getName());
            return true;

        } catch (Exception e) {
            log.error("Failed to run Maven in {}", workingDir.getName(), e);
            return false;
        }
    }
}
