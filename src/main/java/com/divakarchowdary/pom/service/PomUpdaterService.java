package com.divakarchowdary.pom.service;

import com.divakarchowdary.pom.excel.ExcelWriter;
import com.divakarchowdary.pom.exception.PomUpdaterServiceException;
import com.divakarchowdary.pom.model.PomUpdaterProperties;
import com.divakarchowdary.pom.model.RepoResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PomUpdaterService {

    private static final int REPO_TIMEOUT_MINUTES = 10;

    private final PomUpdaterProperties props;
    private final RepoProcessor repoProcessor;
    private final ExcelWriter excelWriter;
    private final EmailService emailService;

    public void run() {
        List<PomUpdaterProperties.Repo> repos = props.getRepos();
        int poolSize = Math.min(repos.size(), props.getThreadPoolSize());

        log.info("=========================================");
        log.info("  Starting POM Standardization");
        log.info("  Repos     : {}", repos.size());
        log.info("  Pool size : {}", poolSize);
        log.info("  Versions  : {}", props.getVersions().size());
        log.info("  Owner     : {}", props.getOwner());
        log.info("=========================================");

        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        List<RepoResult> results = new ArrayList<>();

        try {
            List<Future<RepoResult>> futures = repos.stream()
                    .map(repo -> pool.submit(() -> repoProcessor.process(props, repo)))
                    .toList();

            for (Future<RepoResult> future : futures) {
                try {
                    results.add(future.get(REPO_TIMEOUT_MINUTES, TimeUnit.MINUTES));
                } catch (TimeoutException e) {
                    log.error("Repo processing timed out after {} minutes", REPO_TIMEOUT_MINUTES);
                    future.cancel(true);
                } catch (ExecutionException e) {
                    log.error("Repo processing failed: {}", e.getCause().getMessage(), e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Processing interrupted", e);
                    break;
                }
            }

        } catch (Exception e) {
            throw new PomUpdaterServiceException("Pipeline execution failed", e);
        } finally {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(REPO_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                    log.warn("Thread pool did not terminate in time — forcing shutdown");
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        printSummary(results);
        writeExcelAndNotify(results);
    }

    private void printSummary(List<RepoResult> results) {
        log.info("=========================================");
        log.info("  Pipeline Summary");
        log.info("=========================================");
        results.forEach(r -> log.info("  {} → {} {}",
                r.getRepo().getName(),
                r.getStatus(),
                r.getPrUrl() != null ? r.getPrUrl() : ""));

        long success = results.stream().filter(r -> r.getStatus() == RepoResult.Status.SUCCESS).count();
        long failed = results.stream().filter(r -> r.getStatus() == RepoResult.Status.FAILED).count();
        long noChanges = results.stream().filter(r -> r.getStatus() == RepoResult.Status.NO_CHANGES).count();

        log.info("-----------------------------------------");
        log.info("  SUCCESS   : {}", success);
        log.info("  FAILED    : {}", failed);
        log.info("  NO_CHANGES: {}", noChanges);
        log.info("=========================================");
    }

    private void writeExcelAndNotify(List<RepoResult> results) {
        try {
            excelWriter.writeSummary(results);
        } catch (Exception e) {
            log.error("Failed to write Excel summary: {}", e.getMessage(), e);
        }

        try {
            String batchId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String profile = props.getProfile().toUpperCase();
            emailService.send(
                    profile + " – POM Standardization Complete",
                    buildEmailBody(results, batchId, profile)
            );
        } catch (Exception e) {
            log.error("Failed to send email notification: {}", e.getMessage(), e);
        }
    }

    private String buildEmailBody(List<RepoResult> results, String batchId, String profile) {
        long totalPrs = results.stream().filter(r -> r.getPrUrl() != null).count();

        StringBuilder sb = new StringBuilder();
        sb.append("""
            <html>
            <body style="font-family:Arial,sans-serif;color:#333;">
            <h2 style="color:#0055A4;">%s – POM Standardization Complete</h2>
            <p>The automation run has completed. Please review the pull requests below.</p>
            <table style="border-collapse:collapse;">
                <tr><td>Run ID</td><td>&nbsp;:&nbsp;</td><td><b>%s</b></td></tr>
                <tr><td>Pull Requests</td><td>&nbsp;:&nbsp;</td><td><b>%d</b></td></tr>
            </table>
            <h3 style="color:#0055A4;">Pull Request Summary</h3>
            <table style="border-collapse:collapse;width:100%%;border:1px solid #ccc;">
            <thead>
            <tr style="background:#0055A4;color:white;">
                <th style="padding:8px;border:1px solid #ccc;">Repository</th>
                <th style="padding:8px;border:1px solid #ccc;">Status</th>
                <th style="padding:8px;border:1px solid #ccc;">PR</th>
            </tr>
            </thead>
            <tbody>
            """.formatted(profile, batchId, totalPrs));

        for (RepoResult r : results) {
            String prCell = r.getPrUrl() != null
                    ? "<a href=\"" + r.getPrUrl() + "\">" + r.getPrUrl() + "</a>"
                    : "N/A";

            sb.append("""
                <tr>
                    <td style="padding:8px;border:1px solid #ccc;">%s</td>
                    <td style="padding:8px;border:1px solid #ccc;">%s</td>
                    <td style="padding:8px;border:1px solid #ccc;">%s</td>
                </tr>
                """.formatted(r.getRepo().getName(), r.getStatus().name(), prCell));
        }

        sb.append("""
            </tbody></table>
            <br><hr>
            <p style="font-size:12px;color:#777;">
                Automated message from POM Standardization Agent.
            </p>
            </body></html>
            """);

        return sb.toString();
    }
}
