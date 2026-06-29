package com.divakarchowdary.pom.excel;

import com.divakarchowdary.pom.model.PomUpdaterProperties;
import com.divakarchowdary.pom.model.RepoResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExcelWriter {

    private final PomUpdaterProperties props;

    public File writeSummary(List<RepoResult> results) throws Exception {
        String profile = props.getProfile();
        File outputDir = new File(props.getPrDir(), "Pull request files/" + profile);

        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IllegalStateException("Unable to create output directory: " + outputDir);
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        File file = new File(outputDir, profile.toUpperCase() + "-POM-" + timestamp + ".xlsx");

        try (Workbook wb = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(file)) {

            Sheet sheet = wb.createSheet("PR Summary");

            // Header row
            Row header = sheet.createRow(0);
            CellStyle headerStyle = headerStyle(wb);
            createHeaderCell(header, 0, "Repository", headerStyle);
            createHeaderCell(header, 1, "Status", headerStyle);
            createHeaderCell(header, 2, "Pull Request URL", headerStyle);

            // Hyperlink style
            CellStyle linkStyle = linkStyle(wb);
            CreationHelper helper = wb.getCreationHelper();

            int rowIdx = 1;
            for (RepoResult r : results) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(r.getRepo().getName());
                row.createCell(1).setCellValue(r.getStatus().name());

                if (r.getPrUrl() != null) {
                    Hyperlink link = helper.createHyperlink(HyperlinkType.URL);
                    link.setAddress(r.getPrUrl());

                    Cell cell = row.createCell(2);
                    cell.setCellValue(r.getPrUrl());
                    cell.setHyperlink(link);
                    cell.setCellStyle(linkStyle);
                } else {
                    row.createCell(2).setCellValue("N/A");
                }
            }

            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            sheet.autoSizeColumn(2);

            wb.write(fos);
        }

        log.info("Excel summary written: {}", file.getAbsolutePath());
        return file;
    }

    private CellStyle headerStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle linkStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setUnderline(Font.U_SINGLE);
        font.setColor(IndexedColors.BLUE.getIndex());
        style.setFont(font);
        return style;
    }

    private void createHeaderCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }
}
