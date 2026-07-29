package com.qaframework.excel;

import com.qaframework.config.AppConfig;
import com.qaframework.model.ScoreResult;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Writes everything into ONE output Excel file with two sheets:
 *   - "Results"   : one row per query with all confidence scores + status
 *   - "Dashboard" : overall pass/fail counts and average scores across all queries
 */
public final class ExcelWriter {

    private ExcelWriter() {
    }

    public static void write(List<ScoreResult> results, String outputPath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {

            CellStyle headerStyle = buildHeaderStyle(workbook);
            CellStyle passStyle = buildStatusStyle(workbook, IndexedColors.LIGHT_GREEN);
            CellStyle failStyle = buildStatusStyle(workbook, IndexedColors.ROSE);
            CellStyle wrapStyle = buildWrapStyle(workbook);

            writeResultsSheet(workbook, results, headerStyle, passStyle, failStyle, wrapStyle);
            writeDashboardSheet(workbook, results, headerStyle);

            File outFile = new File(outputPath);
            File parent = outFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                workbook.write(fos);
            }
        }
    }

    // ─── Results sheet ──────────────────────────────────────────────────────

    private static void writeResultsSheet(Workbook workbook, List<ScoreResult> results,
                                           CellStyle headerStyle, CellStyle passStyle,
                                           CellStyle failStyle, CellStyle wrapStyle) {
        Sheet sheet = workbook.createSheet("Results");

        String[] headers = {
                "Row#", "Query", "Expected_Response", "Actual_Response",
                "Semantic_Correctness", "Relevance", "Completeness", "Groundedness",
                "Overall_Confidence", "Status", "Reason"
        };

        Row headerRow = sheet.createRow(0);
        for (int c = 0; c < headers.length; c++) {
            Cell cell = headerRow.createCell(c);
            cell.setCellValue(headers[c]);
            cell.setCellStyle(headerStyle);
        }

        int r = 1;
        for (ScoreResult result : results) {
            Row row = sheet.createRow(r++);

            row.createCell(0).setCellValue(result.getTestCase().getRowNumber());
            setWrappedCell(row, 1, result.getTestCase().getQuery(), wrapStyle);
            setWrappedCell(row, 2, result.getTestCase().getExpectedResponse(), wrapStyle);
            setWrappedCell(row, 3, result.getTestCase().getActualResponse(), wrapStyle);
            row.createCell(4).setCellValue(result.getSemanticCorrectness());
            row.createCell(5).setCellValue(result.getRelevance());
            row.createCell(6).setCellValue(result.getCompleteness());
            row.createCell(7).setCellValue(result.getGroundedness());
            row.createCell(8).setCellValue(result.getOverallConfidence());

            Cell statusCell = row.createCell(9);
            statusCell.setCellValue(result.getStatus());
            statusCell.setCellStyle(result.isPass() ? passStyle : failStyle);

            setWrappedCell(row, 10, result.getReason(), wrapStyle);
        }

        for (int c = 0; c < headers.length; c++) {
            sheet.setColumnWidth(c, 20 * 256);
        }
        sheet.setColumnWidth(1, 40 * 256);
        sheet.setColumnWidth(2, 40 * 256);
        sheet.setColumnWidth(3, 40 * 256);
        sheet.setColumnWidth(10, 45 * 256);
        sheet.createFreezePane(0, 1);
    }

    private static void setWrappedCell(Row row, int col, String value, CellStyle wrapStyle) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(wrapStyle);
    }

    // ─── Dashboard sheet ────────────────────────────────────────────────────

    private static void writeDashboardSheet(Workbook workbook, List<ScoreResult> results, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet("Dashboard");

        int total = results.size();
        long passed = results.stream().filter(ScoreResult::isPass).count();
        long failed = total - passed;
        double passPct = total == 0 ? 0 : (passed * 100.0) / total;

        double avgSemantic = average(results, ScoreResult::getSemanticCorrectness);
        double avgRelevance = average(results, ScoreResult::getRelevance);
        double avgCompleteness = average(results, ScoreResult::getCompleteness);
        double avgGroundedness = average(results, ScoreResult::getGroundedness);
        double avgOverall = average(results, ScoreResult::getOverallConfidence);

        int r = 0;
        r = writeTitleRow(sheet, r, "AI Response Validation — Dashboard", headerStyle);
        r++;

        r = writeKeyValueRow(sheet, r, "Total Queries", String.valueOf(total));
        r = writeKeyValueRow(sheet, r, "Passed", String.valueOf(passed));
        r = writeKeyValueRow(sheet, r, "Failed", String.valueOf(failed));
        r = writeKeyValueRow(sheet, r, "Pass %", String.format("%.1f%%", passPct));
        r = writeKeyValueRow(sheet, r, "Pass Threshold Used", AppConfig.getPassThreshold() + " (Overall_Confidence >=)");
        r++;

        r = writeTitleRow(sheet, r, "Average Scores Across All Queries", headerStyle);
        r = writeKeyValueRow(sheet, r, "Avg Semantic_Correctness", String.format("%.1f", avgSemantic));
        r = writeKeyValueRow(sheet, r, "Avg Relevance", String.format("%.1f", avgRelevance));
        r = writeKeyValueRow(sheet, r, "Avg Completeness", String.format("%.1f", avgCompleteness));
        r = writeKeyValueRow(sheet, r, "Avg Groundedness", String.format("%.1f", avgGroundedness));
        r = writeKeyValueRow(sheet, r, "Avg Overall_Confidence", String.format("%.1f", avgOverall));
        r++;

        r = writeTitleRow(sheet, r, "Failed Queries (Row# — Query)", headerStyle);
        for (ScoreResult result : results) {
            if (!result.isPass()) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(result.getTestCase().getRowNumber());
                row.createCell(1).setCellValue(result.getTestCase().getQuery());
                row.createCell(2).setCellValue("Overall_Confidence: " + result.getOverallConfidence());
            }
        }
        if (failed == 0) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue("None — all queries passed.");
        }

        sheet.setColumnWidth(0, 35 * 256);
        sheet.setColumnWidth(1, 45 * 256);
        sheet.setColumnWidth(2, 25 * 256);
    }

    private static double average(List<ScoreResult> results, java.util.function.ToIntFunction<ScoreResult> extractor) {
        return results.stream().mapToInt(extractor).average().orElse(0);
    }

    private static int writeTitleRow(Sheet sheet, int rowIdx, String title, CellStyle headerStyle) {
        Row row = sheet.createRow(rowIdx);
        Cell cell = row.createCell(0);
        cell.setCellValue(title);
        cell.setCellStyle(headerStyle);
        return rowIdx + 1;
    }

    private static int writeKeyValueRow(Sheet sheet, int rowIdx, String key, String value) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(key);
        row.createCell(1).setCellValue(value);
        return rowIdx + 1;
    }

    // ─── Styles ─────────────────────────────────────────────────────────────

    private static CellStyle buildHeaderStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static CellStyle buildStatusStyle(Workbook workbook, IndexedColors color) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private static CellStyle buildWrapStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        return style;
    }
}
