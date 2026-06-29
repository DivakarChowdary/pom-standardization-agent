package com.divakarchowdary.pom.service;

import com.divakarchowdary.pom.git.GithubPRClient;
import com.divakarchowdary.pom.model.PomUpdaterProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkApprovePRs {

    private final PomUpdaterProperties props;
    private final GithubPRClient prClient;

    public void approveLatestExcel() throws IOException, InvalidFormatException {
        File excelFile = findLatestExcelFile();
        log.info("Using Excel file: {}", excelFile.getAbsolutePath());

        List<String> prUrls = readPrUrls(excelFile);
        log.info("Found {} PRs to approve", prUrls.size());

        int approved = 0, failed = 0;
        for (String url : prUrls) {
            try {
                approveAndMerge(url);
                approved++;
            } catch (Exception e) {
                log.error("Failed to process PR {}: {}", url, e.getMessage());
                failed++;
            }
        }

        log.info("Bulk approve complete — approved: {}, failed: {}", approved, failed);
    }

    private void approveAndMerge(String prUrl) throws IOException {
        String cleaned = prUrl.replaceFirst("https?://[^/]+/", "");
        String[] parts = cleaned.split("/");

        if (parts.length < 4) {
            throw new IllegalArgumentException("Cannot parse PR URL: " + prUrl);
        }

        String owner = parts[0];
        String repo = parts[1];
        int prNumber = Integer.parseInt(parts[3]);
        String apiBase = props.getGithubApiBaseUrl();

        log.info("Approving PR #{} in {}/{}", prNumber, owner, repo);
        prClient.approvePR(apiBase, owner, repo, prNumber, props.getApproverToken());

        log.info("Merging PR #{} in {}/{}", prNumber, owner, repo);
        prClient.mergePR(apiBase, owner, repo, prNumber, props.getApproverToken());

        log.info("PR #{} approved and merged: {}", prNumber, prUrl);
    }

    private File findLatestExcelFile() {
        File dir = new File(props.getPrDir(), "Pull request files" + File.separator + props.getProfile());
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IllegalStateException("Directory not found: " + dir.getAbsolutePath());
        }

        return Arrays.stream(Objects.requireNonNull(dir.listFiles()))
                .filter(f -> f.isFile() && f.getName().endsWith(".xlsx"))
                .max(Comparator.comparingLong(File::lastModified))
                .orElseThrow(() -> new IllegalStateException("No Excel files found in: " + dir));
    }

    private List<String> readPrUrls(File file) throws IOException, InvalidFormatException {
        List<String> urls = new ArrayList<>();
        try (Workbook wb = new XSSFWorkbook(file)) {
            Sheet sheet = wb.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Cell cell = row.getCell(2);
                if (cell != null && cell.getCellType() == CellType.STRING) {
                    String url = cell.getStringCellValue().trim();
                    if (!url.isBlank() && !url.equals("N/A")) {
                        urls.add(url);
                    }
                }
            }
        }
        return urls;
    }
}
